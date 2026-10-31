package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Deleting an entry through the API (D-30, API_CONTRACT {@code DELETE /entries/{id}}): it goes to Recently deleted and
 * every other request about it is a {@code 404}, except that an entry with nothing to bring back is erased at once.
 * Restore, erase and the daily sweep are task 3.11; here the row's {@code deleted_at} is read with SQL.
 */
class EntryDeleteTest extends EntryApiTest {

    // Moved to Recently deleted

    @Test
    void deletingAnEntryAnswers204WithNoBodyAndKeepsTheRowWithTheTimeItWasDeleted() {
        User alice = aUser();
        String id = createTitledEntry(alice);
        clockAt("2026-11-01T03:04:05Z");

        delete(alice, id).expectStatus().isNoContent().expectBody().isEmpty();

        Map<String, Object> row = row(id);
        assertThat(instantOf(row.get("deleted_at")))
                .as("deleted_at comes from the injected clock")
                .isEqualTo("2026-11-01T03:04:05Z");
        assertThat(row).containsEntry("title", "A title worth keeping");
    }

    @Test
    void afterDeletingEveryOtherRequestAboutTheEntryIs404() {
        User alice = aUser();
        String id = createTitledEntry(alice);
        delete(alice, id).expectStatus().isNoContent();

        for (var response : List.of(
                get(alice, id),
                put(alice, id, json("version", 1, "entryDate", "2026-10-31")),
                seal(alice, id, "2027-10-31T22:00:00Z"),
                delete(alice, id))) {
            String body = bodyText(response.expectStatus().isNotFound());
            assertThat(problemType(body)).isEqualTo("not-found");
            assertThat(body).doesNotContain("worth keeping");
        }
        assertThat(row(id).get("unlock_date"))
                .as("the refused seal changed nothing")
                .isNull();
    }

    @Test
    void aDeletedEntryLooksExactlyLikeOneThatNeverExisted() {
        User alice = aUser();
        String id = createTitledEntry(alice);
        delete(alice, id).expectStatus().isNoContent();

        String deleted = bodyText(get(alice, id).expectStatus().isNotFound());
        String never = bodyText(get(alice, UUID.randomUUID()).expectStatus().isNotFound());

        assertThat(JsonPath.<String>read(deleted, "$.detail")).isEqualTo(JsonPath.<String>read(never, "$.detail"));
        assertThat(JsonPath.<String>read(deleted, "$.title")).isEqualTo(JsonPath.<String>read(never, "$.title"));
    }

    @Test
    void theIdOfADeletedEntryCannotBeReusedByACreate() {
        User alice = aUser();
        String id = createTitledEntry(alice);
        delete(alice, id).expectStatus().isNoContent();

        String body = bodyText(post(alice, json("id", id)).expectStatus().isEqualTo(409));

        assertThat(problemType(body)).isEqualTo("conflict");
    }

    @Test
    void anEntryIsKeptWhenAnyOneOfItsTitleItsWordsOrItsMediaIsThere() {
        User alice = aUser();
        String titleOnly = createEntryId(alice, json("title", "Only a title"));
        String wordsOnly = createEntryId(alice, json("contentJson", doc("Only words")));
        String photoOnly = createEntryId(alice, json());
        aPhoto(photoOnly, "users/x/entries/y/only-a-photo.jpg", 0);

        for (String id : List.of(titleOnly, wordsOnly, photoOnly)) {
            delete(alice, id).expectStatus().isNoContent();

            assertThat(rowExists(id))
                    .as("entry %s waits in Recently deleted", id)
                    .isTrue();
            assertThat(row(id).get("deleted_at")).isNotNull();
        }
    }

    @Test
    void deletingKeepsTheMediaRowsAndTheTagsSoARestoreCanBringThemBack() {
        User alice = aUser();
        String id = createTitledEntry(alice);
        aPhoto(id, "users/x/entries/y/keep-me.jpg", 0);
        UUID tagId = UUID.randomUUID();
        jdbc.sql("INSERT INTO tags (id, user_id, name) VALUES (:id, :user, 'Family')")
                .param("id", tagId)
                .param("user", alice.getId())
                .update();
        jdbc.sql("INSERT INTO entry_tags (entry_id, tag_id) VALUES (:entry, :tag)")
                .param("entry", UUID.fromString(id))
                .param("tag", tagId)
                .update();

        delete(alice, id).expectStatus().isNoContent();

        assertThat(jdbc.sql("SELECT count(*) FROM media WHERE entry_id = :id")
                        .param("id", UUID.fromString(id))
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
        assertThat(jdbc.sql("SELECT count(*) FROM entry_tags WHERE entry_id = :id")
                        .param("id", UUID.fromString(id))
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
    }

    @Test
    void deletingLeavesTheWeekAndTheUsersOtherEntriesAlone() {
        User alice = aUser();
        String first = createTitledEntry(alice);
        String second = createTitledEntry(alice);

        delete(alice, first).expectStatus().isNoContent();

        assertThat(countWeeks(alice)).isEqualTo(1);
        assertThat(getEntry(alice, second)).containsEntry("title", "A title worth keeping");
    }

    // Erased at once

    @Test
    void anEntryWithNothingInItIsErasedAtOnceAndItsIdIsFreeAgain() {
        User alice = aUser();
        UUID id = UUID.randomUUID();
        createEntry(alice, json("id", id.toString()));

        delete(alice, id).expectStatus().isNoContent();

        assertThat(rowExists(id)).as("never waits in Recently deleted").isFalse();
        get(alice, id).expectStatus().isNotFound();
        post(alice, json("id", id.toString(), "title", "Reborn")).expectStatus().isCreated();
    }

    @Test
    void anEmptyCaptionCountsAsNothing() {
        User alice = aUser();
        String blankParagraph = createEntryId(
                alice, json("contentJson", json("type", "doc", "content", List.of(json("type", "paragraph")))));
        String justARule = createEntryId(
                alice, json("contentJson", json("type", "doc", "content", List.of(json("type", "horizontalRule")))));
        String spaces = createEntryId(alice, json("contentJson", doc("   ")));

        for (String id : List.of(blankParagraph, justARule, spaces)) {
            delete(alice, id).expectStatus().isNoContent();

            assertThat(rowExists(id)).as("entry %s is erased", id).isFalse();
        }
    }

    @Test
    void anEntryWithAPhotoButNothingElseIsNotEmpty() {
        User alice = aUser();
        String id = createEntryId(alice, json());
        aPhoto(id, "users/x/entries/y/photo.jpg", 0);

        delete(alice, id).expectStatus().isNoContent();

        assertThat(rowExists(id)).isTrue();
    }

    @Test
    void erasingAnEmptyEntryNeedsNoCleanupOfItsTagsAndLeavesTheTagItself() {
        User alice = aUser();
        String id = createEntryId(alice, json());
        UUID tagId = UUID.randomUUID();
        jdbc.sql("INSERT INTO tags (id, user_id, name) VALUES (:id, :user, 'Work')")
                .param("id", tagId)
                .param("user", alice.getId())
                .update();
        jdbc.sql("INSERT INTO entry_tags (entry_id, tag_id) VALUES (:entry, :tag)")
                .param("entry", UUID.fromString(id))
                .param("tag", tagId)
                .update();

        delete(alice, id).expectStatus().isNoContent();

        assertThat(rowExists(id)).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM entry_tags WHERE tag_id = :id")
                        .param("id", tagId)
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM tags WHERE id = :id")
                        .param("id", tagId)
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
    }

    // Sealed entries (D-10: delete is always allowed)

    @Test
    void aSealedEntryWithContentIsDeletedIntoRecentlyDeletedAndStaysSealed() {
        User alice = aUser();
        String id = createTitledEntry(alice);
        seal(alice, id, "2027-10-31T22:00:00Z").expectStatus().isOk();

        delete(alice, id).expectStatus().isNoContent();

        assertThat(row(id).get("deleted_at")).isNotNull();
        assertThat(row(id).get("unlock_date")).isNotNull();
    }

    @Test
    void aSealedEmptyEntryIsErasedAtOnceLikeAnyOther() {
        User alice = aUser();
        String id = createEntryId(alice, json());
        seal(alice, id, "2027-10-31T22:00:00Z").expectStatus().isOk();

        delete(alice, id).expectStatus().isNoContent();

        assertThat(rowExists(id)).isFalse();
    }

    // Whose entry it is

    @Test
    void deletingAnotherUsersEntryIs404AndLeavesItAlone() {
        User alice = aUser();
        User bob = aUser();
        String bobsEntry = createTitledEntry(bob);
        Map<String, Object> before = row(bobsEntry);

        String someonesElse = bodyText(delete(alice, bobsEntry).expectStatus().isNotFound());
        String missing =
                bodyText(delete(alice, UUID.randomUUID()).expectStatus().isNotFound());

        assertThat(problemType(someonesElse)).isEqualTo("not-found");
        assertThat(JsonPath.<String>read(missing, "$.detail"))
                .isEqualTo(JsonPath.<String>read(someonesElse, "$.detail"));
        assertThat(row(bobsEntry)).isEqualTo(before);
        assertThat(getEntry(bob, bobsEntry)).containsEntry("title", "A title worth keeping");
    }

    @Test
    void deletingTheEmptyEntryOfAnotherUserIsAlso404AndTheEntryIsStillThere() {
        User alice = aUser();
        User bob = aUser();
        String bobsEmpty = createEntryId(bob, json());

        delete(alice, bobsEmpty).expectStatus().isNotFound();

        assertThat(rowExists(bobsEmpty)).isTrue();
    }

    @Test
    void deletingWithAnIdThatIsNotAUuidIs400AndFromAUserWhoIsNotThereIs404() {
        User alice = aUser();
        String id = createTitledEntry(alice);

        String body = bodyText(delete(alice, "not-a-uuid").expectStatus().isBadRequest());
        assertThat(problemType(body)).isEqualTo("malformed-request");

        User nobody = new User(UUID.randomUUID(), "nobody@memoirist.test", "Nobody", "Australia/Melbourne");
        delete(nobody, id).expectStatus().isNotFound();
        assertThat(row(id).get("deleted_at")).isNull();
    }
}

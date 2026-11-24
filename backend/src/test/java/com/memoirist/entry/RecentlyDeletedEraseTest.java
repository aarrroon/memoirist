package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code POST /api/v1/entries/{entryId}/erase} (task 3.11, D-30): an entry in Recently deleted is erased at once, with
 * its media rows and its stored files, and nothing else is touched. Erasing goes through {@code EntryErasure}, which
 * {@code MediaCleanupTest} tests on its own; these tests are about the endpoint around it.
 */
class RecentlyDeletedEraseTest extends RecentlyDeletedApiTest {

    private static final String T1 = "2026-10-31T02:00:00Z";

    @Test
    void erasingRemovesTheEntryItsMediaRowsAndItsStoredFiles() {
        User alice = aUser();
        String id = anEntryWithPhotos(alice, W4, "Goodbye", 3);
        List<String> keys = objectKeysOf(id);
        assertThat(keys).allSatisfy(key -> assertThat(stored(key)).isTrue());
        deleteAt(T1, alice, id);
        assertThat(keys)
                .as("deleting keeps the files")
                .allSatisfy(key -> assertThat(stored(key)).isTrue());

        erase(alice, id).expectStatus().isNoContent().expectBody().isEmpty();

        assertThat(rowExists(id)).as("the entry").isFalse();
        assertThat(mediaRowCount(id)).as("its media rows").isZero();
        assertThat(keys)
                .as("its files")
                .allSatisfy(key -> assertThat(stored(key)).isFalse());
        assertThat(deletedIds(alice)).isEmpty();
        get(alice, id).expectStatus().isNotFound();
    }

    @Test
    void erasingOneEntryLeavesEveryOtherEntryAndFileAlone() {
        User alice = aUser();
        User bob = aUser();
        String erased = anEntryWithPhotos(alice, W4, "Erased", 1);
        String waiting = anEntryWithPhotos(alice, W4, "Also deleted", 1);
        String live = anEntryWithPhotos(alice, W4, "Live", 1);
        String bobs = anEntryWithPhotos(bob, W4, "Bob's, deleted", 1);
        List<String> kept = List.of(
                objectKeysOf(waiting).get(0),
                objectKeysOf(live).get(0),
                objectKeysOf(bobs).get(0));
        deleteAt(T1, alice, erased);
        deleteOk(alice, waiting);
        deleteOk(bob, bobs);

        erase(alice, erased).expectStatus().isNoContent();

        assertThat(deletedIds(alice)).containsExactly(waiting);
        assertThat(deletedIds(bob)).containsExactly(bobs);
        assertThat(rowExists(live)).isTrue();
        assertThat(kept)
                .as("everyone else's files")
                .allSatisfy(key -> assertThat(stored(key)).isTrue());
    }

    @Test
    void itsWeekItsTagsAndItsTemplateAreLeftInPlace() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Tagged");
        UUID work = newTag(alice, "Work");
        tag(id, work);
        deleteAt(T1, alice, id);
        long weeks = countWeeks(alice);

        erase(alice, id).expectStatus().isNoContent();

        assertThat(countWeeks(alice))
                .as("the week row stays; the feed hides an empty week")
                .isEqualTo(weeks);
        assertThat(jdbc.sql("SELECT count(*) FROM tags WHERE id = :id")
                        .param("id", work)
                        .query(Long.class)
                        .single())
                .as("the tag itself")
                .isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM entry_tags WHERE tag_id = :id")
                        .param("id", work)
                        .query(Long.class)
                        .single())
                .as("but not its link to the erased entry")
                .isZero();
    }

    @Test
    void anErasedEntrysIdIsFreeAgain() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Old");
        deleteAt(T1, alice, id);
        post(alice, json("id", id, "title", "Same id")).expectStatus().isEqualTo(409);

        erase(alice, id).expectStatus().isNoContent();

        post(alice, json("id", id, "title", "Same id, after the erase"))
                .expectStatus()
                .isCreated();
    }

    // Loot Drops

    @Test
    void aSealedEntryCanBeErasedWhileItIsStillSealed() {
        User alice = aUser();
        String drop = aLootDrop(alice, "Sealed", SEALED_UNTIL);
        String key = aStoredPhoto(drop, 0);
        deleteAt(T1, alice, drop);

        erase(alice, drop).expectStatus().isNoContent();

        assertThat(rowExists(drop)).isFalse();
        assertThat(stored(key)).isFalse();
    }

    // Not in Recently deleted

    @Test
    void anEntryThatIsNotDeletedIs404AndNothingIsErased() {
        User alice = aUser();
        String live = anEntryWithPhotos(alice, W4, "Live", 1);
        String key = objectKeysOf(live).get(0);

        String body = bodyText(erase(alice, live).expectStatus().isNotFound());

        assertThat(problemType(body)).isEqualTo("not-found");
        assertThat(JsonPath.<String>read(body, "$.detail")).doesNotContain(live);
        assertThat(rowExists(live)).isTrue();
        assertThat(stored(key)).isTrue();
        getEntry(alice, live);
    }

    @Test
    void erasingTwiceIsTheSecondTimeA404() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Twice");
        deleteAt(T1, alice, id);

        erase(alice, id).expectStatus().isNoContent();
        erase(alice, id).expectStatus().isNotFound();
    }

    @Test
    void anotherUsersDeletedEntryIs404AndItsFilesAreKept() {
        User alice = aUser();
        User bob = aUser();
        String bobs = anEntryWithPhotos(bob, W4, "Bob's", 2);
        List<String> keys = objectKeysOf(bobs);
        deleteAt(T1, bob, bobs);

        String foreign = bodyText(erase(alice, bobs).expectStatus().isNotFound());
        String missing = bodyText(erase(alice, UUID.randomUUID()).expectStatus().isNotFound());

        assertThat(problemType(foreign)).isEqualTo("not-found");
        assertThat(JsonPath.<String>read(foreign, "$.detail")).isEqualTo(JsonPath.<String>read(missing, "$.detail"));
        assertThat(rowExists(bobs)).isTrue();
        assertThat(deletedIds(bob)).containsExactly(bobs);
        assertThat(keys).allSatisfy(key -> assertThat(stored(key)).isTrue());
    }

    @Test
    void aRestoredEntryCannotBeErasedThroughThisCallAndKeepsItsFiles() {
        User alice = aUser();
        String id = anEntryWithPhotos(alice, W4, "Restored", 1);
        String key = objectKeysOf(id).get(0);
        deleteAt(T1, alice, id);
        restore(alice, id).expectStatus().isOk();

        erase(alice, id).expectStatus().isNotFound();

        assertThat(rowExists(id)).isTrue();
        assertThat(stored(key)).isTrue();
    }

    @Test
    void aPathIdThatIsNotAUuidIs400AndAHeaderForNobodyIs404() {
        User alice = aUser();
        assertThat(problemType(
                        bodyText(erase(alice, "not-a-uuid").expectStatus().isBadRequest())))
                .isEqualTo("malformed-request");

        String id = entryOn(alice, W4, "Mine");
        deleteAt(T1, alice, id);
        client.post()
                .uri(ENTRIES + "/" + id + "/erase")
                .header(HEADER, UUID.randomUUID().toString())
                .exchange()
                .expectStatus()
                .isNotFound();
        assertThat(rowExists(id)).isTrue();
    }

    @Test
    void eightErasesAtTheSameMomentGiveOne204AndSeven404() throws Exception {
        User alice = aUser();
        String id = anEntryWithPhotos(alice, W4, "Race", 2);
        List<String> keys = objectKeysOf(id);
        deleteAt(T1, alice, id);

        List<Integer> statuses = atTheSameTime(
                8, caller -> erase(alice, id).returnResult().getStatus().value());

        assertThat(statuses).containsExactlyInAnyOrder(204, 404, 404, 404, 404, 404, 404, 404);
        assertThat(rowExists(id)).isFalse();
        assertThat(keys).allSatisfy(key -> assertThat(stored(key)).isFalse());
    }
}

package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code POST /api/v1/entries/{entryId}/mark-seen} (task 3.10, D-10): what it writes, when it refuses, that it is safe
 * to repeat, and that it is a view mark and not an edit. How the Opened strip reacts is in {@link EntryOpenedTest}.
 */
class EntryMarkSeenTest extends EntryOpenedApiTest {

    // The happy path

    @Test
    void markingAnOpenedLootDropSeenIs204WithNoBodyAndRecordsTheTime() {
        User alice = aUser();
        String id = aLootDrop(alice, "Dear future me", OPENS_1);
        clockAt("2026-11-03T03:04:05Z");

        markSeen(alice, id).expectStatus().isNoContent().expectBody().isEmpty();

        assertThat(seenAt(id)).isEqualTo("2026-11-03T03:04:05Z");
    }

    @Test
    void aLootDropIsOpenAtTheVeryInstantItUnlocks() {
        User alice = aUser();
        String id = aLootDrop(alice, "Dear future me", OPENS_1);
        clockAt(OPENS_1);

        markSeenOk(alice, id);

        assertThat(seenAt(id)).isEqualTo(OPENS_1);
    }

    @Test
    void markingItAgainIs204AndKeepsTheFirstSeenTime() {
        User alice = aUser();
        String id = aLootDrop(alice, "Dear future me", OPENS_1);
        clockAt("2026-11-03T00:00:00Z");
        markSeenOk(alice, id);

        clock.advance(Duration.ofHours(5));
        markSeenOk(alice, id);

        assertThat(seenAt(id)).as("the second call changed nothing").isEqualTo("2026-11-03T00:00:00Z");
    }

    // A view mark is not an edit

    @Test
    void markingSeenDoesNotRaiseTheVersionOrMoveUpdatedAt() {
        User alice = aUser();
        String id = aLootDrop(alice, "Dear future me", OPENS_1);
        clockAt(AFTER_ALL);
        Map<String, Object> before = getEntry(alice, id);
        Object updatedAtBefore = row(id).get("updated_at");

        markSeenOk(alice, id);

        Map<String, Object> after = getEntry(alice, id);
        assertThat(after.get("version")).as("version").isEqualTo(before.get("version"));
        assertThat(after.get("updatedAt")).as("updatedAt").isEqualTo(before.get("updatedAt"));
        assertThat(row(id).get("updated_at")).as("the column itself").isEqualTo(updatedAtBefore);
    }

    @Test
    void aCaptionSavedWithTheVersionReadBeforeMarkingSeenIsNotStale() {
        User alice = aUser();
        String id = aLootDrop(alice, "Dear future me", OPENS_1);
        clockAt(AFTER_ALL);
        // The client reads the opened entry (version 1: created at 0, sealed at 1), the user taps it in the strip,
        // the app marks it seen, and then the user saves the caption they were editing.
        Map<String, Object> read = getEntry(alice, id);
        markSeenOk(alice, id);

        Map<String, Object> saved = bodyMap(put(
                        alice,
                        id,
                        json(
                                "version",
                                read.get("version"),
                                "entryDate",
                                W4,
                                "title",
                                "Dear future me",
                                "contentJson",
                                doc("I did get the job.")))
                .expectStatus()
                .isOk());

        assertThat(saved).containsEntry("contentText", "I did get the job.");
    }

    // When it refuses

    @Test
    void aSealedLootDropIs409EntrySealedAndNothingIsWritten() {
        User alice = aUser();
        String id = aLootDrop(alice, "Dear future me", OPENS_3);
        clockAt(OPENS_2);

        String body = bodyText(markSeen(alice, id).expectStatus().isEqualTo(409));

        assertThat(problemType(body)).isEqualTo("entry-sealed");
        assertThat(body).as("the error says nothing about what is inside").doesNotContain("Dear future me");
        assertThat(seenAt(id)).isNull();
    }

    @Test
    void anEntryThatWasNeverSealedHasNothingToMarkAndAnswers204() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Never sealed");

        markSeenOk(alice, id);

        assertThat(seenAt(id))
                .as("no seen mark on an entry that is not a Loot Drop")
                .isNull();
    }

    @Test
    void anotherUsersLootDropIs404EvenWhenOpenAndEvenWhenSealed() {
        User alice = aUser();
        User bob = aUser();
        String opened = aLootDrop(alice, "Alice's, opened", OPENS_1);
        String sealed = aLootDrop(alice, "Alice's, sealed", OPENS_3);
        clockAt(OPENS_2);

        markSeen(bob, opened).expectStatus().isNotFound();
        // A sealed one is also 404, not 409: Bob must not learn that Alice has a Loot Drop with this id.
        markSeen(bob, sealed).expectStatus().isNotFound();

        assertThat(seenAt(opened)).isNull();
        assertThat(seenAt(sealed)).isNull();
    }

    @Test
    void anEntryThatDoesNotExistIs404AndLooksTheSameAsSomeoneElses() {
        User alice = aUser();
        UUID missing = UUID.randomUUID();

        String body = bodyText(markSeen(alice, missing).expectStatus().isNotFound());

        assertThat(problemType(body)).isEqualTo("not-found");
        // instance is the request path, which holds the id the client sent; detail is what must not repeat it.
        assertThat(JsonPath.<String>read(body, "$.detail")).doesNotContain(missing.toString());
    }

    @Test
    void aLootDropInRecentlyDeletedIs404AndIsNotMarked() {
        User alice = aUser();
        String id = aLootDrop(alice, "Deleted", OPENS_1);
        clockAt(AFTER_ALL);
        delete(alice, id).expectStatus().isNoContent();

        markSeen(alice, id).expectStatus().isNotFound();

        assertThat(seenAt(id)).isNull();
    }

    @Test
    void aHeaderThatNamesNobodyIs404() {
        User nobody = new User(UUID.randomUUID(), "nobody@memoirist.test", "Nobody", "Australia/Melbourne");

        markSeen(nobody, UUID.randomUUID()).expectStatus().isNotFound();
    }

    @Test
    void anIdThatIsNotAUuidIs400() {
        User alice = aUser();

        String body = bodyText(markSeen(alice, "not-a-uuid").expectStatus().isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
    }

    @Test
    void theOnlyMethodIsPost() {
        User alice = aUser();
        String id = aLootDrop(alice, "Dear future me", OPENS_1);
        clockAt(AFTER_ALL);

        client.get()
                .uri(ENTRIES + "/" + id + "/mark-seen")
                .header(HEADER, alice.getId().toString())
                .exchange()
                .expectStatus()
                .isEqualTo(405);

        assertThat(seenAt(id)).isNull();
    }
}

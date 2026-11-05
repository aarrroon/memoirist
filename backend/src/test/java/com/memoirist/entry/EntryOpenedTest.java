package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/v1/entries/opened}, the Opened strip (task 3.10, D-10): which Loot Drops it lists, in what order,
 * and when they leave it and come back. {@code mark-seen} itself is in {@link EntryMarkSeenTest}; the cost of the
 * strip in statements is in {@link EntryOpenedQueryCountTest}.
 *
 * <p>All of it runs on the movable clock ({@link EntryApiTest}), which is what makes "an entry appears once its unlock
 * time passes" testable without waiting: the unlock instants are set with the real {@code seal} call, and the clock
 * is moved to just before, exactly at and after them.
 */
class EntryOpenedTest extends EntryOpenedApiTest {

    // What the strip lists

    @Test
    void aUserWithNoLootDropsGetsAnEmptyArray() {
        User alice = aUser();
        entryOn(alice, W4, "An ordinary entry");

        assertThat(openedBody(alice)).isEqualTo("[]");
    }

    @Test
    void aLootDropAppearsAtTheInstantItOpensAndNotBefore() {
        User alice = aUser();
        String id = aLootDrop(alice, "Dear future me", OPENS_1);

        clockAt("2026-11-02T21:59:59.999999Z");
        assertThat(openedIds(alice)).as("one microsecond before it opens").isEmpty();

        clockAt(OPENS_1);
        assertThat(openedIds(alice)).as("at the unlock instant").containsExactly(id);

        clockAt("2026-11-02T22:00:00.000001Z");
        assertThat(openedIds(alice)).as("a microsecond later").containsExactly(id);
    }

    @Test
    void aSealedLootDropIsNeverListedAndNeitherIsAnEntryThatWasNeverSealed() {
        User alice = aUser();
        aLootDrop(alice, "Not yet", OPENS_3);
        entryOn(alice, W4, "Never sealed");

        clockAt(OPENS_2);

        assertThat(openedBody(alice))
                .as("one is sealed until OPENS_3, the other has no unlock date")
                .isEqualTo("[]");
    }

    @Test
    void aRowIsTheWholeSummaryOfTheNowOpenEntry() {
        User alice = aUser();
        UUID work = newTag(alice, "Work");
        String id = createEntryId(
                alice, json("entryDate", W4, "title", "Dear future me", "contentJson", doc("I hope you got the job.")));
        aPhoto(id, "users/test/opened/1.jpg", 0);
        aPhoto(id, "users/test/opened/2.jpg", 1);
        aVoiceNote(id, "users/test/opened/3.webm", 2);
        tag(id, work);
        sealUntil(alice, id, OPENS_1);
        clockAt(OPENS_1);

        List<Map<String, Object>> rows = openedRows(alice);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst())
                .containsEntry("id", id)
                .containsEntry("entryDate", W4)
                .containsEntry("title", "Dear future me")
                .containsEntry("previewLine", "I hope you got the job.")
                .containsEntry("photoCount", 2)
                .containsEntry("voiceNoteCount", 1)
                .containsEntry("firstMediaKind", "PHOTO")
                .containsEntry("unlockDate", OPENS_1)
                .containsEntry("isLocked", false)
                .containsEntry("dateUnknown", false)
                .containsEntry("deletedAt", null);
        assertThat(JsonPath.<List<String>>read(openedBody(alice), "$[0].tags[*].name"))
                .containsExactly("Work");
        assertThat(rows.getFirst().keySet())
                .as("the same 14 fields as every other list row")
                .hasSize(14);
    }

    @Test
    void theMostRecentlyOpenedComesFirst() {
        User alice = aUser();
        // Made in a different order from the one they open in, so neither the creation order nor the id order passes.
        String third = aLootDrop(alice, "Opens third", OPENS_3);
        String first = aLootDrop(alice, "Opens first", OPENS_1);
        String second = aLootDrop(alice, "Opens second", OPENS_2);

        clockAt(AFTER_ALL);

        assertThat(openedIds(alice)).containsExactly(third, second, first);
    }

    @Test
    void twoLootDropsThatOpenInTheSameInstantAlwaysComeInTheSameOrder() {
        User alice = aUser();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ids.add(aLootDrop(alice, "Opens together " + i, OPENS_1));
        }
        clockAt(AFTER_ALL);

        // The tie-break is the id, newest first, as Postgres orders uuids (not Java's UUID.compareTo, which compares
        // signed longs and would sort some ids differently).
        List<String> byIdDescending = jdbc.sql("SELECT id::text FROM entries WHERE user_id = :user ORDER BY id DESC")
                .param("user", alice.getId())
                .query(String.class)
                .list();
        assertThat(byIdDescending).containsExactlyInAnyOrderElementsOf(ids);

        assertThat(openedIds(alice)).containsExactlyElementsOf(byIdDescending);
        assertThat(openedIds(alice)).as("the same again").containsExactlyElementsOf(byIdDescending);
    }

    @Test
    void theStripHoldsAtMostTwentyAndKeepsTheMostRecentlyOpened() {
        User alice = aUser();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            ids.add(entryOn(alice, W4, "Drop " + i));
        }
        // Drop i opened at i o'clock on 20 October, so drop 21 opened last. Written with SQL: the API only seals
        // into the future, and 22 seals and clock moves would say nothing more.
        for (int i = 0; i < ids.size(); i++) {
            unlockedAt(ids.get(i), "2026-10-20T%02d:00:00Z".formatted(i));
        }

        List<String> strip = openedIds(alice);

        assertThat(strip).hasSize(20);
        assertThat(strip.getFirst()).as("opened last").isEqualTo(ids.get(21));
        assertThat(strip.getLast()).as("the twentieth most recent").isEqualTo(ids.get(2));
        assertThat(strip).as("the two that opened first are cut").doesNotContain(ids.get(0), ids.get(1));
    }

    // What leaves the strip

    @Test
    void aSeenLootDropLeavesTheStripAndTheOthersStay() {
        User alice = aUser();
        String seen = aLootDrop(alice, "Seen", OPENS_1);
        String waiting = aLootDrop(alice, "Waiting", OPENS_2);
        clockAt(AFTER_ALL);
        assertThat(openedIds(alice)).containsExactly(waiting, seen);

        markSeenOk(alice, seen);

        assertThat(openedIds(alice)).containsExactly(waiting);
    }

    @Test
    void aLootDropInRecentlyDeletedIsLeftOutEvenThoughItHasOpened() {
        User alice = aUser();
        String kept = aLootDrop(alice, "Kept", OPENS_1);
        String deleted = aLootDrop(alice, "Deleted", OPENS_2);
        clockAt(AFTER_ALL);
        assertThat(openedIds(alice)).containsExactly(deleted, kept);

        delete(alice, deleted).expectStatus().isNoContent();

        assertThat(openedIds(alice)).containsExactly(kept);
        assertThat(row(deleted).get("deleted_at"))
                .as("it is only set aside, not erased")
                .isNotNull();
    }

    @Test
    void aLootDropSealedAgainLeavesTheStripAtOnceAndComesBackWhenItOpensAgain() {
        User alice = aUser();
        String id = aLootDrop(alice, "Sealed twice", OPENS_1);
        clockAt(OPENS_1);
        assertThat(openedIds(alice)).containsExactly(id);

        sealUntil(alice, id, OPENS_3);

        assertThat(openedIds(alice))
                .as("sealed again, nothing was ever marked seen")
                .isEmpty();
        clockAt(OPENS_3);
        assertThat(openedIds(alice)).as("and open again").containsExactly(id);
    }

    // Whose

    @Test
    void anotherUsersLootDropsAreNeverInTheStrip() {
        User alice = aUser();
        User bob = aUser();
        String alices = aLootDrop(alice, "Alice's", OPENS_1);
        String bobs = aLootDrop(bob, "Bob's", OPENS_1);
        clockAt(AFTER_ALL);

        assertThat(openedIds(alice)).containsExactly(alices);
        assertThat(openedIds(bob)).containsExactly(bobs);
    }

    @Test
    void aHeaderThatNamesNobodyGetsAnEmptyStrip() {
        User nobody = new User(UUID.randomUUID(), "nobody@memoirist.test", "Nobody", "Australia/Melbourne");

        assertThat(openedBody(nobody)).isEqualTo("[]");
    }

    // The whole life of a Loot Drop

    // TODO(human) PRACTICE: Write one test that walks a Loot Drop through its whole life on the movable clock, and
    //   checks the Opened strip after every step: sealed (not listed, and mark-seen is refused with 409), opened
    //   (listed), marked seen (gone), sealed again to a later date (still gone, and refused again), opened again
    //   (listed again), marked seen again (gone). Use the real seal and mark-seen calls, and move the clock with
    // clockAt.
    // Hint: D-10 and API_CONTRACT (the Opened strip). The test is a state machine, so name each state in an
    //   assertion message (`.as("...")`) and the failure tells you which step broke. Think about which instants are
    //   worth testing (just before, at, after), and why this is only possible because the Clock is injected.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    @Test
    void aLootDropGoesThroughSealedOpenedSeenSealedAgainOpenedAgainAndSeenAgain() {
        User alice = aUser();
        String id = aLootDrop(alice, "Dear future me", OPENS_1);

        // Sealed: not listed, and there is nothing to have seen yet.
        assertThat(openedIds(alice)).as("sealed").isEmpty();
        markSeen(alice, id).expectStatus().isEqualTo(409);

        // Opened: the strip lists it.
        clockAt(OPENS_1);
        assertThat(openedIds(alice)).as("opened").containsExactly(id);

        // Seen: it leaves the strip, and stays out as time passes.
        markSeenOk(alice, id);
        assertThat(openedIds(alice)).as("seen").isEmpty();
        clockAt(OPENS_2);
        assertThat(openedIds(alice)).as("seen, a day later").isEmpty();

        // Sealed again to a later date: the seen mark is cleared, and it is a sealed Loot Drop once more.
        sealUntil(alice, id, OPENS_3);
        assertThat(seenAt(id)).as("sealing clears the seen mark").isNull();
        assertThat(openedIds(alice)).as("sealed again").isEmpty();
        markSeen(alice, id).expectStatus().isEqualTo(409);

        // Opened again: back in the strip, as if for the first time.
        clockAt(OPENS_3);
        assertThat(openedIds(alice)).as("opened again").containsExactly(id);

        // Seen again: gone for good.
        markSeenOk(alice, id);
        assertThat(openedIds(alice)).as("seen again").isEmpty();
        assertThat(seenAt(id)).as("with the time of the second look").isEqualTo(OPENS_3);
    }
    // └── END ANSWER ▲ ──
}

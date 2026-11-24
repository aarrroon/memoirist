package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code POST /api/v1/entries/{entryId}/restore} (task 3.11, D-30): an entry in Recently deleted comes back in its week
 * with its media, tags and seal as they were. Where it shows up again (the feed, the timeline, the counts, the Opened
 * strip) is {@link RecentlyDeletedVisibilityTest}.
 */
class RecentlyDeletedRestoreTest extends RecentlyDeletedApiTest {

    private static final String T1 = "2026-10-31T02:00:00Z";

    // What comes back

    @Test
    void restoringReturnsTheWholeEntryWithItsMediaAndTagsInTheirOldPlaces() {
        User alice = aUser();
        String id = entryWithCaption(alice, W3, heading(1, "Day out"), paragraph("We went to the beach"));
        String first = aStoredPhoto(id, 0);
        String second = aStoredPhoto(id, 1);
        UUID work = newTag(alice, "Work");
        UUID family = newTag(alice, "Family");
        tag(id, work);
        tag(id, family);
        String weekBefore = weekIdOf(id);
        deleteAt(T1, alice, id);

        String body = bodyText(restore(alice, id).expectStatus().isOk());

        assertThat(JsonPath.<String>read(body, "$.id")).isEqualTo(id);
        assertThat(JsonPath.<String>read(body, "$.weeklyLogId"))
                .as("its own week")
                .isEqualTo(weekBefore);
        assertThat(JsonPath.<String>read(body, "$.entryDate")).isEqualTo(W3);
        assertThat(JsonPath.<Boolean>read(body, "$.isLocked")).isFalse();
        assertThat(JsonPath.<String>read(body, "$.contentText")).contains("We went to the beach");
        assertThat(JsonPath.<List<String>>read(body, "$.tags[*].name")).containsExactly("Family", "Work");
        assertThat(JsonPath.<List<Integer>>read(body, "$.media[*].displayOrder"))
                .containsExactly(0, 1);
        assertThat(JsonPath.<List<String>>read(body, "$.media[*].fileUrl"))
                .allSatisfy(url -> assertThat(url).startsWith("http"));
        assertThat(deletedAt(id)).as("it left Recently deleted").isNull();
        assertThat(objectKeysOf(id)).containsExactly(first, second);
        assertThat(stored(first)).isTrue();
        assertThat(stored(second)).isTrue();
    }

    @Test
    void aRestoredEntryIsAnOrdinaryEntryThatCanBeReadAndEditedAgain() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Back again");
        deleteAt(T1, alice, id);
        get(alice, id).expectStatus().isNotFound();

        int version = JsonPath.read(bodyText(restore(alice, id).expectStatus().isOk()), "$.version");

        assertThat(getEntry(alice, id)).containsEntry("title", "Back again");
        put(alice, id, json("version", version, "entryDate", W4, "title", "Edited after the restore"))
                .expectStatus()
                .isOk();
        assertThat(getEntry(alice, id)).containsEntry("title", "Edited after the restore");
        assertThat(deletedIds(alice)).isEmpty();
    }

    @Test
    void restoringIsAnEditSoTheVersionGoesUpOnceMoreAndAStaleCopyIsRefused() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Versions");
        int written = JsonPath.read(bodyText(get(alice, id).expectStatus().isOk()), "$.version");
        deleteAt(T1, alice, id);
        int whileDeleted = versionOf(id);

        int restored = JsonPath.read(bodyText(restore(alice, id).expectStatus().isOk()), "$.version");

        assertThat(whileDeleted).as("deleting raised it").isEqualTo(written + 1);
        assertThat(restored).as("restoring raised it again").isEqualTo(whileDeleted + 1);
        String stale = bodyText(put(alice, id, json("version", written, "entryDate", W4, "title", "From a stale copy"))
                .expectStatus()
                .isEqualTo(409));
        assertThat(problemType(stale)).isEqualTo("entry-stale");
    }

    @Test
    void restoringChangesNothingButDeletedAt() {
        User alice = aUser();
        String id = entryWithCaption(alice, W2, paragraph("Same words"));
        UUID work = newTag(alice, "Work");
        tag(id, work);
        deleteAt(T1, alice, id);
        Map<String, Object> before = row(id);

        restore(alice, id).expectStatus().isOk();
        Map<String, Object> after = row(id);

        assertThat(after.get("deleted_at")).isNull();
        for (String column : List.of(
                "weekly_log_id",
                "entry_date",
                "title",
                "content_json",
                "content_text",
                "preview_line",
                "unlock_date",
                "unlock_seen_at",
                "unlock_notified",
                "date_unknown",
                "created_at")) {
            assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
        }
        assertThat(countWeeks(alice)).as("no new week").isEqualTo(1);
    }

    // Loot Drops (D-10)

    @Test
    void aSealedLootDropComesBackSealedAndRedacted() {
        User alice = aUser();
        String drop = aLootDrop(alice, "A secret", SEALED_UNTIL);
        aStoredPhoto(drop, 0);
        deleteAt(T1, alice, drop);

        String text = bodyText(restore(alice, drop).expectStatus().isOk());

        assertThat(JsonPath.<Boolean>read(text, "$.isLocked")).isTrue();
        assertThat(JsonPath.<String>read(text, "$.unlockDate")).isEqualTo(SEALED_UNTIL);
        assertThat(JsonPath.<Object>read(text, "$.title")).isNull();
        assertThat(JsonPath.<Object>read(text, "$.contentJson")).isNull();
        assertThat(JsonPath.<List<?>>read(text, "$.media")).isEmpty();
        assertThat(text).doesNotContain("A secret").doesNotContain("fileUrl");
        assertThat(bodyText(get(alice, drop).expectStatus().isOk())).doesNotContain("A secret");
        put(alice, drop, json("version", 5, "entryDate", W4, "title", "Trying to edit"))
                .expectStatus()
                .isEqualTo(409);
    }

    @Test
    void aLootDropThatOpenedWhileItWaitedComesBackWhole() {
        User alice = aUser();
        String drop = aLootDrop(alice, "Opens while deleted", OPENS_1);
        deleteAt(T1, alice, drop);
        clockAt(OPENS_1);

        String text = bodyText(restore(alice, drop).expectStatus().isOk());

        assertThat(JsonPath.<Boolean>read(text, "$.isLocked")).isFalse();
        assertThat(JsonPath.<String>read(text, "$.title")).isEqualTo("Opens while deleted");
    }

    // The seen mark (D-10)

    @Test
    void anOpenedUnseenLootDropReturnsToTheOpenedStripWhenItIsRestored() {
        User alice = aUser();
        String drop = aLootDrop(alice, "Waiting to be seen", OPENS_1);
        clockAt(OPENS_1);
        assertThat(openedIds(alice)).containsExactly(drop);
        deleteOk(alice, drop);
        assertThat(openedIds(alice)).as("deleted: out of the strip").isEmpty();

        restore(alice, drop).expectStatus().isOk();

        assertThat(openedIds(alice))
                .as("restored, still unseen: back in the strip")
                .containsExactly(drop);
        assertThat(seenAt(drop)).isNull();
    }

    @Test
    void aLootDropThatWasSeenStaysSeenAfterTheRestore() {
        User alice = aUser();
        String drop = aLootDrop(alice, "Already seen", OPENS_1);
        clockAt(OPENS_1);
        markSeenOk(alice, drop);
        String seen = seenAt(drop);
        deleteOk(alice, drop);

        restore(alice, drop).expectStatus().isOk();

        assertThat(openedIds(alice)).isEmpty();
        assertThat(seenAt(drop)).as("the mark is kept as it was").isEqualTo(seen);
    }

    // Not in Recently deleted

    @Test
    void anEntryThatIsNotDeletedIs404AndIsLeftAlone() {
        User alice = aUser();
        String live = entryOn(alice, W4, "Live");
        int version = versionOf(live);

        String body = bodyText(restore(alice, live).expectStatus().isNotFound());

        assertThat(problemType(body)).isEqualTo("not-found");
        // The detail never repeats the id (the instance is the request path, which holds it).
        assertThat(JsonPath.<String>read(body, "$.detail")).doesNotContain(live);
        assertThat(versionOf(live)).as("not written").isEqualTo(version);
    }

    @Test
    void restoringTwiceIsTheSecondTimeA404() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Twice");
        deleteAt(T1, alice, id);

        restore(alice, id).expectStatus().isOk();
        int version = versionOf(id);
        restore(alice, id).expectStatus().isNotFound();

        assertThat(versionOf(id)).as("the second call wrote nothing").isEqualTo(version);
    }

    @Test
    void anotherUsersDeletedEntryIs404AndStaysInTheirListWhileAnIdThatDoesNotExistIsTheSame404() {
        User alice = aUser();
        User bob = aUser();
        String bobs = entryOn(bob, W4, "Bob's");
        deleteAt(T1, bob, bobs);

        String foreign = bodyText(restore(alice, bobs).expectStatus().isNotFound());
        String missing =
                bodyText(restore(alice, UUID.randomUUID()).expectStatus().isNotFound());

        assertThat(problemType(foreign)).isEqualTo("not-found");
        assertThat(JsonPath.<String>read(foreign, "$.detail")).isEqualTo(JsonPath.<String>read(missing, "$.detail"));
        assertThat(deletedIds(bob)).as("still waiting for Bob").containsExactly(bobs);
        assertThat(deletedAt(bobs)).isNotNull();
    }

    @Test
    void anErasedEntryCannotBeRestored() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Erased");
        deleteAt(T1, alice, id);
        erase(alice, id).expectStatus().isNoContent();

        restore(alice, id).expectStatus().isNotFound();
    }

    @Test
    void aPathIdThatIsNotAUuidIs400AndAHeaderForNobodyIs404() {
        User alice = aUser();

        String malformed = bodyText(restore(alice, "not-a-uuid").expectStatus().isBadRequest());
        assertThat(problemType(malformed)).isEqualTo("malformed-request");

        String id = entryOn(alice, W4, "Mine");
        deleteAt(T1, alice, id);
        client.post()
                .uri(ENTRIES + "/" + id + "/restore")
                .header(HEADER, UUID.randomUUID().toString())
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    // Overlapping requests (the row is locked while it is looked up)

    @Test
    void eightRestoresAtTheSameMomentGiveOneOkAndSeven404AndOneVersionBump() throws Exception {
        User alice = aUser();
        String id = entryOn(alice, W4, "Race");
        deleteAt(T1, alice, id);
        int whileDeleted = versionOf(id);

        List<Integer> statuses = atTheSameTime(
                8, caller -> restore(alice, id).returnResult().getStatus().value());

        assertThat(statuses).containsExactlyInAnyOrder(200, 404, 404, 404, 404, 404, 404, 404);
        assertThat(versionOf(id)).as("restored once").isEqualTo(whileDeleted + 1);
        assertThat(deletedAt(id)).isNull();
    }

    @Test
    void aRestoreAndAnEraseAtTheSameMomentLeaveOneConsistentOutcomeWhicheverWins() throws Exception {
        for (int round = 0; round < 4; round++) {
            User alice = aUser();
            String id = entryOn(alice, W4, "Round " + round);
            String key = aStoredPhoto(id, 0);
            deleteAt(T1, alice, id);

            List<Integer> statuses = atTheSameTime(
                    2,
                    caller -> caller == 0
                            ? restore(alice, id).returnResult().getStatus().value()
                            : erase(alice, id).returnResult().getStatus().value());

            boolean restoreWon = statuses.get(0) == 200;
            if (restoreWon) {
                assertThat(statuses).as("round " + round).containsExactly(200, 404);
                assertThat(rowExists(id)).isTrue();
                assertThat(deletedAt(id)).isNull();
                assertThat(stored(key)).as("a restored entry keeps its file").isTrue();
            } else {
                assertThat(statuses).as("round " + round).containsExactly(404, 204);
                assertThat(rowExists(id)).isFalse();
                assertThat(stored(key)).as("an erased entry loses its file").isFalse();
            }
        }
    }
}

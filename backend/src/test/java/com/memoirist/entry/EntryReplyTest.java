package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Replies to past you (task 3.13, D-33): {@code replyToEntryId} on {@code POST} and {@code PUT /api/v1/entries} and
 * {@code replyIds} on the Loot Drop that was answered. A reply is an ordinary entry that points at one of the user's own
 * Loot Drops that has opened; these tests cover what it may point at, how the link is edited, what a sealed reply shows
 * (D-10) and what deleting either end does (D-30).
 *
 * <p>Most tests start with {@link #anOpenedLootDrop}: a Loot Drop sealed until {@link #OPENS_1} and then the clock moved
 * to {@link #AFTER_ALL}, so it is open and everything created afterwards is "later".
 */
class EntryReplyTest extends RecentlyDeletedApiTest {

    private static final String LATER = "2026-12-01T00:00:00Z";
    private static final String MESSAGE = "must be one of your Loot Drops that has opened";

    // Helpers

    /** A titled Loot Drop that has opened: sealed, then the clock moves past its unlock date. */
    private String anOpenedLootDrop(User user, String title) {
        String id = aLootDrop(user, title, OPENS_1);
        clockAt(AFTER_ALL);
        return id;
    }

    private RestTestClient.ResponseSpec reply(User user, Object targetId) {
        return post(user, json("entryDate", W4, "title", "Dear past me", "replyToEntryId", targetId));
    }

    /** A reply that must be accepted. Returns its id. */
    private String aReply(User user, Object targetId) {
        RestTestClient.ResponseSpec response = reply(user, targetId);
        response.expectStatus().isCreated();
        return JsonPath.read(bodyText(response), "$.id");
    }

    private static List<String> replyIds(Map<String, Object> entry) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) entry.get("replyIds");
        return ids;
    }

    private String linkOf(Object entryId) {
        Object link = row(entryId).get("reply_to_entry_id");
        return link == null ? null : link.toString();
    }

    // What a reply may point at

    @Test
    void aReplyToAnOpenedLootDropIs201AndNamesItsTarget() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");

        String body = bodyText(reply(alice, drop).expectStatus().isCreated());

        assertThat(JsonPath.<String>read(body, "$.replyToEntryId")).isEqualTo(drop);
        assertThat(JsonPath.<List<String>>read(body, "$.replyIds")).isEmpty();
        assertThat(linkOf(JsonPath.<String>read(body, "$.id"))).isEqualTo(drop);
    }

    @Test
    void theOpenedLootDropListsItsRepliesOldestFirst() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");
        String first = aReply(alice, drop);
        clock.advance(Duration.ofMinutes(5));
        String second = aReply(alice, drop);
        clock.advance(Duration.ofMinutes(5));
        String third = aReply(alice, drop);

        assertThat(replyIds(getEntry(alice, drop))).containsExactly(first, second, third);
        assertThat(getEntry(alice, first).get("replyToEntryId")).isEqualTo(drop);
    }

    @Test
    void anEntryWithNoRepliesAndOneThatWasNeverSealedShowAnEmptyList() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");
        String plain = entryOn(alice, W4, "Plain");

        assertThat(replyIds(getEntry(alice, drop))).isEmpty();
        assertThat(replyIds(getEntry(alice, plain))).isEmpty();
    }

    @Test
    void aSealedLootDropCannotBeRepliedToAndTheAnswerIs400WithNothingCreated() {
        User alice = aUser();
        String drop = aLootDrop(alice, "Dear future me", OPENS_1);
        long before = countEntries(alice);

        String body = bodyText(reply(alice, drop).expectStatus().isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsEntry("replyToEntryId", MESSAGE);
        assertThat(countEntries(alice)).isEqualTo(before);
    }

    @Test
    void anEntryThatWasNeverSealedCannotBeRepliedToAndSaysTheSameAsASealedOne() {
        User alice = aUser();
        String plain = entryOn(alice, W4, "Plain");

        String body = bodyText(reply(alice, plain).expectStatus().isBadRequest());

        assertThat(fieldErrors(body)).containsEntry("replyToEntryId", MESSAGE);
    }

    @Test
    void anotherUsersOpenedLootDropIs404AndLooksLikeOneThatIsNotThere() {
        User alice = aUser();
        User bob = aUser();
        String bobsDrop = anOpenedLootDrop(bob, "Bob's secret");
        long before = countEntries(alice);

        String theirs = bodyText(reply(alice, bobsDrop).expectStatus().isNotFound());
        String missing = bodyText(reply(alice, UUID.randomUUID()).expectStatus().isNotFound());

        assertThat(problemType(theirs)).isEqualTo("not-found");
        assertThat(JsonPath.<String>read(theirs, "$.detail")).isEqualTo(JsonPath.<String>read(missing, "$.detail"));
        assertThat(theirs).doesNotContain(bobsDrop);
        assertThat(countEntries(alice)).isEqualTo(before);
        assertThat(replyIds(getEntry(bob, bobsDrop))).isEmpty();
    }

    @Test
    void aLootDropInRecentlyDeletedCannotBeRepliedTo() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");
        deleteOk(alice, drop);

        reply(alice, drop).expectStatus().isNotFound();
    }

    @Test
    void noReplyTargetAtAllIsAnOrdinaryEntry() {
        User alice = aUser();

        Map<String, Object> created = createEntry(alice, json("entryDate", W4, "title", "Plain"));

        assertThat(created.get("replyToEntryId")).isNull();
        assertThat(linkOf(created.get("id"))).isNull();
    }

    @Test
    void aMalformedReplyTargetIs400() {
        User alice = aUser();

        String body = bodyText(postRaw(alice, "{\"replyToEntryId\":\"not-a-uuid\"}")
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
    }

    // Changing the link with a PUT

    @Test
    void aPutCanLinkAnEntryToAnOpenedLootDropAndUnlinkItByLeavingTheFieldOut() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");
        String id = entryOn(alice, W4, "Later thoughts");
        long version = ((Number) getEntry(alice, id).get("version")).longValue();

        Map<String, Object> linked = bodyMap(put(
                        alice,
                        id,
                        json("version", version, "entryDate", W4, "title", "Later thoughts", "replyToEntryId", drop))
                .expectStatus()
                .isOk());
        assertThat(linked.get("replyToEntryId")).isEqualTo(drop);
        assertThat(replyIds(getEntry(alice, drop))).containsExactly(id);

        Map<String, Object> unlinked =
                bodyMap(put(alice, id, json("version", version + 1, "entryDate", W4, "title", "Later thoughts"))
                        .expectStatus()
                        .isOk());
        assertThat(unlinked.get("replyToEntryId")).isNull();
        assertThat(replyIds(getEntry(alice, drop))).isEmpty();
    }

    @Test
    void aPutThatPointsAtASealedOrNeverSealedOrForeignEntryIsRefusedAndWritesNothing() {
        User alice = aUser();
        User bob = aUser();
        String id = entryOn(alice, W4, "Later thoughts");
        String sealedDrop = aLootDrop(alice, "Still sealed", LATER);
        String plain = entryOn(alice, W4, "Plain");
        String bobsDrop = anOpenedLootDrop(bob, "Bob's");

        for (String target : List.of(sealedDrop, plain)) {
            String body = bodyText(put(alice, id, json("version", 0, "entryDate", W4, "replyToEntryId", target))
                    .expectStatus()
                    .isBadRequest());
            assertThat(fieldErrors(body)).containsEntry("replyToEntryId", MESSAGE);
        }
        put(alice, id, json("version", 0, "entryDate", W4, "replyToEntryId", bobsDrop))
                .expectStatus()
                .isNotFound();

        assertThat(linkOf(id)).isNull();
        assertThat(((Number) getEntry(alice, id).get("version")).longValue()).isZero();
    }

    @Test
    void anEntryCannotReplyToItself() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");
        long version = ((Number) getEntry(alice, drop).get("version")).longValue();

        String body = bodyText(put(alice, drop, json("version", version, "entryDate", W4, "replyToEntryId", drop))
                .expectStatus()
                .isBadRequest());

        assertThat(fieldErrors(body)).containsEntry("replyToEntryId", "an entry can't reply to itself");
        assertThat(linkOf(drop)).isNull();
    }

    @Test
    void aReplyCanStillBeEditedAfterItsLootDropIsSealedAgain() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");
        String id = aReply(alice, drop);
        seal(alice, drop, LATER).expectStatus().isOk();

        Map<String, Object> edited =
                bodyMap(put(alice, id, json("version", 0, "entryDate", W4, "title", "Edited", "replyToEntryId", drop))
                        .expectStatus()
                        .isOk());

        assertThat(edited.get("title")).isEqualTo("Edited");
        assertThat(edited.get("replyToEntryId")).as("the link is unchanged").isEqualTo(drop);
    }

    // Sealed entries (D-10)

    @Test
    void aSealedReplyIsRedactedAndItsLinkCannotBeChanged() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");
        String id = aReply(alice, drop);
        seal(alice, id, LATER).expectStatus().isOk();

        Map<String, Object> shown = getEntry(alice, id);
        assertThat(shown.get("isLocked")).isEqualTo(true);
        assertThat(shown.get("replyToEntryId")).isNull();
        assertThat(replyIds(shown)).isEmpty();

        String body = bodyText(put(alice, id, json("version", 1, "entryDate", W4, "replyToEntryId", null))
                .expectStatus()
                .isEqualTo(409));
        assertThat(problemType(body)).isEqualTo("entry-sealed");
        assertThat(linkOf(id)).as("the link is still there").isEqualTo(drop);
    }

    @Test
    void aSealedReplyIsLeftOutOfTheLootDropsReplyIdsUntilItOpens() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");
        String open = aReply(alice, drop);
        clock.advance(Duration.ofMinutes(1));
        String hidden = aReply(alice, drop);
        seal(alice, hidden, LATER).expectStatus().isOk();

        assertThat(replyIds(getEntry(alice, drop))).containsExactly(open);

        clockAt("2026-12-01T00:00:01Z");
        assertThat(replyIds(getEntry(alice, drop))).containsExactly(open, hidden);
        assertThat(getEntry(alice, hidden).get("replyToEntryId")).isEqualTo(drop);
    }

    @Test
    void aSealedLootDropShowsNoRepliesAndNoLinkEvenWhileItHasThem() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");
        aReply(alice, drop);
        seal(alice, drop, LATER).expectStatus().isOk();

        Map<String, Object> shown = getEntry(alice, drop);

        assertThat(shown.get("isLocked")).isEqualTo(true);
        assertThat(replyIds(shown)).isEmpty();
        assertThat(shown.get("replyToEntryId")).isNull();
    }

    // Deleting either end (D-30, D-33)

    @Test
    void deletingTheReplyLeavesTheLootDropAndRestoringBringsTheLinkBack() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");
        String id = aReply(alice, drop);

        deleteOk(alice, id);
        assertThat(getEntry(alice, drop).get("title")).isEqualTo("Dear future me");
        assertThat(replyIds(getEntry(alice, drop))).isEmpty();

        restore(alice, id).expectStatus().isOk();
        assertThat(replyIds(getEntry(alice, drop))).containsExactly(id);
    }

    @Test
    void erasingTheReplyLeavesTheLootDrop() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");
        String id = aReply(alice, drop);
        deleteOk(alice, id);

        erase(alice, id).expectStatus().isNoContent();

        assertThat(rowExists(id)).isFalse();
        assertThat(getEntry(alice, drop).get("title")).isEqualTo("Dear future me");
        assertThat(replyIds(getEntry(alice, drop))).isEmpty();
    }

    @Test
    void deletingTheLootDropLeavesTheReplyAndRestoringKeepsTheLink() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");
        String id = aReply(alice, drop);

        deleteOk(alice, drop);
        Map<String, Object> reply = getEntry(alice, id);
        assertThat(reply.get("title")).isEqualTo("Dear past me");
        assertThat(reply.get("replyToEntryId"))
                .as("the link waits with the deleted Loot Drop")
                .isEqualTo(drop);

        restore(alice, drop).expectStatus().isOk();
        assertThat(replyIds(getEntry(alice, drop))).containsExactly(id);
    }

    @Test
    void erasingTheLootDropLeavesTheReplyAndClearsItsLink() {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");
        String id = aReply(alice, drop);
        deleteOk(alice, drop);

        erase(alice, drop).expectStatus().isNoContent();

        assertThat(rowExists(drop)).isFalse();
        Map<String, Object> reply = getEntry(alice, id);
        assertThat(reply.get("title")).isEqualTo("Dear past me");
        assertThat(reply.get("replyToEntryId")).isNull();
        assertThat(linkOf(id)).isNull();
    }

    @Test
    void anEmptyLootDropThatIsDeletedIsErasedAtOnceAndTheReplyKeepsLiving() {
        User alice = aUser();
        String drop = createEntryId(alice, json("entryDate", W4));
        sealUntil(alice, drop, OPENS_1);
        clockAt(AFTER_ALL);
        String id = aReply(alice, drop);

        deleteOk(alice, drop);

        assertThat(rowExists(drop)).isFalse();
        assertThat(getEntry(alice, id).get("replyToEntryId")).isNull();
    }

    // Several requests at once

    // This one is a smoke test: with the lock removed it still passed three runs in a row, because the window is a few
    // milliseconds wide. EntryReplyLockTest proves the lock itself and fails without it.
    @Test
    void repliesWrittenAtTheSameTimeAsTheDeleteOfAnEmptyLootDropNeverFailWithA500() throws Exception {
        User alice = aUser();
        String drop = createEntryId(alice, json("entryDate", W4));
        sealUntil(alice, drop, OPENS_1);
        clockAt(AFTER_ALL);

        List<Integer> statuses = atTheSameTime(6, caller -> {
            if (caller == 0) {
                return delete(alice, drop).returnResult().getStatus().value();
            }
            return reply(alice, drop).returnResult().getStatus().value();
        });

        assertThat(statuses).allSatisfy(status -> assertThat(status).isIn(201, 204, 404));
        assertThat(rowExists(drop)).isFalse();
        List<String> links = new ArrayList<>(jdbc.sql("SELECT reply_to_entry_id FROM entries WHERE user_id = :user")
                .param("user", alice.getId())
                .query((rs, n) -> String.valueOf(rs.getObject(1)))
                .list());
        assertThat(links)
                .as("no reply is left pointing at the erased Loot Drop")
                .doesNotContain(drop);
    }

    @Test
    void severalRepliesToTheSameLootDropAtTheSameTimeAllSucceed() throws Exception {
        User alice = aUser();
        String drop = anOpenedLootDrop(alice, "Dear future me");

        List<Integer> statuses = atTheSameTime(
                8, caller -> reply(alice, drop).returnResult().getStatus().value());

        assertThat(statuses).containsOnly(201);
        assertThat(replyIds(getEntry(alice, drop))).hasSize(8);
    }
}

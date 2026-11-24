package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.tag.TagApiTest;
import com.memoirist.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code tagIds} on {@code POST} and {@code PUT /api/v1/entries} and {@code tags} on the {@code Entry} that comes back
 * (task 3.8, D-26): who may be tagged with what, the limit of 20, what a change does to {@code version}, the order the
 * checks run in, and how a sealed Loot Drop (D-10) hides its tags and refuses to change them.
 */
class EntryTagsTest extends TagApiTest {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> tagsOf(Map<String, Object> entry) {
        return (List<Map<String, Object>>) entry.get("tags");
    }

    private static List<String> namesOf(Map<String, Object> entry) {
        return tagsOf(entry).stream().map(tag -> tag.get("name").toString()).toList();
    }

    private static Map<String, Object> edit(int version, Object... more) {
        Map<String, Object> body = json("version", version, "entryDate", "2026-10-31");
        for (int i = 0; i < more.length; i += 2) {
            body.put((String) more[i], more[i + 1]);
        }
        return body;
    }

    // POST: what is stored and shown

    @Test
    void anEntryIsCreatedWithItsTagsAndTheyComeBackInNameOrderWithSourceUser() {
        User alice = aUser();
        UUID work = aTag(alice, "work");
        UUID family = aTag(alice, "Family");
        UUID zoo = aTag(alice, "Zoo");

        Map<String, Object> entry = bodyMap(post(alice, json("title", "Saturday", "tagIds", ids(zoo, work, family)))
                .expectStatus()
                .isCreated());

        assertThat(namesOf(entry)).as("by name, not in the order sent").containsExactly("Family", "work", "Zoo");
        assertThat(tagsOf(entry)).allSatisfy(tag -> {
            assertThat(tag.keySet()).containsExactlyInAnyOrder("id", "name", "source");
            assertThat(tag).containsEntry("source", "USER");
        });
        assertThat(linkedTagIds(entry.get("id"))).containsExactlyInAnyOrder(work, family, zoo);
        assertThat(namesOf(getEntry(alice, entry.get("id")))).containsExactly("Family", "work", "Zoo");
        assertThat(jdbc.sql("SELECT DISTINCT source FROM entry_tags WHERE entry_id = :id")
                        .param("id", UUID.fromString(entry.get("id").toString()))
                        .query(String.class)
                        .list())
                .containsExactly("USER");
        assertThat(row(entry.get("id"))).containsEntry("version", 0L);
    }

    @Test
    void aMissingNullOrEmptyListMeansNoTags() {
        User alice = aUser();

        assertThat(tagsOf(createEntry(alice, json("title", "a")))).isEmpty();
        assertThat(tagsOf(createEntry(alice, json("title", "b", "tagIds", null))))
                .isEmpty();
        assertThat(tagsOf(createEntry(alice, json("title", "c", "tagIds", List.of()))))
                .isEmpty();
    }

    @Test
    void anIdRepeatedInTheListIsOneTag() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");

        Map<String, Object> entry = bodyMap(post(alice, json("title", "x", "tagIds", ids(work, work, work)))
                .expectStatus()
                .isCreated());

        assertThat(namesOf(entry)).containsExactly("Work");
        assertThat(linkedTagIds(entry.get("id"))).containsExactly(work);
    }

    @Test
    void theSameTagCanBeOnManyEntriesAndEveryEntryKeepsItsOwnSet() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        UUID family = aTag(alice, "Family");

        String first = createEntryId(alice, json("title", "1", "tagIds", ids(work, family)));
        String second = createEntryId(alice, json("title", "2", "tagIds", ids(work)));

        assertThat(linkedTagIds(first)).containsExactlyInAnyOrder(work, family);
        assertThat(linkedTagIds(second)).containsExactly(work);
        assertThat(countLinks(work)).isEqualTo(2);
    }

    @Test
    void aReplayedPostDoesNotApplyItsTagIds() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        UUID id = UUID.randomUUID();
        createEntry(alice, json("id", id.toString(), "title", "First"));

        Map<String, Object> again =
                bodyMap(post(alice, json("id", id.toString(), "title", "Again", "tagIds", ids(work)))
                        .expectStatus()
                        .isOk());

        assertThat(tagsOf(again)).isEmpty();
        assertThat(linkedTagIds(id)).isEmpty();
    }

    // POST: the limits

    @Test
    void twentyTagsAreAllowedAndTwentyOneIs400() {
        User alice = aUser();
        List<UUID> twentyOne = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            twentyOne.add(aTag(alice, "Tag " + i));
        }

        Map<String, Object> ok = bodyMap(post(
                        alice,
                        json(
                                "title",
                                "twenty",
                                "tagIds",
                                ids(twentyOne.subList(0, 20).toArray(UUID[]::new))))
                .expectStatus()
                .isCreated());
        assertThat(tagsOf(ok)).hasSize(20);
        long before = countEntries(alice);

        String body = bodyText(post(alice, json("title", "twenty-one", "tagIds", ids(twentyOne.toArray(UUID[]::new))))
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body))
                .containsOnlyKeys("tagIds")
                .containsEntry("tagIds", "size must be between 0 and 20");
        assertThat(countEntries(alice)).as("nothing was created").isEqualTo(before);
    }

    @Test
    void theLimitCountsTheListAsSentSoTwentyOneCopiesOfOneIdIs400() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        List<String> copies = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            copies.add(work.toString());
        }

        String body =
                bodyText(post(alice, json("tagIds", copies)).expectStatus().isBadRequest());

        assertThat(fieldErrors(body)).containsOnlyKeys("tagIds");
        assertThat(countEntries(alice)).isZero();
    }

    @Test
    void aNullItemIsNamedByItsPositionAndAWordIsMalformed() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        List<Object> withNull = new ArrayList<>();
        withNull.add(work.toString());
        withNull.add(null);

        String nullItem =
                bodyText(post(alice, json("tagIds", withNull)).expectStatus().isBadRequest());
        assertThat(problemType(nullItem)).isEqualTo("validation-failed");
        assertThat(fieldErrors(nullItem)).containsOnlyKeys("tagIds[1]").containsEntry("tagIds[1]", "must not be null");

        String word = bodyText(
                post(alice, json("tagIds", List.of("work"))).expectStatus().isBadRequest());
        assertThat(problemType(word)).isEqualTo("malformed-request");
        assertThat(countEntries(alice)).isZero();
    }

    // POST: whose tags

    @Test
    void anotherUsersTagAnUnknownTagAndAMixOfBothAreTheSame404AndNothingIsCreated() {
        User alice = aUser();
        User bob = aUser();
        UUID mine = aTag(alice, "Mine");
        UUID bobs = aTag(bob, "Bob's");
        UUID nobodys = UUID.randomUUID();

        List<String> bodies = new ArrayList<>();
        for (List<String> tagIds : List.of(ids(bobs), ids(nobodys), ids(mine, bobs), ids(bobs, mine, nobodys))) {
            String body = bodyText(post(alice, json("title", "x", "tagIds", tagIds))
                    .expectStatus()
                    .isNotFound());
            assertThat(problemType(body)).isEqualTo("not-found");
            assertThat(body).doesNotContain(bobs.toString()).doesNotContain(nobodys.toString());
            bodies.add(withoutTimestampAndInstance(body));
        }

        assertThat(bodies).as("the client cannot tell the cases apart").containsOnly(bodies.get(0));
        assertThat(countEntries(alice)).as("no entry").isZero();
        assertThat(countWeeks(alice)).as("and no week").isZero();
        assertThat(countLinks(bobs)).isZero();
        assertThat(countLinks(mine)).isZero();
    }

    /** The error without the two fields that differ between calls: when it happened. */
    private static String withoutTimestampAndInstance(String body) {
        return body.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "");
    }

    @Test
    void theTagsAreCheckedAfterTheDateAndTheCaption() {
        User alice = aUser();
        User bob = aUser();
        UUID bobs = aTag(bob, "Bob's");

        String futureDate = bodyText(post(alice, json("entryDate", "2999-01-01", "tagIds", ids(bobs)))
                .expectStatus()
                .isBadRequest());
        assertThat(fieldErrors(futureDate)).containsOnlyKeys("entryDate");

        String badCaption = bodyText(post(alice, json("contentJson", json("type", "image"), "tagIds", ids(bobs)))
                .expectStatus()
                .isBadRequest());
        assertThat(fieldErrors(badCaption)).containsOnlyKeys("contentJson");
    }

    // PUT: replacing the set

    @Test
    void aPutReplacesTheWholeSetAndEachChangeRaisesTheVersionByOne() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        UUID family = aTag(alice, "Family");
        UUID travel = aTag(alice, "Travel");
        String id = createEntryId(alice, json("title", "Mine", "tagIds", ids(work)));

        Map<String, Object> added = bodyMap(put(alice, id, edit(0, "title", "Mine", "tagIds", ids(work, family)))
                .expectStatus()
                .isOk());
        assertThat(namesOf(added)).containsExactly("Family", "Work");
        assertThat(added.get("version")).isEqualTo(1);

        Map<String, Object> swapped = bodyMap(put(alice, id, edit(1, "title", "Mine", "tagIds", ids(travel, family)))
                .expectStatus()
                .isOk());
        assertThat(namesOf(swapped)).containsExactly("Family", "Travel");
        assertThat(swapped.get("version")).isEqualTo(2);
        assertThat(linkedTagIds(id)).containsExactlyInAnyOrder(family, travel);
        assertThat(countLinks(work))
                .as("the tag itself is untouched, only the link went")
                .isZero();
        assertThat(tagRow(work)).containsEntry("name", "Work");

        Map<String, Object> cleared = bodyMap(put(alice, id, edit(2, "title", "Mine", "tagIds", List.of()))
                .expectStatus()
                .isOk());
        assertThat(tagsOf(cleared)).isEmpty();
        assertThat(cleared.get("version")).isEqualTo(3);
        assertThat(linkedTagIds(id)).isEmpty();
        assertThat(countTags(alice)).as("three tags still exist").isEqualTo(3);
    }

    @Test
    void changingOnlyTheTagsStillMovesUpdatedAtAsAnyEditDoes() throws InterruptedException {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        String id = createEntryId(alice, json("title", "Mine"));
        String before = instantOf(row(id).get("updated_at"));
        Thread.sleep(20); // updated_at is stamped by the JVM clock, so let it tick

        put(alice, id, edit(0, "title", "Mine", "tagIds", ids(work)))
                .expectStatus()
                .isOk();

        assertThat(instantOf(row(id).get("updated_at"))).isNotEqualTo(before);
        assertThat(row(id)).containsEntry("version", 1L);
    }

    @Test
    void aPutThatNamesTheTagsTheEntryAlreadyHasWritesNothing() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        UUID family = aTag(alice, "Family");
        String id = createEntryId(alice, json("title", "Mine", "tagIds", ids(work, family)));
        Map<String, Object> before = row(id);

        // The same two, in the other order and with a repeat.
        Map<String, Object> answer =
                bodyMap(put(alice, id, edit(0, "title", "Mine", "tagIds", ids(family, work, family)))
                        .expectStatus()
                        .isOk());

        assertThat(row(id)).as("not even the version moved").isEqualTo(before);
        assertThat(namesOf(answer)).containsExactly("Family", "Work");
        assertThat(answer.get("version")).isEqualTo(0);
    }

    @Test
    void aPutCanChangeTheTagsTogetherWithTheDateAndTheCaptionInOneVersionStep() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        String id = createEntryId(alice, json("title", "Mine"));

        Map<String, Object> answer = bodyMap(put(
                        alice,
                        id,
                        edit(
                                0,
                                "entryDate",
                                "2026-10-21",
                                "title",
                                "Moved",
                                "contentJson",
                                doc("words"),
                                "tagIds",
                                ids(work)))
                .expectStatus()
                .isOk());

        assertThat(answer.get("version")).as("one edit, one step").isEqualTo(1);
        assertThat(namesOf(answer)).containsExactly("Work");
        assertThat(weekStartOf(id)).isEqualTo("2026-10-19");
    }

    // PUT: what is required, whose tags, and in which order

    @Test
    void tagIdsIsRequiredOnAPutAndAnEntryKeepsItsTagsWhenTheListIsMissing() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        String id = createEntryId(alice, json("title", "Mine", "tagIds", ids(work)));
        Map<String, Object> before = row(id);

        String missing = bodyText(
                putAsSent(alice, id, edit(0, "title", "Mine")).expectStatus().isBadRequest());
        assertThat(problemType(missing)).isEqualTo("validation-failed");
        assertThat(fieldErrors(missing)).containsOnlyKeys("tagIds").containsEntry("tagIds", "must not be null");

        String explicitNull = bodyText(putAsSent(alice, id, edit(0, "title", "Mine", "tagIds", null))
                .expectStatus()
                .isBadRequest());
        assertThat(fieldErrors(explicitNull)).containsOnlyKeys("tagIds");

        assertThat(row(id)).isEqualTo(before);
        assertThat(linkedTagIds(id)).containsExactly(work);
    }

    @Test
    void aPutIsHeldToTheSameLimitsAsAPost() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        String id = createEntryId(alice, json("title", "Mine"));
        List<String> twentyOne = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            twentyOne.add(work.toString());
        }
        List<Object> withNull = new ArrayList<>();
        withNull.add(null);

        String tooMany = bodyText(
                put(alice, id, edit(0, "tagIds", twentyOne)).expectStatus().isBadRequest());
        assertThat(fieldErrors(tooMany)).containsOnlyKeys("tagIds");

        String nullItem = bodyText(
                put(alice, id, edit(0, "tagIds", withNull)).expectStatus().isBadRequest());
        assertThat(fieldErrors(nullItem)).containsOnlyKeys("tagIds[0]");

        assertThat(row(id)).containsEntry("version", 0L);
    }

    @Test
    void aPutNamingSomeoneElsesTagOrAnUnknownOneIs404AndTheEntryIsUntouched() {
        User alice = aUser();
        User bob = aUser();
        UUID mine = aTag(alice, "Mine");
        UUID bobs = aTag(bob, "Bob's");
        String id = createEntryId(alice, json("title", "Mine", "tagIds", ids(mine)));
        Map<String, Object> before = row(id);

        for (List<String> tagIds : List.of(ids(bobs), ids(UUID.randomUUID()), ids(mine, bobs))) {
            String body = bodyText(put(alice, id, edit(0, "title", "Changed", "tagIds", tagIds))
                    .expectStatus()
                    .isNotFound());
            assertThat(problemType(body)).isEqualTo("not-found");
            assertThat(body).doesNotContain(bobs.toString());
        }

        assertThat(row(id)).as("not even the title changed").isEqualTo(before);
        assertThat(linkedTagIds(id)).containsExactly(mine);
        assertThat(countLinks(bobs)).isZero();
    }

    @Test
    void aStaleVersionIsToldBeforeAnyTagIsLookedAtAndALaterDateBeforeThat() {
        User alice = aUser();
        User bob = aUser();
        UUID bobs = aTag(bob, "Bob's");
        String id = createEntryId(alice, json("title", "Mine"));
        put(alice, id, edit(0, "title", "Newer")).expectStatus().isOk();

        String stale = bodyText(
                put(alice, id, edit(0, "tagIds", ids(bobs))).expectStatus().isEqualTo(409));
        assertThat(problemType(stale)).isEqualTo("entry-stale");

        String futureDate = bodyText(put(alice, id, edit(1, "entryDate", "2999-01-01", "tagIds", ids(bobs)))
                .expectStatus()
                .isBadRequest());
        assertThat(fieldErrors(futureDate)).containsOnlyKeys("entryDate");
    }

    @Test
    void anotherUsersEntryIs404EvenWhenTheTagsAreFine() {
        User alice = aUser();
        User bob = aUser();
        UUID mine = aTag(alice, "Mine");
        String bobsEntry = createEntryId(bob, json("title", "Bob's"));

        put(alice, bobsEntry, edit(0, "tagIds", ids(mine))).expectStatus().isNotFound();

        assertThat(linkedTagIds(bobsEntry)).isEmpty();
    }

    // The seal (D-10)

    @Test
    void aSealedEntryShowsNoTagsThoughItKeepsThemAndTheyReturnWhenItOpens() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        String id = createEntryId(alice, json("title", "For later", "tagIds", ids(work)));

        Map<String, Object> sealed =
                bodyMap(seal(alice, id, "2026-12-25T00:00:00Z").expectStatus().isOk());
        assertThat(tagsOf(sealed)).as("the seal answer").isEmpty();
        assertThat(tagsOf(getEntry(alice, id))).as("a read").isEmpty();
        assertThat(bodyText(get(alice, id))).doesNotContain("Work").doesNotContain(work.toString());
        assertThat(linkedTagIds(id)).as("the link is kept").containsExactly(work);

        clockAt("2026-12-25T00:00:01Z");
        assertThat(namesOf(getEntry(alice, id))).as("opened").containsExactly("Work");
    }

    @Test
    void aSealedEntryCannotHaveItsTagsChangedWhateverTheListHolds() {
        User alice = aUser();
        User bob = aUser();
        UUID mine = aTag(alice, "Mine");
        UUID other = aTag(alice, "Other");
        UUID bobs = aTag(bob, "Bob's");
        String id = createEntryId(alice, json("title", "For later", "tagIds", ids(mine)));
        seal(alice, id, "2026-12-25T00:00:00Z").expectStatus().isOk();
        Map<String, Object> before = row(id);

        List<String> answers = new ArrayList<>();
        for (List<String> tagIds : List.of(ids(other), ids(), ids(bobs), ids(UUID.randomUUID()))) {
            String body = bodyText(
                    put(alice, id, edit(1, "tagIds", tagIds)).expectStatus().isEqualTo(409));
            assertThat(problemType(body)).isEqualTo("entry-sealed");
            answers.add(withoutTimestampAndInstance(body));
        }

        assertThat(answers)
                .as("a good tag, none, someone else's and an unknown one all get the same answer")
                .containsOnly(answers.get(0));
        assertThat(row(id)).isEqualTo(before);
        assertThat(linkedTagIds(id)).containsExactly(mine);
    }

    @Test
    void aLootDropThatHasOpenedTakesTagChangesAgain() {
        User alice = aUser();
        UUID mine = aTag(alice, "Mine");
        UUID other = aTag(alice, "Other");
        String id = createEntryId(alice, json("title", "For later", "tagIds", ids(mine)));
        seal(alice, id, "2026-12-25T00:00:00Z").expectStatus().isOk();
        clockAt("2026-12-26T00:00:00Z");

        Map<String, Object> answer = bodyMap(put(alice, id, edit(1, "entryDate", "2026-10-31", "tagIds", ids(other)))
                .expectStatus()
                .isOk());

        assertThat(namesOf(answer)).containsExactly("Other");
        assertThat(linkedTagIds(id)).containsExactly(other);
    }
}

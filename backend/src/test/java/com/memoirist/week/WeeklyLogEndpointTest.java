package com.memoirist.week;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.SqlLog;
import com.memoirist.user.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * {@code /api/v1/weekly-logs/{logId}}: one week's detail, its title, closing and reopening, and how its Eras follow
 * the Eras' dates (API_CONTRACT, Weekly logs; D-08, D-10, D-22, D-25, D-30). The feed is {@link FeedEndpointTest}.
 */
class WeeklyLogEndpointTest extends WeekApiTest {

    private static final String SEALED_UNTIL = "2027-01-01T00:00:00Z";

    // GET: the week and its entries

    @Test
    void aWeekComesBackWithTheSameFieldsAsInTheFeed() {
        User alice = aUser();
        String entryId = entryOn(alice, W3, "Lab day");
        createEra(alice, "Trip", "2026-10-20", "2026-10-22", false);
        String weekId = weekIdOf(entryId);

        String week = weekBody(alice, weekId);

        assertThat(JsonPath.<Map<String, Object>>read(week, "$").keySet())
                .containsExactlyInAnyOrder(
                        "id", "title", "startDate", "endDate", "isClosed", "soundtrack", "eras", "entries");
        assertThat(JsonPath.<String>read(week, "$.id")).isEqualTo(weekId);
        assertThat(JsonPath.<String>read(week, "$.startDate")).isEqualTo(W3);
        assertThat(JsonPath.<String>read(week, "$.endDate")).isEqualTo("2026-10-25");
        assertThat(JsonPath.<Boolean>read(week, "$.isClosed")).isFalse();
        assertThat(eraTitles(week)).containsExactly("Trip");
        assertThat(JsonPath.<List<String>>read(week, "$.entries[*].id")).containsExactly(entryId);
        assertThat(JsonPath.<Object>read(week, "$.entries[0].title")).isEqualTo("Lab day");
        // The feed shows the very same week.
        assertThat(JsonPath.<Object>read(feedBody(alice), "$.items[0].eras")).isEqualTo(JsonPath.read(week, "$.eras"));
    }

    @Test
    void entriesRunFromTheOldestMemoryDateAndThenTheOldestWritten() {
        User alice = aUser();
        String wednesdayFirst = entryOn(alice, "2026-10-21", "Wednesday, written first");
        String monday = entryOn(alice, "2026-10-19", "Monday");
        String wednesdayLater = entryOn(alice, "2026-10-21", "Wednesday, written later");

        String week = weekBody(alice, weekIdOf(monday));

        assertThat(JsonPath.<List<String>>read(week, "$.entries[*].id"))
                .containsExactly(monday, wednesdayFirst, wednesdayLater);
    }

    @Test
    void aSealedEntryIsALockedRowWithNothingPrivateInWeekDetail() {
        User alice = aUser();
        String open = entryOn(alice, W3, "An open entry");
        String drop = createEntryId(
                alice, json("entryDate", W3, "title", "SECRET-TITLE", "contentJson", doc("SECRET words")));
        aPhoto(drop, "users/test/entries/drop/SECRET-KEY.jpg", 0);
        sealUntil(alice, drop, SEALED_UNTIL);

        String week = weekBody(alice, weekIdOf(open));

        assertThat(week).as("nothing private anywhere in the answer").doesNotContain("SECRET");
        List<Map<String, Object>> locked = JsonPath.read(week, "$.entries[?(@.isLocked == true)]");
        assertThat(locked).hasSize(1);
        assertThat(locked.getFirst().get("id")).isEqualTo(drop);
        assertThat(locked.getFirst().get("unlockDate")).isEqualTo(SEALED_UNTIL);
        assertThat(locked.getFirst().get("title")).isNull();
        assertThat(locked.getFirst().get("media")).isEqualTo(List.of());
    }

    @Test
    void aSealedEntryOpensInWeekDetailAtTheUnlockMoment() {
        User alice = aUser();
        String drop = entryOn(alice, W3, "Opens soon");
        sealUntil(alice, drop, "2026-11-02T01:00:00Z");
        String weekId = weekIdOf(drop);

        clockAt("2026-11-02T00:59:59Z");
        assertThat(weekBody(alice, weekId)).doesNotContain("Opens soon");

        clockAt("2026-11-02T01:00:00Z");
        assertThat(JsonPath.<List<String>>read(weekBody(alice, weekId), "$.entries[*].title"))
                .containsExactly("Opens soon");
    }

    @Test
    void entriesInRecentlyDeletedAreNotInTheWeekAndAWeekWithNoneLeftIsStillFound() {
        User alice = aUser();
        String kept = entryOn(alice, W3, "Kept");
        String deleted = entryOn(alice, W3, "Deleted");
        String weekId = weekIdOf(kept);
        delete(alice, deleted).expectStatus().isNoContent();

        assertThat(JsonPath.<List<String>>read(weekBody(alice, weekId), "$.entries[*].id"))
                .containsExactly(kept);

        delete(alice, kept).expectStatus().isNoContent();
        String empty = weekBody(alice, weekId);
        assertThat(JsonPath.<List<Object>>read(empty, "$.entries")).isEmpty();
        assertThat(JsonPath.<String>read(empty, "$.startDate")).isEqualTo(W3);
        assertThat(weekStarts(feedBody(alice))).as("but the feed hides it").isEmpty();
    }

    @Test
    void aWeekOfAnotherUserOrOfNobodyIs404() {
        User alice = aUser();
        User bob = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Alice's"));

        for (String id : List.of(weekId, UUID.randomUUID().toString())) {
            String body = bodyText(getWeek(bob, id).expectStatus().isNotFound());
            assertThat(problemType(body)).isEqualTo("not-found");
            // `instance` is the request path, which the caller sent; `detail` is ours and never repeats the id.
            assertThat(JsonPath.<String>read(body, "$.detail")).doesNotContain(id);
        }
        putWeek(bob, weekId, json("title", "Mine now")).expectStatus().isNotFound();
        closeWeek(bob, weekId).expectStatus().isNotFound();
        reopenWeek(bob, weekId).expectStatus().isNotFound();
        assertThat(JsonPath.<Object>read(weekBody(alice, weekId), "$.title")).isNull();
    }

    @Test
    void aWeekIdThatIsNotAUuidIs400AndThereIsNoWayToDeleteOrCreateAWeek() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Entry"));

        assertThat(problemType(
                        bodyText(getWeek(alice, "not-a-uuid").expectStatus().isBadRequest())))
                .isEqualTo("malformed-request");
        client.delete()
                .uri(WEEKLY_LOGS + "/" + weekId)
                .header(HEADER, alice.getId().toString())
                .exchange()
                .expectStatus()
                .isEqualTo(405);
        client.post()
                .uri(WEEKLY_LOGS)
                .header(HEADER, alice.getId().toString())
                .exchange()
                .expectStatus()
                .isNotFound();
        assertThat(countWeeks(alice))
                .as("weeks are made by entries, not by the API")
                .isEqualTo(1);
    }

    // A week's Eras follow the Eras' dates

    @Test
    void aNewEraAppearsOnTheWeekAtOnceAndLeavesWhenItIsDeleted() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Entry"));
        assertThat(eraTitles(weekBody(alice, weekId))).isEmpty();

        String eraId = createEra(alice, "Brand new", "2026-10-01", "2026-12-31", false);
        assertThat(eraTitles(weekBody(alice, weekId))).containsExactly("Brand new");

        deleteEra(alice, eraId);
        assertThat(eraTitles(weekBody(alice, weekId))).isEmpty();
    }

    @Test
    void changingAnErasDatesChangesWhichWeeksItHoldsAsSoonAsItIsSaved() {
        User alice = aUser();
        String w3 = weekIdOf(entryOn(alice, W3, "In W3"));
        String w4 = weekIdOf(entryOn(alice, W4, "In W4"));
        String eraId = createEra(alice, "Moving Era", "2026-10-19", "2026-10-25", false);
        assertThat(eraTitles(weekBody(alice, w3))).containsExactly("Moving Era");
        assertThat(eraTitles(weekBody(alice, w4))).isEmpty();

        putEra(alice, eraId, json("title", "Moving Era", "startDate", "2026-10-26", "endDate", "2026-11-01"))
                .expectStatus()
                .isOk();

        assertThat(eraTitles(weekBody(alice, w3))).as("no longer holds W3").isEmpty();
        assertThat(eraTitles(weekBody(alice, w4))).as("now holds W4").containsExactly("Moving Era");
        String feed = feedBody(alice);
        assertThat(JsonPath.<List<List<String>>>read(feed, "$.items[*].eras[*].title"))
                .as("the feed agrees: only W4 (the first item) lists it")
                .hasSize(1);
        assertThat(JsonPath.<String>read(feed, "$.items[0].eras[0].title")).isEqualTo("Moving Era");
    }

    @Test
    void anEraThatEndsOnTheWeeksMondayHoldsItAndOneThatEndedTheSundayBeforeDoesNot() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Entry"));
        String eraId = createEra(alice, "Edge", "2026-09-01", "2026-10-19", false);
        assertThat(eraTitles(weekBody(alice, weekId)))
                .as("ends on the Monday: holds")
                .containsExactly("Edge");

        putEra(alice, eraId, json("title", "Edge", "startDate", "2026-09-01", "endDate", "2026-10-18"))
                .expectStatus()
                .isOk();
        assertThat(eraTitles(weekBody(alice, weekId)))
                .as("ended the Sunday before")
                .isEmpty();

        putEra(alice, eraId, json("title", "Edge", "startDate", "2026-10-25", "endDate", null))
                .expectStatus()
                .isOk();
        assertThat(eraTitles(weekBody(alice, weekId)))
                .as("starts on the Sunday: holds")
                .containsExactly("Edge");
    }

    @Test
    void pinningAnEraPutsItFirstAndUnpinningPutsItBack() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Entry"));
        String older = createEra(alice, "Older start", "2026-01-05", null, false);
        createEra(alice, "Later start", "2026-10-01", null, false);
        assertThat(eraTitles(weekBody(alice, weekId))).containsExactly("Later start", "Older start");

        putEra(alice, older, json("title", "Older start", "startDate", "2026-01-05", "pinnedInFeed", true))
                .expectStatus()
                .isOk();
        assertThat(eraTitles(weekBody(alice, weekId))).containsExactly("Older start", "Later start");

        putEra(alice, older, json("title", "Older start", "startDate", "2026-01-05", "pinnedInFeed", false))
                .expectStatus()
                .isOk();
        assertThat(eraTitles(weekBody(alice, weekId))).containsExactly("Later start", "Older start");
    }

    @Test
    void severalPinnedErasKeepTheLatestStartFirstAndSameDayErasTheNewestCreated() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Entry"));
        createEra(alice, "Pinned, early", "2026-01-05", null, true);
        createEra(alice, "Pinned, late", "2026-09-01", null, true);
        createEra(alice, "Unpinned, latest of all", "2026-10-19", null, false);
        createEra(alice, "Same day, first made", "2026-06-01", null, false);
        createEra(alice, "Same day, made later", "2026-06-01", null, false);

        assertThat(eraTitles(weekBody(alice, weekId)))
                .containsExactly(
                        "Pinned, late",
                        "Pinned, early",
                        "Unpinned, latest of all",
                        "Same day, made later",
                        "Same day, first made");
    }

    // PUT: the title

    @Test
    void thePutSetsAndReplacesTheTitleAndTheNextReadShowsIt() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Entry"));

        String saved = bodyText(putWeek(alice, weekId, json("title", "Grampians Trip Week"))
                .expectStatus()
                .isOk());

        assertThat(JsonPath.<String>read(saved, "$.title")).isEqualTo("Grampians Trip Week");
        assertThat(JsonPath.<String>read(weekBody(alice, weekId), "$.title")).isEqualTo("Grampians Trip Week");
        assertThat(JsonPath.<String>read(feedBody(alice), "$.items[0].title")).isEqualTo("Grampians Trip Week");

        putWeek(alice, weekId, json("title", "Renamed")).expectStatus().isOk();
        assertThat(JsonPath.<String>read(weekBody(alice, weekId), "$.title")).isEqualTo("Renamed");
    }

    @Test
    void thePutAnswersWithTheWholeWeekIncludingItsEntriesAndEras() {
        User alice = aUser();
        String entryId = entryOn(alice, W3, "Entry");
        createEra(alice, "Trip", "2026-10-20", "2026-10-22", false);

        String saved = bodyText(putWeek(alice, weekIdOf(entryId), json("title", "Named"))
                .expectStatus()
                .isOk());

        assertThat(JsonPath.<List<String>>read(saved, "$.entries[*].id")).containsExactly(entryId);
        assertThat(eraTitles(saved)).containsExactly("Trip");
    }

    @Test
    void aTitleIsStrippedAndAnEmptyOrMissingOneClearsTheTitle() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Entry"));

        putWeek(alice, weekId, json("title", "  Padded  ")).expectStatus().isOk();
        assertThat(JsonPath.<String>read(weekBody(alice, weekId), "$.title")).isEqualTo("Padded");

        for (Map<String, Object> clearing :
                List.of(json("title", ""), json("title", "   "), json("title", null), json())) {
            putWeek(alice, weekId, json("title", "Something")).expectStatus().isOk();
            String saved =
                    bodyText(putWeek(alice, weekId, clearing).expectStatus().isOk());
            assertThat(JsonPath.<Object>read(saved, "$.title"))
                    .as("body " + clearing)
                    .isNull();
            assertThat(titleInDatabase(weekId))
                    .as("stored as null, not as empty text")
                    .isNull();
        }
    }

    @Test
    void aTitleOf200CharactersIsAcceptedAndOneOverIsA400() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Entry"));

        putWeek(alice, weekId, json("title", "t".repeat(200))).expectStatus().isOk();
        putWeek(alice, weekId, json("title", "  " + "t".repeat(200) + "  "))
                .expectStatus()
                .isOk();
        String body = bodyText(putWeek(alice, weekId, json("title", "t".repeat(201)))
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsEntry("title", "size must be between 0 and 200");
        assertThat(titleInDatabase(weekId)).as("the bad PUT saved nothing").hasSize(200);
    }

    @Test
    void thePutIgnoresEveryFieldItDoesNotOwn() {
        User alice = aUser();
        String entryId = entryOn(alice, W3, "Entry");
        String weekId = weekIdOf(entryId);
        long versionBefore = (long) row(entryId).get("version");

        putWeek(
                        alice,
                        weekId,
                        json(
                                "title",
                                "Only this",
                                "isClosed",
                                true,
                                "startDate",
                                "2020-01-06",
                                "soundtrack",
                                "spotify:track:1a2B3c4D5e6F7g8H9i0J1k",
                                "eras",
                                List.of(),
                                "userId",
                                UUID.randomUUID().toString()))
                .expectStatus()
                .isOk();

        Map<String, Object> row = jdbc.sql("SELECT * FROM weekly_logs WHERE id = :id")
                .param("id", UUID.fromString(weekId))
                .query()
                .singleRow();
        assertThat(row.get("title")).isEqualTo("Only this");
        assertThat(row.get("is_closed")).isEqualTo(false);
        assertThat(row.get("start_date").toString()).isEqualTo(W3);
        assertThat(row.get("soundtrack"))
                .as("soundtracks arrive with task 3.14")
                .isNull();
        assertThat(row.get("user_id")).isEqualTo(alice.getId());
        assertThat((long) row(entryId).get("version")).as("no entry changed").isEqualTo(versionBefore);
    }

    @Test
    void aBodyThatIsNotJsonIs415AndBrokenJsonIs400() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Entry"));

        client.put()
                .uri(WEEKLY_LOGS + "/" + weekId)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.TEXT_PLAIN)
                .body("title=Nope")
                .exchange()
                .expectStatus()
                .isEqualTo(415);
        String body = bodyText(client.put()
                .uri(WEEKLY_LOGS + "/" + weekId)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"title\": ")
                .exchange()
                .expectStatus()
                .isBadRequest());
        assertThat(problemType(body)).isEqualTo("malformed-request");
    }

    @Test
    void aPutThatChangesNothingWritesNothing() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Entry"));
        putWeek(alice, weekId, json("title", "Same")).expectStatus().isOk();

        try (SqlLog sql = SqlLog.capture()) {
            putWeek(alice, weekId, json("title", "Same")).expectStatus().isOk();

            assertThat(sql.statementsStartingWith("update")).isEmpty();
        }
    }

    // Close and reopen

    @Test
    void closingAndReopeningAWeekToggleIsClosedAndAnswerWithTheWeek() {
        User alice = aUser();
        String entryId = entryOn(alice, W3, "Entry");
        String weekId = weekIdOf(entryId);
        putWeek(alice, weekId, json("title", "Named")).expectStatus().isOk();

        String closed = bodyText(closeWeek(alice, weekId).expectStatus().isOk());
        assertThat(JsonPath.<Boolean>read(closed, "$.isClosed")).isTrue();
        assertThat(JsonPath.<Map<String, Object>>read(closed, "$")).doesNotContainKey("closed");
        assertThat(JsonPath.<String>read(closed, "$.title"))
                .as("closing keeps the title")
                .isEqualTo("Named");
        assertThat(JsonPath.<List<String>>read(closed, "$.entries[*].id")).containsExactly(entryId);
        assertThat(JsonPath.<Boolean>read(weekBody(alice, weekId), "$.isClosed"))
                .isTrue();
        assertThat(JsonPath.<Boolean>read(feedBody(alice), "$.items[0].isClosed"))
                .isTrue();

        String reopened = bodyText(reopenWeek(alice, weekId).expectStatus().isOk());
        assertThat(JsonPath.<Boolean>read(reopened, "$.isClosed")).isFalse();
        assertThat(JsonPath.<Boolean>read(weekBody(alice, weekId), "$.isClosed"))
                .isFalse();
        assertThat(JsonPath.<String>read(reopened, "$.title")).isEqualTo("Named");
    }

    @Test
    void closingAClosedWeekAndReopeningAnOpenOneAreNotErrorsAndWriteNothing() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Entry"));

        try (SqlLog sql = SqlLog.capture()) {
            reopenWeek(alice, weekId).expectStatus().isOk();
            assertThat(sql.statementsStartingWith("update"))
                    .as("an open week reopened")
                    .isEmpty();
        }

        closeWeek(alice, weekId).expectStatus().isOk();
        Object closedAt = jdbc.sql("SELECT updated_at FROM weekly_logs WHERE id = :id")
                .param("id", UUID.fromString(weekId))
                .query()
                .singleValue();
        try (SqlLog sql = SqlLog.capture()) {
            String again = bodyText(closeWeek(alice, weekId).expectStatus().isOk());

            assertThat(JsonPath.<Boolean>read(again, "$.isClosed")).isTrue();
            assertThat(sql.statementsStartingWith("update"))
                    .as("a closed week closed again")
                    .isEmpty();
        }
        Object after = jdbc.sql("SELECT updated_at FROM weekly_logs WHERE id = :id")
                .param("id", UUID.fromString(weekId))
                .query()
                .singleValue();
        assertThat(after).isEqualTo(closedAt);
    }

    @Test
    void aClosedWeekStaysEditableAndStillTakesNewEntries() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "First"));
        closeWeek(alice, weekId).expectStatus().isOk();

        putWeek(alice, weekId, json("title", "Edited while closed"))
                .expectStatus()
                .isOk();
        String second = entryOn(alice, W3, "Second");

        String week = weekBody(alice, weekId);
        assertThat(JsonPath.<String>read(week, "$.title")).isEqualTo("Edited while closed");
        assertThat(JsonPath.<Boolean>read(week, "$.isClosed"))
                .as("still closed")
                .isTrue();
        assertThat(JsonPath.<List<String>>read(week, "$.entries[*].id"))
                .hasSize(2)
                .contains(second);
    }

    @Test
    void closingAWeekWritesOnlyItsOwnColumnAndSoDoesTheTitle() {
        User alice = aUser();
        String weekId = weekIdOf(entryOn(alice, W3, "Entry"));

        try (SqlLog sql = SqlLog.capture()) {
            closeWeek(alice, weekId).expectStatus().isOk();
            putWeek(alice, weekId, json("title", "Named")).expectStatus().isOk();

            List<String> updates = sql.statementsStartingWith("update weekly_logs");
            assertThat(updates).hasSize(2);
            assertThat(updates.get(0)).contains("is_closed").doesNotContain("title");
            assertThat(updates.get(1)).contains("title").doesNotContain("is_closed");
        }
    }

    // helpers

    private String titleInDatabase(String weekId) {
        return jdbc.sql("SELECT title FROM weekly_logs WHERE id = :id")
                .param("id", UUID.fromString(weekId))
                .query(String.class)
                .optional()
                .orElse(null);
    }
}

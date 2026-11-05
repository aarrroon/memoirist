package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Memory dates and weeks through the API (D-07, D-08, D-09, D-34): "today" in the user's zone, the Sunday-night and
 * daylight-saving edges of a week, backdating and its limits, {@code dateUnknown}, and what a new date does to an
 * edited entry. The app's clock is {@code MutableClock}, so each test names the exact instant it is run at.
 *
 * <p>Melbourne is UTC+11 in summer ("AEDT", from the first Sunday of October) and UTC+10 in winter ("AEST", from the
 * first Sunday of April). Los Angeles is UTC-7 until the first Sunday of November and UTC-8 after it.
 */
class EntryDatesTest extends EntryApiTest {

    // Today, in the user's zone

    @Test
    void withNoDateTheEntryIsDatedTodayInTheUsersZoneNotUtcsAndNotTheServers() {
        // 01:00 UTC on Saturday 31 October is noon that day in Melbourne but still Friday evening in Los Angeles.
        User melbourne = aUser("Australia/Melbourne");
        User losAngeles = aUser("America/Los_Angeles");

        assertThat(createEntry(melbourne, json())).containsEntry("entryDate", "2026-10-31");
        assertThat(createEntry(losAngeles, json())).containsEntry("entryDate", "2026-10-30");
    }

    @Test
    void anEntryWrittenAt2330OnSundayInMelbourneLandsInThatMelbourneWeek() {
        User alice = aUser();
        clockAt("2026-11-01T12:30:00Z"); // 23:30 on Sunday 1 November in Melbourne (UTC+11)

        Map<String, Object> entry = createEntry(alice, json());

        assertThat(entry).containsEntry("entryDate", "2026-11-01");
        assertThat(weekStartOf(entry.get("id"))).isEqualTo("2026-10-26");
    }

    @Test
    void anEntryWrittenAfterMidnightInMelbourneIsInTheNextWeekWhileUtcIsStillSunday() {
        User alice = aUser();
        clockAt("2026-11-01T13:30:00Z"); // 00:30 on Monday 2 November in Melbourne; still Sunday in UTC

        Map<String, Object> entry = createEntry(alice, json());

        assertThat(entry).containsEntry("entryDate", "2026-11-02");
        assertThat(weekStartOf(entry.get("id")))
                .as("not the week UTC would have chosen")
                .isEqualTo("2026-11-02");
    }

    @Test
    void aUserBehindUtcIsStillInTheEarlierWeekWhileUtcIsAlreadyMonday() {
        User losAngeles = aUser("America/Los_Angeles");
        clockAt("2026-11-02T03:00:00Z"); // Monday in UTC; 19:00 on Sunday 1 November in Los Angeles (UTC-8)

        Map<String, Object> entry = createEntry(losAngeles, json());

        assertThat(entry).containsEntry("entryDate", "2026-11-01");
        assertThat(weekStartOf(entry.get("id"))).isEqualTo("2026-10-26");
    }

    @Test
    void whenDaylightSavingBeginsTheWeekChangesAtTheRealMidnightNotOneHourLater() {
        // Melbourne's clocks went forward at 02:00 on Sunday 4 October 2026 (to UTC+11), so midnight into Monday
        // 5 October is 13:00 UTC. Counting from UTC+10 would put 13:00 UTC at 23:00 on the Sunday.
        User alice = aUser();

        clockAt("2026-10-04T12:59:59Z");
        Map<String, Object> sunday = createEntry(alice, json());
        clockAt("2026-10-04T13:00:00Z");
        Map<String, Object> monday = createEntry(alice, json());

        assertThat(sunday).containsEntry("entryDate", "2026-10-04");
        assertThat(weekStartOf(sunday.get("id"))).isEqualTo("2026-09-28");
        assertThat(monday).containsEntry("entryDate", "2026-10-05");
        assertThat(weekStartOf(monday.get("id"))).isEqualTo("2026-10-05");
    }

    @Test
    void whenDaylightSavingEndsTheWeekChangesAtTheRealMidnightNotOneHourEarlier() {
        // Clocks went back at 03:00 on Sunday 4 April 2027 (to UTC+10), a 25-hour day, so midnight into Monday 5 April
        // is 14:00 UTC. Counting from UTC+11 would put 13:00 UTC at the start of the Monday.
        User alice = aUser();

        clockAt("2027-04-04T13:59:59Z");
        Map<String, Object> sunday = createEntry(alice, json());
        clockAt("2027-04-04T14:00:00Z");
        Map<String, Object> monday = createEntry(alice, json());

        assertThat(sunday).containsEntry("entryDate", "2027-04-04");
        assertThat(weekStartOf(sunday.get("id"))).isEqualTo("2027-03-29");
        assertThat(monday).containsEntry("entryDate", "2027-04-05");
        assertThat(weekStartOf(monday.get("id"))).isEqualTo("2027-04-05");
    }

    @Test
    void aWeekThatContainsTheDaylightSavingChangeHoldsAllSevenDays() {
        User alice = aUser();
        clockAt("2026-10-11T01:00:00Z");

        String monday = createEntryId(alice, json("entryDate", "2026-09-28"));
        String sunday = createEntryId(alice, json("entryDate", "2026-10-04"));

        assertThat(weekStartOf(monday)).isEqualTo("2026-09-28");
        assertThat(weekStartOf(sunday)).isEqualTo("2026-09-28");
        assertThat(countWeeks(alice)).isEqualTo(1);
    }

    // Backdating and its limits (D-09)

    @Test
    void backdatingFilesTheEntryOnTheDayItHappenedAndInThatWeek() {
        User alice = aUser();

        Map<String, Object> entry = createEntry(alice, json("entryDate", "2026-10-20"));

        assertThat(entry).containsEntry("entryDate", "2026-10-20");
        assertThat(weekStartOf(entry.get("id"))).isEqualTo("2026-10-19");
        assertThat(row(entry.get("id")).get("entry_date")).hasToString("2026-10-20");
        assertThat(row(entry.get("id")).get("created_at").toString())
                .as("created_at still says when it was really written")
                .startsWith("20");
    }

    @Test
    void anOldDateIsFineAndSoIsTheEarliestAllowedDay() {
        User alice = aUser();

        assertThat(createEntry(alice, json("entryDate", "1985-07-13"))).containsEntry("entryDate", "1985-07-13");
        Map<String, Object> earliest = createEntry(alice, json("entryDate", "1900-01-01"));
        assertThat(earliest).containsEntry("entryDate", "1900-01-01");
        assertThat(weekStartOf(earliest.get("id"))).isEqualTo("1900-01-01");
    }

    @Test
    void todayIsAllowedAndTomorrowIsNot() {
        User alice = aUser();

        createEntry(alice, json("entryDate", "2026-10-31"));
        String body = bodyText(
                post(alice, json("entryDate", "2026-11-01")).expectStatus().isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsExactly(Map.entry("entryDate", "must not be after today"));
        assertThat(JsonPath.<String>read(body, "$.detail")).isEqualTo("entryDate must not be after today");
        assertThat(countEntries(alice)).isEqualTo(1);
    }

    @Test
    void aDateFarInTheFutureIsRejected() {
        User alice = aUser();

        for (String date : new String[] {"2027-10-31", "2999-12-31", "9999-12-31"}) {
            String body =
                    bodyText(post(alice, json("entryDate", date)).expectStatus().isBadRequest());
            assertThat(fieldErrors(body)).containsOnlyKeys("entryDate");
        }
        assertThat(countEntries(alice)).isZero();
        assertThat(countWeeks(alice)).isZero();
    }

    @Test
    void aDateBefore1900IsRejectedSoATypoCannotMakeAWeekInTheYear202() {
        User alice = aUser();

        for (String date : new String[] {"1899-12-31", "0202-10-26", "0001-01-01", "-0001-01-01"}) {
            String body =
                    bodyText(post(alice, json("entryDate", date)).expectStatus().isBadRequest());

            assertThat(problemType(body)).as(date).isEqualTo("validation-failed");
            assertThat(fieldErrors(body))
                    .as(date)
                    .containsExactly(Map.entry("entryDate", "must not be before 1900-01-01"));
        }
        assertThat(countEntries(alice)).isZero();
        assertThat(countWeeks(alice)).isZero();
    }

    @Test
    void whetherADateIsInTheFutureIsDecidedByTheUsersTodayNotUtcs() {
        // 20:00 UTC on 31 October: already Sunday 1 November in Melbourne, still Saturday 31 October in Los Angeles.
        User melbourne = aUser("Australia/Melbourne");
        User losAngeles = aUser("America/Los_Angeles");
        clockAt("2026-10-31T20:00:00Z");

        createEntry(melbourne, json("entryDate", "2026-11-01"));
        post(losAngeles, json("entryDate", "2026-11-01")).expectStatus().isBadRequest();
        createEntry(losAngeles, json("entryDate", "2026-10-31"));
    }

    // dateUnknown (D-34)

    @Test
    void dateUnknownOnCreateIsSavedAndShownAndDefaultsToFalse() {
        User alice = aUser();

        Map<String, Object> guessed = createEntry(alice, json("entryDate", "2026-10-20", "dateUnknown", true));
        Map<String, Object> normal = createEntry(alice, json("entryDate", "2026-10-20"));

        assertThat(guessed).containsEntry("dateUnknown", true);
        assertThat(row(guessed.get("id"))).containsEntry("date_unknown", true);
        assertThat(getEntry(alice, guessed.get("id"))).containsEntry("dateUnknown", true);
        assertThat(normal).containsEntry("dateUnknown", false);
        assertThat(row(normal.get("id"))).containsEntry("date_unknown", false);
    }

    @Test
    void dateUnknownStaysVisibleWhileTheEntryIsSealed() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Secret", "dateUnknown", true));

        Map<String, Object> sealed =
                bodyMap(seal(alice, id, "2027-01-01T09:00:00Z").expectStatus().isOk());

        assertThat(sealed)
                .containsEntry("isLocked", true)
                .containsEntry("dateUnknown", true)
                .containsEntry("title", null);
        assertThat(getEntry(alice, id)).containsEntry("dateUnknown", true).containsEntry("isLocked", true);
    }

    @Test
    void dateUnknownOnACreateThatIsSealedLaterIsShownInTheSealResponseToo() {
        User alice = aUser();
        String id = createEntryId(alice, json("dateUnknown", true));

        seal(alice, id, "2030-05-05T09:00:00Z")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.dateUnknown")
                .isEqualTo(true);
    }

    // Changing the date of an existing entry

    @Test
    void changingTheDateMovesTheEntryToTheMatchingWeekAndClearsDateUnknown() {
        User alice = aUser();
        Map<String, Object> entry = createEntry(alice, json("entryDate", "2026-10-27", "dateUnknown", true));
        String id = entry.get("id").toString();
        assertThat(weekStartOf(id)).isEqualTo("2026-10-26");

        Map<String, Object> moved = bodyMap(put(alice, id, json("version", 0, "entryDate", "2026-10-21"))
                .expectStatus()
                .isOk());

        assertThat(moved)
                .containsEntry("entryDate", "2026-10-21")
                .containsEntry("dateUnknown", false)
                .containsEntry("version", 1);
        assertThat(moved.get("weeklyLogId")).isNotEqualTo(entry.get("weeklyLogId"));
        assertThat(weekStartOf(id)).isEqualTo("2026-10-19");
        assertThat(row(id)).containsEntry("date_unknown", false);
        assertThat(countWeeks(alice))
                .as("the week the entry left is not deleted")
                .isEqualTo(2);

        Map<String, Object> back = bodyMap(put(alice, id, json("version", 1, "entryDate", "2026-10-28"))
                .expectStatus()
                .isOk());
        assertThat(back.get("weeklyLogId"))
                .as("the first week is found again, not made again")
                .isEqualTo(entry.get("weeklyLogId"));
        assertThat(countWeeks(alice)).isEqualTo(2);
    }

    @Test
    void changingTheDateWithinTheSameWeekKeepsTheWeekButStillClearsDateUnknown() {
        User alice = aUser();
        Map<String, Object> entry = createEntry(alice, json("entryDate", "2026-10-27", "dateUnknown", true));

        Map<String, Object> moved = bodyMap(put(alice, entry.get("id"), json("version", 0, "entryDate", "2026-10-29"))
                .expectStatus()
                .isOk());

        assertThat(moved).containsEntry("entryDate", "2026-10-29").containsEntry("dateUnknown", false);
        assertThat(moved.get("weeklyLogId")).isEqualTo(entry.get("weeklyLogId"));
        assertThat(countWeeks(alice)).isEqualTo(1);
    }

    @Test
    void anUnchangedDateKeepsDateUnknownAndTheWeek() {
        User alice = aUser();
        Map<String, Object> entry = createEntry(alice, json("entryDate", "2026-10-27", "dateUnknown", true));

        Map<String, Object> edited = bodyMap(
                put(alice, entry.get("id"), json("version", 0, "entryDate", "2026-10-27", "title", "Now with a title"))
                        .expectStatus()
                        .isOk());

        assertThat(edited).containsEntry("dateUnknown", true).containsEntry("title", "Now with a title");
        assertThat(edited.get("weeklyLogId")).isEqualTo(entry.get("weeklyLogId"));
    }

    @Test
    void aClientCannotSetDateUnknownOnAnUpdate() {
        User alice = aUser();
        Map<String, Object> entry = createEntry(alice, json("entryDate", "2026-10-27"));

        Map<String, Object> edited =
                bodyMap(put(alice, entry.get("id"), json("version", 0, "entryDate", "2026-10-27", "dateUnknown", true))
                        .expectStatus()
                        .isOk());

        assertThat(edited).containsEntry("dateUnknown", false);
        assertThat(row(entry.get("id"))).containsEntry("date_unknown", false);
    }

    @Test
    void anUpdateIsHeldToTheSameLimitsAsACreateAndChangesNothingWhenRefused() {
        User alice = aUser();
        Map<String, Object> entry = createEntry(alice, json("entryDate", "2026-10-27", "title", "Keep me"));
        String id = entry.get("id").toString();

        for (String date : new String[] {"2026-11-01", "2999-01-01", "1899-12-31", "0202-01-01"}) {
            String body = bodyText(put(alice, id, json("version", 0, "entryDate", date, "title", "Changed"))
                    .expectStatus()
                    .isBadRequest());
            assertThat(fieldErrors(body)).as(date).containsOnlyKeys("entryDate");
        }

        assertThat(row(id)).containsEntry("title", "Keep me").containsEntry("version", 0L);
        assertThat(row(id).get("entry_date")).hasToString("2026-10-27");
        assertThat(countWeeks(alice)).as("a refused update makes no week").isEqualTo(1);
    }

    @Test
    void aDateThatHasSinceBecomeTomorrowCanStillBeKeptWhileOtherFieldsAreEdited() {
        // The user moves from Melbourne to Los Angeles. Their entry dated Saturday 31 October is now "in the future"
        // (it is still Friday there), but they did not change that date, so editing the title must still work.
        User alice = aUser("Australia/Melbourne");
        Map<String, Object> entry = createEntry(alice, json());
        assertThat(entry).containsEntry("entryDate", "2026-10-31");
        jdbc.sql("UPDATE users SET timezone = 'America/Los_Angeles' WHERE id = :id")
                .param("id", alice.getId())
                .update();

        put(alice, entry.get("id"), json("version", 0, "entryDate", "2026-10-31", "title", "Edited from LA"))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.title")
                .isEqualTo("Edited from LA");
        put(alice, entry.get("id"), json("version", 1, "entryDate", "2026-11-01"))
                .expectStatus()
                .isBadRequest();
    }
}

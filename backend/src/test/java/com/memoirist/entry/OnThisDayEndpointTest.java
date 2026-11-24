package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.Fixtures;
import com.memoirist.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/v1/entries/on-this-day} (task 3.12, D-32, API_CONTRACT Entries, <em>On this day</em>), one test for
 * each thing the contract promises. The clock starts on Saturday 2026-10-31, so unless a test moves it the strip looks
 * for 31 October in earlier years.
 */
class OnThisDayEndpointTest extends OnThisDayApiTest {

    // The days that matter

    @Test
    void entriesFromTheSameDayInEarlierYearsAreListedNewestYearFirst() {
        User alice = aUser();
        String twoYearsAgo = entryOn(alice, "2024-10-31", "Two years ago");
        String lastYear = entryOn(alice, "2025-10-31", "Last year");
        String longAgo = entryOn(alice, "1999-10-31", "Long ago");

        assertThat(onThisDayIds(alice)).containsExactly(lastYear, twoYearsAgo, longAgo);
        assertThat(onThisDayDates(alice)).containsExactly("2025-10-31", "2024-10-31", "1999-10-31");
    }

    @Test
    void thisYearsEntriesAreNotListed() {
        User alice = aUser();
        entryOn(alice, "2026-10-31", "Today");
        entryOn(alice, "2026-10-30", "Yesterday");
        entryOn(alice, "2026-01-31", "Same day number, earlier this year");

        assertThat(onThisDayBody(alice)).isEqualTo("[]");
    }

    @Test
    void onlyTheSameMonthAndDayMatch() {
        User alice = aUser();
        String match = entryOn(alice, "2025-10-31", "The day");
        entryOn(alice, "2025-10-30", "The day before");
        entryOn(alice, "2025-11-01", "The day after");
        entryOn(alice, "2025-08-31", "Same day of another month");
        entryOn(alice, "2025-12-31", "Same day of another month");
        entryOn(alice, "2025-10-01", "Same month, another day");

        assertThat(onThisDayIds(alice)).containsExactly(match);
    }

    @Test
    void anEmptyJournalAndAJournalWithNothingFromThisDayGiveAnEmptyArray() {
        User nobody = aUser();
        User alice = aUser();
        entryOn(alice, "2025-10-30", "Not this day");

        assertThat(onThisDayBody(nobody)).isEqualTo("[]");
        assertThat(onThisDayBody(alice)).isEqualTo("[]");
    }

    @Test
    void theAnswerIsAPlainJsonArrayOfWholeSummaryRows() {
        User alice = aUser();
        String id = createEntryId(
                alice,
                json(
                        "entryDate",
                        "2025-10-31",
                        "title",
                        "By the lake",
                        "contentJson",
                        json(
                                "type",
                                "doc",
                                "content",
                                List.of(heading(2, "A heading"), paragraph("Walked to the lake.")))));
        aPhoto(id, "users/test/otd/" + id + "/1.jpg", 0);
        aVoiceNote(id, "users/test/otd/" + id + "/voice.webm", 1);
        aTag(alice, id, "Holidays");

        onThisDay(alice).expectStatus().isOk().expectHeader().contentTypeCompatibleWith("application/json");
        List<Map<String, Object>> rows = onThisDayRows(alice);

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.keySet())
                .as("the fields of EntrySummary, named as in the contract")
                .containsExactlyInAnyOrder(
                        "id",
                        "weeklyLogId",
                        "entryDate",
                        "title",
                        "previewLine",
                        "photoCount",
                        "voiceNoteCount",
                        "firstMediaKind",
                        "tags",
                        "createdAt",
                        "unlockDate",
                        "isLocked",
                        "dateUnknown",
                        "deletedAt");
        assertThat(row.get("id")).isEqualTo(id);
        assertThat(row.get("title")).isEqualTo("By the lake");
        assertThat(row.get("previewLine")).isEqualTo("Walked to the lake.");
        assertThat(row.get("photoCount")).isEqualTo(1);
        assertThat(row.get("voiceNoteCount")).isEqualTo(1);
        assertThat(row.get("firstMediaKind")).isEqualTo("PHOTO");
        assertThat(row.get("isLocked")).isEqualTo(false);
        assertThat(row.get("unlockDate")).isNull();
        assertThat(row.get("deletedAt")).isNull();
        assertThat(JsonPath.<List<String>>read(onThisDayBody(alice), "$[0].tags[*].name"))
                .containsExactly("Holidays");
    }

    @Test
    void anEntryWhoseDateIsAGuessIsListedAndSaysSo() {
        User alice = aUser();
        String id = createEntryId(alice, json("entryDate", "2025-10-31", "dateUnknown", true));

        List<Map<String, Object>> rows = onThisDayRows(alice);

        assertThat(rows).extracting(row -> row.get("id")).containsExactly(id);
        assertThat(rows.getFirst().get("dateUnknown")).isEqualTo(true);
    }

    // 29 February (D-32)

    @Test
    void inAYearThatIsNotALeapYear29FebruaryEntriesAreListedOn28February() {
        User alice = aUser();
        String wasLeapDay = entryOn(alice, "2024-02-29", "A leap day");
        String sameYearThe28th = entryOn(alice, "2024-02-28", "The day before it");
        String lastYear = entryOn(alice, "2026-02-28", "Last year");
        String olderLeapDay = entryOn(alice, "2020-02-29", "Another leap day");
        entryOn(alice, "2024-02-27", "Not the day");
        entryOn(alice, "2024-03-01", "Not the day either");
        clockAt("2027-02-28T01:00:00Z"); // Sunday 28 February 2027, 12:00 in Melbourne; 2027 has no 29th

        entryOn(alice, "2027-02-28", "Today, so not an earlier year");

        assertThat(onThisDayIds(alice))
                .as("newest year first; in 2024 the 29th is newer than the 28th")
                .containsExactly(lastYear, wasLeapDay, sameYearThe28th, olderLeapDay);
    }

    @Test
    void inALeapYear28FebruaryDoesNotPullInThe29th() {
        User alice = aUser();
        clockAt("2028-02-28T01:00:00Z"); // Monday 28 February 2028; 2028 has a 29th
        String the28th = entryOn(alice, "2027-02-28", "The 28th last year");
        entryOn(alice, "2024-02-29", "A leap day: it will show on the 29th");
        String older28th = entryOn(alice, "2024-02-28", "The 28th");

        assertThat(onThisDayIds(alice)).containsExactly(the28th, older28th);
    }

    @Test
    void onTheLeapDayItselfOnlyEarlierLeapDaysAreListed() {
        User alice = aUser();
        clockAt("2028-02-29T01:00:00Z"); // Tuesday 29 February 2028
        String leapDay = entryOn(alice, "2024-02-29", "A leap day");
        String olderLeapDay = entryOn(alice, "2020-02-29", "An older leap day");
        entryOn(alice, "2027-02-28", "The 28th is not the 29th");
        entryOn(alice, "2024-02-28", "Nor is this");

        assertThat(onThisDayIds(alice)).containsExactly(leapDay, olderLeapDay);
    }

    @Test
    void a29FebruaryEntryDoesNotFollowOnTo1March() {
        User alice = aUser();
        entryOn(alice, "2024-02-29", "A leap day");
        String lastYear = entryOn(alice, "2026-03-01", "1 March last year");
        String earlier = entryOn(alice, "2024-03-01", "1 March in a leap year");
        clockAt("2027-03-01T01:00:00Z");

        assertThat(onThisDayIds(alice)).containsExactly(lastYear, earlier);
    }

    // The user's time zone (D-07)

    // TODO(human) PRACTICE: Write the test that proves "the same day" follows the user's time zone and not the
    //   server's. Make two users in zones whose calendar days differ at one instant, give each an entry on both of the
    //   two days a year earlier, move the clock to that instant, and check that each sees the day of their own zone.
    // Hint: D-07, and the test JVM runs in UTC (build.gradle.kts). 14:00 UTC on 2026-10-30 is already 01:00 on the 31st
    //   in Melbourne (UTC+11 in October) and still 07:00 on the 30th in Los Angeles (UTC-7). If the code used the
    //   server's zone, both users would be given the 30th, so one assertion would fail. Create the entries before
    //   moving the clock: the memory dates are in the past either way.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    @Test
    void theSameDayFollowsTheUsersTimeZoneNotTheServers() {
        User inMelbourne = aUser("Australia/Melbourne");
        User inLosAngeles = aUser("America/Los_Angeles");
        for (User user : List.of(inMelbourne, inLosAngeles)) {
            entryOn(user, "2025-10-30", "The 30th");
            entryOn(user, "2025-10-31", "The 31st");
        }

        clockAt("2026-10-30T14:00:00Z");

        assertThat(onThisDayDates(inMelbourne)).containsExactly("2025-10-31");
        assertThat(onThisDayDates(inLosAngeles)).containsExactly("2025-10-30");
    }
    // └── END ANSWER ▲ ──

    @Test
    void theDayTurnsOverAtMidnightInTheUsersZone() {
        User alice = aUser("Australia/Melbourne");
        String the31st = entryOn(alice, "2025-10-31", "The 31st");
        String the1st = entryOn(alice, "2025-11-01", "The 1st");

        clockAt("2026-10-31T12:59:59Z"); // 23:59:59 on 31 October in Melbourne
        assertThat(onThisDayIds(alice)).containsExactly(the31st);

        clockAt("2026-10-31T13:00:00Z"); // midnight: 1 November
        assertThat(onThisDayIds(alice)).containsExactly(the1st);
    }

    @Test
    void movingToAnotherZoneChangesTheDayFromTheNextRequest() {
        User alice = aUser("Australia/Melbourne");
        String the30th = entryOn(alice, "2025-10-30", "The 30th");
        String the31st = entryOn(alice, "2025-10-31", "The 31st");
        clockAt("2026-10-30T14:00:00Z");

        assertThat(onThisDayIds(alice)).containsExactly(the31st);
        moveTo(alice, "America/Los_Angeles");
        assertThat(onThisDayIds(alice)).containsExactly(the30th);
    }

    @Test
    void theNewYearArrivesInEachZoneAtItsOwnMidnight() {
        // New Year's Eve: 13:00 UTC on 31 December is already 1 January in Melbourne but 31 December elsewhere.
        User inMelbourne = aUser("Australia/Melbourne");
        User inLondon = aUser("Europe/London");
        for (User user : List.of(inMelbourne, inLondon)) {
            entryOn(user, "2025-12-31", "Last New Year's Eve");
            entryOn(user, "2025-01-01", "Last New Year's Day");
            entryOn(user, "2026-01-01", "This New Year's Day");
        }
        clockAt("2026-12-31T13:00:00Z");

        // Melbourne has crossed into 2027, so 2026 is an earlier year there: its 1 January is listed.
        assertThat(onThisDayDates(inMelbourne)).containsExactly("2026-01-01", "2025-01-01");
        // London is still on 31 December 2026.
        assertThat(onThisDayDates(inLondon)).containsExactly("2025-12-31");
    }

    // The seal (D-10)

    @Test
    void aSealedLootDropIsNotListedNotEvenAsALockedRow() {
        User alice = aUser();
        String open = entryOn(alice, "2025-10-31", "An ordinary day");
        String sealed = entryOn(alice, "2024-10-31", "A secret");
        sealUntil(alice, sealed, SEALED_UNTIL);

        String body = onThisDayBody(alice);

        assertThat(JsonPath.<List<String>>read(body, "$[*].id")).containsExactly(open);
        assertThat(body).doesNotContain(sealed).doesNotContain("A secret").doesNotContain("\"isLocked\":true");
    }

    @Test
    void onlySealedEntriesFromThisDayGiveAnEmptyArrayThatHintsAtNothing() {
        User alice = aUser();
        sealUntil(alice, entryOn(alice, "2025-10-31", "A secret"), SEALED_UNTIL);

        assertThat(onThisDayBody(alice)).isEqualTo("[]");
    }

    @Test
    void aLootDropIsListedWholeFromTheInstantItUnlocks() {
        User alice = aUser();
        // Memory date 3 November 2025, sealed until 22:00 UTC on 2 November 2026 (09:00 on the 3rd in Melbourne).
        String drop = entryOn(alice, "2025-11-03", "Letter to myself");
        sealUntil(alice, drop, OPENS_1);

        clockAt("2026-11-02T21:59:59Z"); // 08:59:59 on 3 November: one second to go
        assertThat(onThisDayBody(alice)).isEqualTo("[]");

        clockAt(OPENS_1);
        List<Map<String, Object>> rows = onThisDayRows(alice);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("id")).isEqualTo(drop);
        assertThat(rows.getFirst().get("isLocked")).isEqualTo(false);
        assertThat(rows.getFirst().get("title")).isEqualTo("Letter to myself");
    }

    @Test
    void sealingAnEntryAgainTakesItOutOfTheStripUntilItOpensAgain() {
        User alice = aUser();
        String id = entryOn(alice, "2025-10-31", "A letter");
        assertThat(onThisDayIds(alice)).containsExactly(id);

        sealUntil(alice, id, SEALED_UNTIL);
        assertThat(onThisDayBody(alice)).isEqualTo("[]");

        // The API never lets a test choose an unlock date in the past, so the database does: it has opened.
        unlockedAt(id, "2026-10-31T00:00:00Z");
        assertThat(onThisDayIds(alice)).containsExactly(id);
    }

    // Recently deleted (D-30)

    @Test
    void anEntryInRecentlyDeletedIsNotListedAndIsListedAgainWhenRestored() {
        User alice = aUser();
        String kept = entryOn(alice, "2025-10-31", "Kept");
        String deleted = entryOn(alice, "2024-10-31", "Deleted");
        delete(alice, deleted).expectStatus().isNoContent();

        assertThat(onThisDayIds(alice)).containsExactly(kept);
        assertThat(onThisDayBody(alice)).doesNotContain("Deleted");

        restore(alice, deleted);
        assertThat(onThisDayIds(alice)).containsExactly(kept, deleted);
    }

    @Test
    void aDeletedLootDropIsNotListedEitherEvenOnceItHasOpened() {
        User alice = aUser();
        String drop = entryOn(alice, "2025-11-03", "Letter to myself");
        sealUntil(alice, drop, OPENS_1);
        delete(alice, drop).expectStatus().isNoContent();

        clockAt(OPENS_1);
        assertThat(onThisDayBody(alice)).isEqualTo("[]");

        restore(alice, drop);
        assertThat(onThisDayIds(alice)).containsExactly(drop);
    }

    // Whose entries (non-negotiable 3)

    @Test
    void anotherUsersEntriesAreNeverListed() {
        User alice = aUser();
        User bob = aUser();
        String aliceEntry = entryOn(alice, "2025-10-31", "Alice's");
        String bobEntry = entryOn(bob, "2025-10-31", "Bob's");
        String bobOlder = entryOn(bob, "2023-10-31", "Bob's older");

        assertThat(onThisDayIds(alice)).containsExactly(aliceEntry);
        assertThat(onThisDayIds(bob)).containsExactly(bobEntry, bobOlder);
        assertThat(onThisDayBody(alice)).doesNotContain("Bob");
    }

    @Test
    void aHeaderNamingNobodyGetsAnEmptyStripLikeTheOtherLists() {
        User ghost = Fixtures.user(); // never saved, so the header names nobody

        assertThat(onThisDayBody(ghost)).isEqualTo("[]");
    }

    @Test
    void aHeaderThatIsNotAUuidIs400() {
        client.get()
                .uri(ON_THIS_DAY)
                .header(HEADER, "not-a-uuid")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    // How many, and in what order

    @Test
    void atMostTwentyAreListedNewestYearsFirst() {
        User alice = aUser();
        List<String> ids = new ArrayList<>();
        for (int year = 2025; year >= 2004; year--) { // 22 years
            ids.add(entryOn(alice, year + "-10-31", "Year " + year));
        }

        List<String> shown = onThisDayIds(alice);

        assertThat(shown).hasSize(20).containsExactlyElementsOf(ids.subList(0, 20));
        assertThat(onThisDayDates(alice).getFirst()).isEqualTo("2025-10-31");
        assertThat(onThisDayDates(alice).getLast()).isEqualTo("2006-10-31");
    }

    @Test
    void entriesFromOneDayComeNewestWrittenFirstThenByIdSoTheOrderNeverChanges() {
        User alice = aUser();
        UUID low = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID high = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        UUID oldest = UUID.fromString("00000000-0000-0000-0000-0000000000f0");
        createEntry(alice, json("id", low.toString(), "entryDate", "2025-10-31", "title", "Low"));
        createEntry(alice, json("id", high.toString(), "entryDate", "2025-10-31", "title", "High"));
        createEntry(alice, json("id", oldest.toString(), "entryDate", "2025-10-31", "title", "Written first"));
        writtenAt(low, "2025-10-31T09:00:00Z");
        writtenAt(high, "2025-10-31T09:00:00Z"); // the same instant as Low, as in a photo dump
        writtenAt(oldest, "2025-10-31T08:00:00Z");

        assertThat(onThisDayIds(alice)).containsExactly(high.toString(), low.toString(), oldest.toString());
        assertThat(onThisDayIds(alice))
                .as("the same again")
                .containsExactly(high.toString(), low.toString(), oldest.toString());
    }
}

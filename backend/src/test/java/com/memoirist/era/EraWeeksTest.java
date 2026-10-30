package com.memoirist.era;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.SqlLog;
import com.memoirist.entry.Entry;
import com.memoirist.entry.EntryRepository;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeekEras;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * What an Era holds, from the outside (D-22): the weeks its dates overlap, and the entries inside its dates. Every case
 * of the "Done when" list of task 3.3 that is about weeks, counts or Recently deleted is here, through HTTP and the
 * real database.
 *
 * <p>The weeks used are Monday-to-Sunday spans in October and November 2026:
 * <pre>
 *   W1  Oct 05 - Oct 11      W3  Oct 19 - Oct 25      W5  Nov 02 - Nov 08
 *   W2  Oct 12 - Oct 18      W4  Oct 26 - Nov 01
 * </pre>
 *
 * <p>Like {@link EraEndpointTest}, each test makes its own users and the cleanup deletes them, with their weeks,
 * entries and Eras.
 */
@IntegrationTest
class EraWeeksTest {

    private static final String ERAS = "/api/v1/eras";
    private static final String HEADER = "X-Dev-User-Id";

    private static final LocalDate W1 = LocalDate.of(2026, 10, 5);
    private static final LocalDate W2 = LocalDate.of(2026, 10, 12);
    private static final LocalDate W3 = LocalDate.of(2026, 10, 19);
    private static final LocalDate W4 = LocalDate.of(2026, 10, 26);
    private static final LocalDate W5 = LocalDate.of(2026, 11, 2);

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Autowired
    WeeklyLogRepository weeks;

    @Autowired
    EntryRepository entries;

    @Autowired
    EraRepository eraRepository;

    @Autowired
    JdbcClient jdbc;

    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void deleteTheUsersThisTestMade() {
        created.forEach(id ->
                jdbc.sql("DELETE FROM users WHERE id = :id").param("id", id).update());
    }

    // A new Era, and changing its dates

    @Test
    void aNewEraHoldsTheExistingWeeksItsDatesOverlapNewestFirst() {
        User alice = aUser();
        WeeklyLog w1 = weekWithAnEntry(alice, W1);
        WeeklyLog w2 = weekWithAnEntry(alice, W2);
        WeeklyLog w3 = weekWithAnEntry(alice, W3);
        WeeklyLog w4 = weekWithAnEntry(alice, W4);
        WeeklyLog w5 = weekWithAnEntry(alice, W5);

        // Oct 14 is a Wednesday of W2 and Oct 27 a Tuesday of W4: the Era holds W2, W3 and W4, not W1 or W5.
        String eraId = createEra(alice, "Trip season", "2026-10-14", "2026-10-27");

        assertThat(weekIds(alice, eraId))
                .containsExactly(
                        w4.getId().toString(), w3.getId().toString(), w2.getId().toString());
        assertThat(weekIds(alice, eraId))
                .doesNotContain(w1.getId().toString(), w5.getId().toString());
    }

    @Test
    void eachWeekRowHasExactlyTheContractFields() {
        User alice = aUser();
        WeeklyLog week = weekWithAnEntry(alice, W4);
        week.setTitle("Grampians Trip Week");
        week.setClosed(true);
        weeks.save(week);
        String eraId = createEra(alice, "Trip", "2026-10-26", "2026-11-01");

        get(alice, ERAS + "/" + eraId)
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> {
                    Map<String, Object> row = JsonPath.read(body, "$.weeklyLogs[0]");
                    assertThat(row.keySet())
                            .containsExactlyInAnyOrder("id", "title", "startDate", "endDate", "isClosed", "entryCount");
                    assertThat(row)
                            .containsEntry("id", week.getId().toString())
                            .containsEntry("title", "Grampians Trip Week")
                            .containsEntry("startDate", "2026-10-26")
                            .containsEntry("endDate", "2026-11-01")
                            .containsEntry("isClosed", true)
                            .containsEntry("entryCount", 1);
                });
    }

    @Test
    void changingTheDatesChangesWhichWeeksItHoldsAtOnceAndWritesNothingElse() {
        User alice = aUser();
        WeeklyLog w1 = weekWithAnEntry(alice, W1);
        WeeklyLog w2 = weekWithAnEntry(alice, W2);
        WeeklyLog w3 = weekWithAnEntry(alice, W3);
        WeeklyLog w4 = weekWithAnEntry(alice, W4);
        String eraId = createEra(alice, "Moves", "2026-10-05", "2026-10-18");
        assertThat(weekIds(alice, eraId)).containsExactly(id(w2), id(w1));
        long weekRows = count("weekly_logs", alice);
        long entryRows = count("entries", alice);

        // Later dates: the old weeks go and the new ones come, with no other call.
        put(alice, eraId, "Moves", "2026-10-19", "2026-11-01").expectStatus().isOk();
        assertThat(weekIds(alice, eraId)).containsExactly(id(w4), id(w3));

        // An Era with no end holds every week from its start onwards.
        put(alice, eraId, "Moves", "2026-10-12", null).expectStatus().isOk();
        assertThat(weekIds(alice, eraId)).containsExactly(id(w4), id(w3), id(w2));

        // A shorter Era: just the week of one day.
        put(alice, eraId, "Moves", "2026-10-01", "2026-10-05").expectStatus().isOk();
        assertThat(weekIds(alice, eraId)).containsExactly(id(w1));

        assertThat(count("weekly_logs", alice)).isEqualTo(weekRows);
        assertThat(count("entries", alice)).isEqualTo(entryRows);
    }

    @Test
    void anEraThatEndsOnAMondayHoldsThatWeekAndOneThatEndedTheSundayBeforeDoesNot() {
        User alice = aUser();
        WeeklyLog w3 = weekWithAnEntry(alice, W3);
        WeeklyLog w4 = weekWithAnEntry(alice, W4);

        String endsOnMonday = createEra(alice, "Ends Monday", "2026-10-01", "2026-10-26");
        String endsOnSunday = createEra(alice, "Ends Sunday", "2026-10-01", "2026-10-25");
        String startsOnSunday = createEra(alice, "Starts Sunday", "2026-10-25", "2026-11-30");

        assertThat(weekIds(alice, endsOnMonday)).containsExactly(id(w4), id(w3));
        assertThat(weekIds(alice, endsOnSunday)).containsExactly(id(w3));
        assertThat(weekIds(alice, startsOnSunday)).containsExactly(id(w4), id(w3));
    }

    @Test
    void anEraWithAnEndDateOfNullIsOngoingAndHoldsLaterWeeksToo() {
        User alice = aUser();
        weekWithAnEntry(alice, W1);
        WeeklyLog w3 = weekWithAnEntry(alice, W3);
        WeeklyLog w5 = weekWithAnEntry(alice, W5);

        String eraId = createEra(alice, "Ongoing", "2026-10-20", null);

        assertThat(weekIds(alice, eraId)).containsExactly(id(w5), id(w3));
    }

    @Test
    void aWeekWithNoEntriesInsideTheEraStillAppearsWithZero() {
        User alice = aUser();
        WeeklyLog week = weeks.save(Fixtures.week(alice.getId(), W4));
        entries.save(new Entry(UUID.randomUUID(), alice.getId(), week.getId(), W4)); // Monday: outside the Era
        String eraId = createEra(alice, "Weekend", "2026-10-31", "2026-11-01");

        assertThat(counts(alice, eraId)).containsExactly(Map.entry(id(week), 0));
    }

    // GET /eras/{id}: which entries are counted

    @Test
    void anEraFromSaturdayToSundayCountsOnlyItsOwnTwoDaysNotTheRestOfTheWeek() {
        User alice = aUser();
        WeeklyLog w4 = weeks.save(Fixtures.week(alice.getId(), W4));
        // Monday to Sunday of the week of Oct 26, one entry a day, and one more on Friday.
        for (int day = 0; day < 7; day++) {
            entryOn(alice, w4, W4.plusDays(day));
        }
        entryOn(alice, w4, LocalDate.of(2026, 10, 30));
        // The Friday before the Era, in the previous week, must not count either.
        entryOn(alice, weeks.save(Fixtures.week(alice.getId(), W3)), LocalDate.of(2026, 10, 23));

        String weekend = createEra(alice, "Weekend away", "2026-10-31", "2026-11-01");

        assertThat(counts(alice, weekend)).containsExactly(Map.entry(id(w4), 2));
        // The whole-week Era counts the whole week: all seven days and the extra Friday.
        String wholeWeek = createEra(alice, "Whole week", "2026-10-26", "2026-11-01");
        assertThat(counts(alice, wholeWeek)).containsExactly(Map.entry(id(w4), 8));
    }

    @Test
    void bothEndsOfTheEraAreInsideIt() {
        User alice = aUser();
        WeeklyLog w4 = weeks.save(Fixtures.week(alice.getId(), W4));
        entryOn(alice, w4, LocalDate.of(2026, 10, 27)); // the day before
        entryOn(alice, w4, LocalDate.of(2026, 10, 28)); // first day
        entryOn(alice, w4, LocalDate.of(2026, 10, 30)); // last day
        entryOn(alice, w4, LocalDate.of(2026, 10, 31)); // the day after

        String eraId = createEra(alice, "Midweek", "2026-10-28", "2026-10-30");

        assertThat(counts(alice, eraId)).containsExactly(Map.entry(id(w4), 2));
    }

    @Test
    void anOngoingEraCountsEveryEntryFromItsStartOnwards() {
        User alice = aUser();
        WeeklyLog w3 = weeks.save(Fixtures.week(alice.getId(), W3));
        WeeklyLog w5 = weeks.save(Fixtures.week(alice.getId(), W5));
        entryOn(alice, w3, LocalDate.of(2026, 10, 20)); // before the start
        entryOn(alice, w3, LocalDate.of(2026, 10, 22));
        entryOn(alice, w5, LocalDate.of(2026, 11, 3));

        String eraId = createEra(alice, "Ongoing", "2026-10-21", null);

        assertThat(counts(alice, eraId)).containsExactly(Map.entry(id(w5), 1), Map.entry(id(w3), 1));
    }

    @Test
    void sealedEntriesAreNotCountedButAnOpenedLootDropIs() {
        User alice = aUser();
        WeeklyLog w4 = weeks.save(Fixtures.week(alice.getId(), W4));
        Instant now = Instant.now();
        entryOn(alice, w4, LocalDate.of(2026, 10, 27)); // an ordinary entry
        Entry sealed = entryOn(alice, w4, LocalDate.of(2026, 10, 28));
        sealed.setUnlockDate(now.plus(365, ChronoUnit.DAYS));
        entries.save(sealed);
        Entry opened = entryOn(alice, w4, LocalDate.of(2026, 10, 29));
        opened.setUnlockDate(now.minus(1, ChronoUnit.DAYS));
        entries.save(opened);
        String eraId = createEra(alice, "Week", "2026-10-26", "2026-11-01");

        get(alice, ERAS + "/" + eraId)
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> {
                    // Two count: the ordinary entry and the Loot Drop that has opened. The sealed one does not.
                    assertThat(JsonPath.<Integer>read(body, "$.weeklyLogs[0].entryCount"))
                            .isEqualTo(2);
                    // And nothing of the sealed entry is in the response, not even its id.
                    assertThat(body).doesNotContain(sealed.getId().toString());
                });
    }

    @Test
    void anEraWhoseOnlyEntriesAreSealedCountsZeroSoItCannotTellHowManyAreSealed() {
        User alice = aUser();
        WeeklyLog w4 = weeks.save(Fixtures.week(alice.getId(), W4));
        for (int day = 0; day < 3; day++) {
            Entry sealed = entryOn(alice, w4, W4.plusDays(day));
            sealed.setUnlockDate(Instant.now().plus(30, ChronoUnit.DAYS));
            entries.save(sealed);
        }
        String eraId = createEra(alice, "Week", "2026-10-26", "2026-11-01");

        assertThat(counts(alice, eraId)).containsExactly(Map.entry(id(w4), 0));
    }

    @Test
    void entriesInRecentlyDeletedAreNotCountedAndComeBackWhenRestored() {
        User alice = aUser();
        WeeklyLog w4 = weeks.save(Fixtures.week(alice.getId(), W4));
        entryOn(alice, w4, LocalDate.of(2026, 10, 27));
        Entry deleted = entryOn(alice, w4, LocalDate.of(2026, 10, 28));
        String eraId = createEra(alice, "Week", "2026-10-26", "2026-11-01");
        assertThat(counts(alice, eraId)).containsExactly(Map.entry(id(w4), 2));

        jdbc.sql("UPDATE entries SET deleted_at = now() WHERE id = :id")
                .param("id", deleted.getId())
                .update();
        assertThat(counts(alice, eraId)).containsExactly(Map.entry(id(w4), 1));

        // Restoring clears deleted_at (task 3.11 adds the endpoint): the entry counts again.
        jdbc.sql("UPDATE entries SET deleted_at = NULL WHERE id = :id")
                .param("id", deleted.getId())
                .update();
        assertThat(counts(alice, eraId)).containsExactly(Map.entry(id(w4), 2));
    }

    @Test
    void aSealedEntryThatIsAlsoDeletedIsNotCountedEither() {
        User alice = aUser();
        WeeklyLog w4 = weeks.save(Fixtures.week(alice.getId(), W4));
        Entry both = entryOn(alice, w4, LocalDate.of(2026, 10, 28));
        both.setUnlockDate(Instant.now().plus(10, ChronoUnit.DAYS));
        entries.save(both);
        jdbc.sql("UPDATE entries SET deleted_at = now() WHERE id = :id")
                .param("id", both.getId())
                .update();
        String eraId = createEra(alice, "Week", "2026-10-26", "2026-11-01");

        assertThat(counts(alice, eraId)).containsExactly(Map.entry(id(w4), 0));
    }

    @Test
    void anotherUsersWeeksAndEntriesAreNeverHeldOrCounted() {
        User alice = aUser();
        User bob = aUser();
        WeeklyLog aliceWeek = weeks.save(Fixtures.week(alice.getId(), W4));
        entryOn(alice, aliceWeek, LocalDate.of(2026, 10, 28));
        // Bob has a week starting on the very same Monday, with three entries in the same days.
        WeeklyLog bobWeek = weeks.save(Fixtures.week(bob.getId(), W4));
        for (int day = 1; day <= 3; day++) {
            entryOn(bob, bobWeek, W4.plusDays(day));
        }
        String eraId = createEra(alice, "Alice's week", "2026-10-26", "2026-11-01");

        assertThat(counts(alice, eraId)).containsExactly(Map.entry(id(aliceWeek), 1));
        assertThat(weekIds(alice, eraId)).doesNotContain(id(bobWeek));
    }

    // Deleting an Era

    @Test
    void deletingAnEraLeavesItsWeeksTheirEntriesAndItsOtherErasUntouched() {
        User alice = aUser();
        WeeklyLog w3 = weekWithAnEntry(alice, W3);
        WeeklyLog w4 = weekWithAnEntry(alice, W4);
        String doomed = createEra(alice, "Doomed", "2026-10-19", "2026-11-01");
        String overlapping = createEra(alice, "Overlapping", "2026-10-25", "2026-10-26");
        String elsewhere = createEra(alice, "Elsewhere", "2020-01-01", "2020-12-31");
        long weekRows = count("weekly_logs", alice);
        long entryRows = count("entries", alice);
        String overlappingBefore = detail(alice, overlapping);
        String elsewhereBefore = detail(alice, elsewhere);
        assertThat(weekIds(alice, overlapping)).containsExactly(id(w4), id(w3));

        delete(alice, doomed).expectStatus().isNoContent();

        assertThat(count("weekly_logs", alice)).as("weeks").isEqualTo(weekRows);
        assertThat(count("entries", alice)).as("entries").isEqualTo(entryRows);
        assertThat(count("eras", alice)).as("the other Eras").isEqualTo(2);
        assertThat(detail(alice, overlapping)).isEqualTo(overlappingBefore);
        assertThat(detail(alice, elsewhere)).isEqualTo(elsewhereBefore);
        get(alice, ERAS + "/" + doomed).expectStatus().isNotFound();
    }

    @Test
    void deletingAnotherUsersEraChangesNothingForTheirWeeks() {
        User alice = aUser();
        User bob = aUser();
        WeeklyLog w4 = weekWithAnEntry(alice, W4);
        String eraId = createEra(alice, "Alice's", "2026-10-26", "2026-11-01");

        delete(bob, eraId).expectStatus().isNotFound();

        assertThat(weekIds(alice, eraId)).containsExactly(id(w4));
    }

    // Query count

    @Test
    void theQueryCountForOneEraDoesNotGrowWithTheNumberOfWeeksOrEntries() {
        User small = aUser();
        WeeklyLog onlyWeek = weeks.save(Fixtures.week(small.getId(), W4));
        entryOn(small, onlyWeek, LocalDate.of(2026, 10, 28));
        String smallEra = createEra(small, "Small", "2026-10-01", "2026-12-31");

        User big = aUser();
        for (int week = 0; week < 8; week++) {
            WeeklyLog w = weeks.save(Fixtures.week(big.getId(), W1.plusWeeks(week)));
            for (int day = 0; day < 7; day++) {
                entryOn(big, w, w.getStartDate().plusDays(day));
            }
        }
        String bigEra = createEra(big, "Big", "2026-10-01", "2026-12-31");

        int smallSelects;
        try (SqlLog sql = SqlLog.capture()) {
            get(small, ERAS + "/" + smallEra).expectStatus().isOk();
            smallSelects = sql.statementsStartingWith("select").size();
        }
        try (SqlLog sql = SqlLog.capture()) {
            get(big, ERAS + "/" + bigEra).expectStatus().isOk();
            assertThat(sql.statementsStartingWith("select"))
                    .as("8 weeks and 56 entries cost as many queries as 1 week and 1 entry")
                    .hasSize(smallSelects);
        }
        // The Era, its weeks, and the grouped count.
        assertThat(smallSelects).isEqualTo(3);
    }

    // A week's Eras (the week endpoints arrive in task 3.7; WeekEras is what they will call)

    @Test
    void aWeeksErasPutPinnedFirstThenTheLatestStartAndFollowTheDatesAtOnce() {
        User alice = aUser();
        WeeklyLog week = weekWithAnEntry(alice, W4);
        String uni = createEra(alice, "Uni Days", "2025-02-24", null);
        String job = createEra(alice, "First job", "2026-03-01", "2026-12-31");
        String trip = createEra(alice, "Weekend trip", "2026-10-31", "2026-11-01");
        String lastYear = createEra(alice, "Last year", "2025-01-01", "2025-12-31");

        assertThat(erasOfWeek(alice, week)).containsExactly("Weekend trip", "First job", "Uni Days");

        // Pin the oldest one: it comes first now, and the rest keep their order.
        putPinned(alice, uni, "Uni Days", "2025-02-24", null, true)
                .expectStatus()
                .isOk();
        assertThat(erasOfWeek(alice, week)).containsExactly("Uni Days", "Weekend trip", "First job");

        // Move last year's Era so it overlaps the week: it joins the list, and the trip leaves it.
        put(alice, lastYear, "Last year", "2025-01-01", "2026-10-26")
                .expectStatus()
                .isOk();
        put(alice, trip, "Weekend trip", "2026-11-02", "2026-11-03")
                .expectStatus()
                .isOk();
        assertThat(erasOfWeek(alice, week)).containsExactly("Uni Days", "First job", "Last year");

        // Delete one and it is gone from the list.
        delete(alice, job).expectStatus().isNoContent();
        assertThat(erasOfWeek(alice, week)).containsExactly("Uni Days", "Last year");
    }

    @Test
    void aWeekInNoEraHasAnEmptyList() {
        User alice = aUser();
        WeeklyLog week = weekWithAnEntry(alice, W4);
        createEra(alice, "Long ago", "2000-01-01", "2000-12-31");

        assertThat(erasOfWeek(alice, week)).isEmpty();
    }

    // Helpers

    private User aUser() {
        User user = users.save(Fixtures.user());
        created.add(user.getId());
        return user;
    }

    private static String id(WeeklyLog week) {
        return week.getId().toString();
    }

    /** Saves the user's week starting on this Monday, with one entry on that Monday. */
    private WeeklyLog weekWithAnEntry(User user, LocalDate monday) {
        WeeklyLog week = weeks.save(Fixtures.week(user.getId(), monday));
        entryOn(user, week, monday);
        return week;
    }

    private Entry entryOn(User user, WeeklyLog week, LocalDate date) {
        return entries.save(new Entry(UUID.randomUUID(), user.getId(), week.getId(), date));
    }

    private String createEra(User user, String title, String start, String end) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("startDate", start);
        body.put("endDate", end);
        return client.post()
                .uri(ERAS)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("id")
                .toString();
    }

    private RestTestClient.ResponseSpec put(User user, String eraId, String title, String start, String end) {
        return putPinned(user, eraId, title, start, end, false);
    }

    private RestTestClient.ResponseSpec putPinned(
            User user, String eraId, String title, String start, String end, boolean pinned) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("startDate", start);
        body.put("endDate", end);
        body.put("pinnedInFeed", pinned);
        return client.put()
                .uri(ERAS + "/" + eraId)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec delete(User user, String eraId) {
        return client.delete()
                .uri(ERAS + "/" + eraId)
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    private RestTestClient.ResponseSpec get(User user, String uri) {
        return client.get().uri(uri).header(HEADER, user.getId().toString()).exchange();
    }

    private String detail(User user, String eraId) {
        return get(user, ERAS + "/" + eraId)
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    /** The ids of the weeks the Era holds, in the order the API gives them. */
    private List<String> weekIds(User user, String eraId) {
        return JsonPath.read(detail(user, eraId), "$.weeklyLogs[*].id");
    }

    /** Week id to entry count, in the order the API gives them (newest week first). */
    private List<Map.Entry<String, Integer>> counts(User user, String eraId) {
        String body = detail(user, eraId);
        List<String> ids = JsonPath.read(body, "$.weeklyLogs[*].id");
        List<Integer> counts = JsonPath.read(body, "$.weeklyLogs[*].entryCount");
        List<Map.Entry<String, Integer>> pairs = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            pairs.add(Map.entry(ids.get(i), counts.get(i)));
        }
        return pairs;
    }

    /** The titles of a week's Eras, as {@link WeekEras} lists them from the user's Eras in the database. */
    private List<String> erasOfWeek(User user, WeeklyLog week) {
        return WeekEras.of(week, eraRepository.findByUserIdOrderByStartDateDesc(user.getId())).stream()
                .map(Era::getTitle)
                .toList();
    }

    private long count(String table, User user) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id = :id")
                .param("id", user.getId())
                .query(Long.class)
                .single();
    }
}

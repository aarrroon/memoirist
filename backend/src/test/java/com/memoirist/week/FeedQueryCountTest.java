package com.memoirist.week;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.SqlLog;
import com.memoirist.user.User;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * How many statements the feed and week detail run, counted by Hibernate's own {@link Statistics}: a page must cost
 * the same however many entries and photos it holds. This is the guard against the classic N+1 (one query for the list,
 * then one more for every row), which no functional test notices, because the answer is the same, only slower.
 *
 * <p>Statistics are switched on by {@code hibernate.generate_statistics} in {@code application-test.yml} and nowhere
 * else. The counters belong to the whole {@code SessionFactory}, so a test clears them, makes one request, and reads
 * them; the tests of a class run one at a time, and nothing else in the app touches the database in between.
 *
 * <p>What a page of the feed costs, and why (see {@code WeeklyLogService}):
 * <ol>
 *   <li>the page of weeks, 2. the user's Eras, 3. the entries of all those weeks;
 *   <li>4. the media of the open entries and 5. their tags, one query each with a {@code left join fetch}, because
 *       {@code EntryMapper} reads the lazy lists of an open entry. A sealed entry's are never read, so a page of
 *       nothing but Loot Drops runs only the first three;
 *   <li>6. the count of weeks for {@code totalItems}, but only when the page is full: Spring Data skips it for a
 *       page with room to spare, since the total is then the offset plus the rows it got. The tests below compare
 *       pages of the same fullness.
 * </ol>
 * The count is the same for one entry and for hundreds, because nothing is loaded by {@code @BatchSize(50)} on
 * {@code Entry}: if an entry's lists were left for Hibernate to load lazily, a page with more than 50 open entries
 * would cost two more statements for each further 50. The tests below fail on that too.
 */
class FeedQueryCountTest extends WeekApiTest {

    private static final String SEALED_UNTIL = "2027-01-01T00:00:00Z";
    private static final LocalDate A_MONDAY = LocalDate.parse(W4);

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    /** The statements Hibernate prepared while {@code request} ran, and nothing else. */
    private long statementsDuring(Runnable request) {
        Statistics statistics = statistics();
        statistics.clear();
        request.run();
        return statistics.getPrepareStatementCount();
    }

    @Test
    void statisticsAreOnInTheTestProfile() {
        assertThat(statistics().isStatisticsEnabled()).isTrue();
    }

    // TODO(human) PRACTICE: Write the test that proves a page of the feed costs the same number of SQL statements
    //   whatever it holds. Build a small journal (one week, one entry, one photo) and a big one for another user
    //   (8 weeks, dozens of entries, photos on most, a few sealed ones, some Eras), ask for the same page of each,
    //   count the statements Hibernate prepared with Statistics (clear() first, then getPrepareStatementCount()),
    //   and assert the two counts are equal. Then say what the count is.
    //   Compare the two counts instead of only hard-coding a number: an extra query that everyone pays is a design
    //   change worth noticing, but an extra query per entry is a bug, and only the comparison tells them apart.
    // Hint: D-22 and TECH_STACK (Weeks and the feed). The counters are global to the SessionFactory, so clear them
    //   right before the request. statementsDuring() above does that. Make the big journal hold more than 50 open
    //   entries, which is where a Hibernate batch of 50 would show.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    @Test
    void aPageCostsTheSameNumberOfStatementsWhateverItHolds() {
        User small = aUser();
        String lonely = entryOn(small, W4, "One entry");
        aPhoto(lonely, "users/test/small/1.jpg", 0);

        User big = aUser();
        createEra(big, "Long job", "2025-03-03", null, false);
        createEra(big, "Trip", "2026-10-20", "2026-10-22", true);
        createEra(big, "Uni", "2024-02-26", "2026-12-18", false);
        populate(big, 8, 9);

        long smallCount = statementsDuring(
                () -> feed(small, "page=0&size=10").expectStatus().isOk());
        long bigCount = statementsDuring(
                () -> feed(big, "page=0&size=10").expectStatus().isOk());

        assertThat(bigCount)
                .as("8 weeks and 72 entries (some sealed, most with photos) cost what 1 week and 1 entry cost")
                .isEqualTo(smallCount);
        assertThat(smallCount)
                .as("weeks, Eras, entries, media, tags (no count: the page has room to spare)")
                .isEqualTo(5);
    }
    // └── END ANSWER ▲ ──

    @Test
    void theNumberOfPhotosPerEntryDoesNotChangeTheCountEither() {
        User bare = aUser();
        populate(bare, 4, 5, 0);
        User heavy = aUser();
        populate(heavy, 4, 5, 6);

        long bareCount =
                statementsDuring(() -> feed(bare, "size=10").expectStatus().isOk());
        long heavyCount =
                statementsDuring(() -> feed(heavy, "size=10").expectStatus().isOk());

        assertThat(heavyCount).as("6 photos on every entry").isEqualTo(bareCount);
    }

    @Test
    void aPageOfOnlySealedLootDropsNeverLoadsMediaOrTags() {
        User alice = aUser();
        for (int week = 0; week < 3; week++) {
            String drop = entryOn(alice, A_MONDAY.minusWeeks(week).toString(), "Secret " + week);
            aPhoto(drop, "users/test/sealed/" + week + ".jpg", 0);
            sealUntil(alice, drop, SEALED_UNTIL);
        }

        try (SqlLog sql = SqlLog.capture()) {
            long count =
                    statementsDuring(() -> feed(alice, "size=10").expectStatus().isOk());

            assertThat(count)
                    .as("weeks, Eras, entries: the mapper skips a sealed entry's collections")
                    .isEqualTo(3);
            assertThat(sql.statements()).noneMatch(statement -> statement.matches(".*\\b(from|join) media\\b.*"));
            assertThat(sql.statements()).noneMatch(statement -> statement.contains("entry_tags"));
        }
    }

    @Test
    void theWeeksQueryFiltersDeletedEntriesInsideItsExistsSubquery() {
        User alice = aUser();
        populate(alice, 2, 2);

        try (SqlLog sql = SqlLog.capture()) {
            feed(alice, "size=10").expectStatus().isOk();
            List<String> weeks = sql.statements().stream()
                    .filter(statement -> statement.contains("from weekly_logs"))
                    .toList();
            assertThat(weeks).hasSize(1);
            assertThat(weeks.getFirst())
                    .as("Hibernate adds @SQLRestriction to the subquery on entries, not only to the outer query")
                    .containsPattern("exists\\(select 1 from entries \\w+ where \\(\\w+\\.deleted_at is null\\)");
        }
    }

    @Test
    void aFeedPageOnlyReads() {
        User alice = aUser();
        populate(alice, 3, 4);

        try (SqlLog sql = SqlLog.capture()) {
            feed(alice, "size=10").expectStatus().isOk();

            assertThat(sql.statements()).isNotEmpty().allMatch(statement -> statement.startsWith("select"));
        }
    }

    @Test
    void anEmptyFeedCostsOnlyTheOneQueryForTheWeeks() {
        User alice = aUser();

        long count =
                statementsDuring(() -> feed(alice, "size=10").expectStatus().isOk());

        assertThat(count)
                .as("the page of weeks; no count (the page has room to spare) and nothing is loaded for no weeks")
                .isEqualTo(1);
    }

    @Test
    void weekDetailCostsTheSameForOneEntryAndForThirty() {
        User small = aUser();
        String lonely = entryOn(small, W4, "One entry");
        aPhoto(lonely, "users/test/detail/1.jpg", 0);
        String weekOfOne = weekIdOf(lonely);
        User big = aUser();
        createEra(big, "Trip", "2026-10-20", "2026-10-30", true);
        createEra(big, "Long job", "2025-03-03", null, false);
        String weekOfThirty = populateOneWeek(big, 30);

        long smallCount =
                statementsDuring(() -> getWeek(small, weekOfOne).expectStatus().isOk());
        long bigCount =
                statementsDuring(() -> getWeek(big, weekOfThirty).expectStatus().isOk());

        assertThat(bigCount)
                .as("30 entries with photos, some sealed, in two Eras")
                .isEqualTo(smallCount);
        assertThat(smallCount).as("the week, Eras, entries, media, tags").isEqualTo(5);
    }

    @Test
    void aFullPageAddsTheCountOfWeeksAndAShortPageDoesNot() {
        User alice = aUser();
        populate(alice, 3, 4);

        long firstOfThree = statementsDuring(
                () -> feed(alice, "page=0&size=2").expectStatus().isOk());
        long lastOfThree = statementsDuring(
                () -> feed(alice, "page=1&size=2").expectStatus().isOk());
        long roomToSpare = statementsDuring(
                () -> feed(alice, "page=0&size=10").expectStatus().isOk());

        assertThat(firstOfThree)
                .as("weeks, count, Eras, entries, media, tags: a full first page has to ask how many there are")
                .isEqualTo(6);
        assertThat(lastOfThree)
                .as("the last page is short, so its total is the offset plus its rows")
                .isEqualTo(5);
        assertThat(roomToSpare).as("no count when the page has room to spare").isEqualTo(5);
    }

    @Test
    void thePageCostsTheSameJustPastTheBatchSizeOf50OpenEntriesAndWellPastIt() {
        User fifty = aUser();
        populateOneWeek(fifty, 50, 1, false);
        User sixty = aUser();
        populateOneWeek(sixty, 60, 1, false);
        User twelveWeeks = aUser();
        populate(twelveWeeks, 12, 10, 2);

        long fiftyCount =
                statementsDuring(() -> feed(fifty, "size=10").expectStatus().isOk());
        long sixtyCount =
                statementsDuring(() -> feed(sixty, "size=10").expectStatus().isOk());
        long manyCount = statementsDuring(
                () -> feed(twelveWeeks, "size=20").expectStatus().isOk());

        assertThat(fiftyCount)
                .as("50 open entries: weeks, Eras, entries, media, tags")
                .isEqualTo(5);
        assertThat(sixtyCount)
                .as("60 open entries, one over the batch size Hibernate would have used")
                .isEqualTo(fiftyCount);
        assertThat(manyCount)
                .as("12 weeks and 120 entries (about 100 of them open), 2 photos on each")
                .isEqualTo(fiftyCount);
    }

    @Test
    void anEntryIsFilledInByTheSameTwoQueriesFromTheVeryInstantItOpens() {
        User alice = aUser();
        String drop = entryOn(alice, W3, "Opens on Monday");
        aPhoto(drop, "users/test/entries/drop/photo.jpg", 0);
        aTag(alice, drop, "later");
        sealUntil(alice, drop, "2026-11-02T01:00:00Z");

        clockAt("2026-11-02T00:59:59Z");
        long beforeCount =
                statementsDuring(() -> feed(alice, "size=10").expectStatus().isOk());
        clockAt("2026-11-02T01:00:00Z");
        try (SqlLog sql = SqlLog.capture()) {
            long atCount =
                    statementsDuring(() -> feed(alice, "size=10").expectStatus().isOk());

            assertThat(beforeCount).as("still sealed: weeks, Eras, entries").isEqualTo(3);
            assertThat(atCount)
                    .as("open at the instant: weeks, Eras, entries, media, tags")
                    .isEqualTo(5);
            assertThat(sql.statements())
                    .as("the queries use the same rule as the mapper, so the entry's media comes from the join")
                    .filteredOn(statement -> statement.matches(".*\\bjoin media\\b.*"))
                    .hasSize(1);
            assertThat(sql.statements())
                    .as("and its tags too, so Hibernate's lazy loading never runs for the entry")
                    .filteredOn(statement -> statement.contains("entry_tags") && statement.contains("unlock_date"))
                    .hasSize(1);
        }
    }

    @Test
    void theMediaAndTagsAreEachOneQueryThatLeavesSealedEntriesOut() {
        User alice = aUser();
        populate(alice, 3, 6);
        String drop = entryOn(alice, W3, "A drop with a photo");
        aPhoto(drop, "users/test/entries/drop/sealed-photo.jpg", 0);
        aTag(alice, drop, "secret");
        sealUntil(alice, drop, SEALED_UNTIL);

        try (SqlLog sql = SqlLog.capture()) {
            feed(alice, "size=10").expectStatus().isOk();

            List<String> media = sql.statements().stream()
                    .filter(statement -> statement.matches(".*\\bjoin media\\b.*"))
                    .toList();
            List<String> tags = sql.statements().stream()
                    .filter(statement -> statement.contains("entry_tags"))
                    .toList();
            assertThat(media).as("one query for all the open entries' media").hasSize(1);
            assertThat(media.getFirst())
                    .as("sealed entries are left out in the query")
                    .contains("unlock_date");
            assertThat(tags).as("one query for all the open entries' tags").hasSize(1);
            assertThat(tags.getFirst())
                    .as("sealed entries are left out in the query")
                    .contains("unlock_date");
            assertThat(sql.statements())
                    .as("nothing is left for Hibernate's lazy batches")
                    .noneMatch(statement -> statement.contains("= any (?)"));
        }
    }

    // The journals

    /** {@code weeks} weeks of {@code perWeek} entries each, newest week first, with photos (0 to 3) and some sealed. */
    private void populate(User user, int weeks, int perWeek) {
        populate(user, weeks, perWeek, -1);
    }

    /**
     * {@code photos} photos on every entry, or, if it is -1, a mix of 0 to 3. Every seventh entry is a sealed Loot Drop.
     */
    private void populate(User user, int weeks, int perWeek, int photos) {
        int n = 0;
        for (int week = 0; week < weeks; week++) {
            String monday = A_MONDAY.minusWeeks(week).toString();
            for (int i = 0; i < perWeek; i++) {
                String entryId = entryOn(user, monday, "Entry " + week + "-" + i);
                int count = photos >= 0 ? photos : n % 4;
                for (int p = 0; p < count; p++) {
                    aPhoto(entryId, "users/test/" + entryId + "/" + p + ".jpg", p);
                }
                if (n % 7 == 6) {
                    sealUntil(user, entryId, SEALED_UNTIL);
                }
                n++;
            }
        }
    }

    /** One week of {@code entries} entries with a photo each, every seventh sealed; returns the week's id. */
    private String populateOneWeek(User user, int entries) {
        return populateOneWeek(user, entries, 2, true);
    }

    private String populateOneWeek(User user, int entries, int photos, boolean someSealed) {
        String weekId = null;
        for (int i = 0; i < entries; i++) {
            String entryId = entryOn(user, W4, "Entry " + i);
            weekId = weekIdOf(entryId);
            for (int p = 0; p < photos; p++) {
                aPhoto(entryId, "users/test/" + entryId + "/" + p + ".jpg", p);
            }
            if (someSealed && i % 7 == 6) {
                sealUntil(user, entryId, SEALED_UNTIL);
            }
        }
        return weekId;
    }
}

package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.SqlLog;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the On this day query reads (task 3.12, TECH_STACK Entries). There is no index on the month and day of
 * {@code entry_date}, on purpose: the query reads the user's own entries in date order from
 * {@code idx_entries_user_date} and tests the month and day on each one. That is cheap for one person's journal and
 * would be expensive only if it read other people's rows, which is what this test guards.
 *
 * <p>It fills the table with a ten-year daily journal for Alice and a much bigger one for Bob, takes the SQL that
 * Hibernate really ran for {@link EntryRepository#findOnThisDay} with {@link SqlLog}, puts the values in place of its
 * {@code ?} marks and runs {@code EXPLAIN (ANALYZE)}. Nothing here is a copy of the query: if someone changes the JPQL
 * so that the user and date range stop being an index condition, the plan changes and this fails. The rows are rolled
 * back with the test's transaction.
 */
@IntegrationTest
@Transactional
class OnThisDayIndexTest {

    private static final Instant NOW = Instant.parse("2026-10-31T01:00:00Z");
    private static final Pattern REMOVED_BY_FILTER = Pattern.compile("Rows Removed by Filter: (\\d+)");

    @Autowired
    EntityManager em;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    UserRepository users;

    @Autowired
    WeeklyLogRepository weeks;

    @Autowired
    EntryRepository entries;

    @Test
    void theQueryReadsOnlyTheUsersOwnEntriesThroughTheUserAndDateIndex() {
        User alice = users.save(Fixtures.user());
        User bob = users.save(Fixtures.user());
        WeeklyLog aliceWeek = weeks.save(Fixtures.week(alice.getId()));
        WeeklyLog bobWeek = weeks.save(Fixtures.week(bob.getId()));
        em.flush();
        // Alice wrote every day for ten years: 3,652 entries from 2016-11-01 to 2026-10-31. Bob has 60,000 entries over
        // more than a century. Only Alice's rows may be touched when she opens the strip.
        fill(alice, aliceWeek, "DATE '2016-11-01' + g", 3_652);
        fill(bob, bobWeek, "DATE '1900-01-01' + (g % 44000)", 60_000);
        jdbc.sql("ANALYZE entries").update();

        List<String> statements;
        List<Entry> strip;
        try (SqlLog sql = SqlLog.capture()) {
            strip = entries.findOnThisDay(
                    alice.getId(), 10, List.of(31), LocalDate.parse("2026-01-01"), NOW, Limit.of(20));
            statements = sql.statements();
        }

        assertThat(strip)
                .as("Alice wrote from 1 November 2016, so 31 October of 2017 through 2025")
                .hasSize(9);
        String select = statements.stream()
                .filter(statement -> statement.startsWith("select") && statement.contains("extract(month"))
                .findFirst()
                .orElseThrow();
        assertThat(select.chars().filter(c -> c == '?').count())
                .as("user, month, one day, before, now and the limit are the only parameters of: %s", select)
                .isEqualTo(6);
        String explainable = select.replaceFirst("\\?", "'" + alice.getId() + "'::uuid")
                .replaceFirst("\\?", "10")
                .replaceFirst("\\?", "31")
                .replaceFirst("\\?", "DATE '2026-01-01'")
                .replaceFirst("\\?", "'" + NOW + "'::timestamptz")
                .replaceFirst("\\?", "20");

        String plan = String.join(
                "\n",
                jdbc.sql("EXPLAIN (ANALYZE) " + explainable).query(String.class).list());

        // Postgres cannot see through extract(), so it expects one row and often picks a bitmap scan.
        // A plain index scan is fine too. Either way the user and date range are the index condition.
        assertThat(plan)
                .as("the plan for: %s", explainable)
                .containsPattern("(Bitmap Index Scan on|Index Scan using) idx_entries_user_date");
        assertThat(plan).doesNotContain("Seq Scan on entries");
        assertThat(rowsRemovedByTheFilter(plan))
                .as("the month and day are tested on Alice's rows only, never on Bob's 60,000: %s", plan)
                .isLessThanOrEqualTo(3_652);
    }

    /** Every row that the plan's nodes read and then threw away, added up. */
    private static long rowsRemovedByTheFilter(String plan) {
        long total = 0;
        Matcher matcher = REMOVED_BY_FILTER.matcher(plan);
        while (matcher.find()) {
            total += Long.parseLong(matcher.group(1));
        }
        return total;
    }

    /** {@code count} plain entries for the user, one per row of a series, dated by the SQL expression {@code date}. */
    private void fill(User user, WeeklyLog week, String date, int count) {
        jdbc.sql("""
                        INSERT INTO entries (id, user_id, weekly_log_id, entry_date)
                        SELECT gen_random_uuid(), :user, :week, %s FROM generate_series(0, %d) g
                        """.formatted(date, count - 1))
                .param("user", user.getId())
                .param("week", week.getId())
                .update();
    }
}

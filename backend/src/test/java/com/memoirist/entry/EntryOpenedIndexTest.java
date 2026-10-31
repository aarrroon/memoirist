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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Opened strip's query is served by the partial index {@code idx_entries_opened_unseen} (task 3.10, DATABASE_SCHEMA
 * Indexes), so it stays fast for a user with years of Loot Drops that were all seen long ago.
 *
 * <p>It fills a table with the shape of a long-time user (most Loot Drops seen, a few waiting, plus many entries that
 * were never sealed and another user's), takes the SQL that Hibernate really ran for
 * {@link EntryRepository#findOpenedUnseen} with {@link SqlLog}, puts the values in place of its {@code ?} marks and asks
 * Postgres to {@code EXPLAIN} it. Nothing here is a copy of the query: if someone changes the JPQL so that the index
 * can't be used (say, by testing {@code unlock_seen_at} in a way the partial index's condition doesn't imply), the plan
 * changes and this fails. The rows are rolled back with the test's transaction.
 */
@IntegrationTest
@Transactional
class EntryOpenedIndexTest {

    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");

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
    void theStripQueryReadsThePartialIndexNotTheWholeTable() {
        User alice = users.save(Fixtures.user());
        User bob = users.save(Fixtures.user());
        WeeklyLog aliceWeek = weeks.save(Fixtures.week(alice.getId()));
        WeeklyLog bobWeek = weeks.save(Fixtures.week(bob.getId()));
        em.flush();
        // Alice: 20,000 Loot Drops opened over the year, every 200th still unseen (100 waiting), and 10,000 plain
        // entries. Bob: 20,000 Loot Drops, all seen. Only Alice's 100 are in the partial index (and none of Bob's).
        fill(alice, aliceWeek, 20_000, "g % 200 = 0");
        fill(bob, bobWeek, 20_000, "FALSE");
        jdbc.sql("""
                        INSERT INTO entries (id, user_id, weekly_log_id, entry_date)
                        SELECT gen_random_uuid(), :user, :week, DATE '2026-10-26' FROM generate_series(1, 10000)
                        """)
                .param("user", alice.getId())
                .param("week", aliceWeek.getId())
                .update();
        jdbc.sql("ANALYZE entries").update();

        List<String> statements;
        List<Entry> strip;
        try (SqlLog sql = SqlLog.capture()) {
            strip = entries.findOpenedUnseen(alice.getId(), NOW, Limit.of(20));
            statements = sql.statements();
        }

        assertThat(strip).as("20 of Alice's 100 waiting Loot Drops").hasSize(20);
        String select = statements.stream()
                .filter(statement -> statement.startsWith("select") && statement.contains("unlock_seen_at is null"))
                .findFirst()
                .orElseThrow();
        assertThat(select.chars().filter(c -> c == '?').count())
                .as("the user, now and the limit are the only parameters of: %s", select)
                .isEqualTo(3);
        String explainable = select.replaceFirst("\\?", "'" + alice.getId() + "'::uuid")
                .replaceFirst("\\?", "'" + NOW + "'::timestamptz")
                .replaceFirst("\\?", "20");

        String plan = String.join(
                "\n", jdbc.sql("EXPLAIN " + explainable).query(String.class).list());

        assertThat(plan).as("the plan for: %s", explainable).contains("idx_entries_opened_unseen");
        assertThat(plan).doesNotContain("Seq Scan on entries");
    }

    /** Loot Drops that opened one minute apart through 2026, with the seen mark on all except where {@code unseen} holds. */
    private void fill(User user, WeeklyLog week, int count, String unseen) {
        jdbc.sql("""
                        INSERT INTO entries (id, user_id, weekly_log_id, entry_date, unlock_date, unlock_seen_at)
                        SELECT gen_random_uuid(), :user, :week, DATE '2026-10-26',
                               TIMESTAMPTZ '2026-01-01 00:00:00+00' + g * INTERVAL '10 minutes',
                               CASE WHEN %s THEN NULL ELSE TIMESTAMPTZ '2026-10-01 00:00:00+00' END
                        FROM generate_series(1, %d) g
                        """.formatted(unseen, count))
                .param("user", user.getId())
                .param("week", week.getId())
                .update();
    }
}

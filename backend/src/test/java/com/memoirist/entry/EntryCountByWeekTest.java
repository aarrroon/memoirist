package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link EntryRepository#countOpenEntriesByWeek}, the count behind an Era's page: which entries it counts, with the
 * clock handed in so the moment a Loot Drop opens can be tested to the microsecond. The same query is exercised through
 * HTTP in {@code EraWeeksTest}; this class pins its edges.
 *
 * <p>Every test runs in a transaction that is rolled back at the end, and {@code em.flush()} makes Postgres see the
 * rows before the query runs.
 */
@IntegrationTest
@Transactional
class EntryCountByWeekTest {

    private static final Instant NOW = Instant.parse("2026-10-31T01:00:00Z");
    private static final LocalDate MONDAY = Fixtures.MONDAY; // 2026-10-26

    @Autowired
    EntityManager em;

    @Autowired
    UserRepository users;

    @Autowired
    WeeklyLogRepository weeks;

    @Autowired
    EntryRepository entries;

    User alice;
    WeeklyLog week;

    @BeforeEach
    void aUserWithAWeek() {
        alice = users.save(Fixtures.user());
        week = weeks.save(Fixtures.week(alice.getId(), MONDAY));
    }

    @Test
    void entriesAreCountedPerWeekAndOnlyWeeksWithSomethingAreReturned() {
        WeeklyLog next = weeks.save(Fixtures.week(alice.getId(), MONDAY.plusWeeks(1)));
        WeeklyLog empty = weeks.save(Fixtures.week(alice.getId(), MONDAY.plusWeeks(2)));
        on(week, MONDAY);
        on(week, MONDAY.plusDays(2));
        on(next, MONDAY.plusWeeks(1));
        em.flush();

        Map<UUID, Long> counts = countsFor(MONDAY, null);

        assertThat(counts).containsOnlyKeys(week.getId(), next.getId());
        assertThat(counts).containsEntry(week.getId(), 2L).containsEntry(next.getId(), 1L);
        assertThat(counts).doesNotContainKey(empty.getId());
    }

    @Test
    void bothEndsOfTheDateRangeAreIncluded() {
        on(week, MONDAY.minusDays(1)); // the day before
        on(week, MONDAY); // the first day
        on(week, MONDAY.plusDays(2)); // the last day
        on(week, MONDAY.plusDays(3)); // the day after
        em.flush();

        assertThat(countsFor(MONDAY, MONDAY.plusDays(2))).containsEntry(week.getId(), 2L);
    }

    @Test
    void aNullEndMeansNoEnd() {
        on(week, MONDAY);
        on(week, MONDAY.plusYears(30));
        em.flush();

        assertThat(total(countsFor(MONDAY, null))).isEqualTo(2L);
        assertThat(total(countsFor(MONDAY, MONDAY.plusDays(1)))).isEqualTo(1L);
    }

    @Test
    void aLootDropCountsFromTheMomentItOpensAndNotBefore() {
        on(week, MONDAY); // never sealed
        on(week, MONDAY).setUnlockDate(NOW); // opens this very moment
        on(week, MONDAY).setUnlockDate(NOW.minusSeconds(1)); // opened a second ago
        on(week, MONDAY).setUnlockDate(NOW.plusNanos(1_000)); // opens one microsecond from now: still sealed
        em.flush();

        // Sealed means the unlock date is after now (security.md), so an unlock date equal to now is already open.
        assertThat(countsFor(MONDAY, null)).containsEntry(week.getId(), 3L);
    }

    @Test
    void theClockDecidesSoTheSameRowsCountOnceTheyHaveOpened() {
        on(week, MONDAY).setUnlockDate(NOW.plusSeconds(60));
        em.flush();

        assertThat(countsFor(MONDAY, null)).isEmpty();
        assertThat(entries.countOpenEntriesByWeek(alice.getId(), MONDAY, null, NOW.plusSeconds(60)))
                .extracting(EntryCountByWeek::entryCount)
                .containsExactly(1L);
    }

    @Test
    void entriesInRecentlyDeletedAreNotCounted() {
        on(week, MONDAY);
        on(week, MONDAY).setDeletedAt(NOW.minusSeconds(60));
        em.flush();

        assertThat(countsFor(MONDAY, null)).containsEntry(week.getId(), 1L);
    }

    @Test
    void aSealedEntryThatWasDeletedIsNotCountedEither() {
        Entry both = on(week, MONDAY);
        both.setUnlockDate(NOW.plusSeconds(86_400));
        both.setDeletedAt(NOW.minusSeconds(60));
        em.flush();

        assertThat(countsFor(MONDAY, null)).isEmpty();
    }

    @Test
    void anotherUsersEntriesAreNotCountedEvenInTheSameDays() {
        User bob = users.save(Fixtures.user());
        WeeklyLog bobWeek = weeks.save(Fixtures.week(bob.getId(), MONDAY));
        on(week, MONDAY);
        entries.save(new Entry(UUID.randomUUID(), bob.getId(), bobWeek.getId(), MONDAY));
        entries.save(new Entry(UUID.randomUUID(), bob.getId(), bobWeek.getId(), MONDAY));
        em.flush();

        assertThat(countsFor(MONDAY, null)).containsOnlyKeys(week.getId()).containsEntry(week.getId(), 1L);
        assertThat(entries.countOpenEntriesByWeek(bob.getId(), MONDAY, null, NOW))
                .extracting(EntryCountByWeek::entryCount)
                .containsExactly(2L);
    }

    @Test
    void aUserWithNoEntriesGetsAnEmptyList() {
        assertThat(entries.countOpenEntriesByWeek(alice.getId(), MONDAY, null, NOW))
                .isEmpty();
    }

    private Entry on(WeeklyLog w, LocalDate date) {
        return entries.save(new Entry(UUID.randomUUID(), alice.getId(), w.getId(), date));
    }

    private Map<UUID, Long> countsFor(LocalDate from, LocalDate to) {
        List<EntryCountByWeek> rows = entries.countOpenEntriesByWeek(alice.getId(), from, to, NOW);
        return rows.stream().collect(Collectors.toMap(EntryCountByWeek::weeklyLogId, EntryCountByWeek::entryCount));
    }

    private static long total(Map<UUID, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }
}

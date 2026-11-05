package com.memoirist.week;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.era.Era;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Which Eras hold a week, and in what order the API lists them (D-22). Plain Java, no database: the edges of the date
 * rule are cheap to check here, and {@code EraWeekAgreementTest} runs the same edges through SQL.
 *
 * <p>The week used throughout is Monday 2026-10-26 to Sunday 2026-11-01.
 */
class WeekErasTest {

    private static final UUID USER = UUID.randomUUID();
    private static final WeeklyLog WEEK = new WeeklyLog(UUID.randomUUID(), USER, LocalDate.of(2026, 10, 26));

    // The rule

    /** Era start, Era end ({@code open} for none), and whether the Era holds the week. */
    @ParameterizedTest(name = "Era {0} to {1}: holds the week = {2}")
    @CsvSource({
        // Ending
        "2026-01-01, 2026-10-25, false", // ended the Sunday before the week
        "2026-01-01, 2026-10-26, true", // ended on the week's Monday: that day is in the Era
        "2026-01-01, 2026-11-01, true", // ended on the week's Sunday
        "2026-01-01, 2027-01-01, true", // ends long after the week
        // Starting
        "2026-11-01, 2026-12-01, true", // starts on the week's Sunday
        "2026-11-02, 2026-12-01, false", // starts the Monday after the week
        "2026-10-26, open, true", // starts on the week's Monday and never ends
        // Shapes
        "2026-10-28, 2026-10-28, true", // a one-day Era in the middle of the week
        "2026-10-24, 2026-10-24, false", // a one-day Era on the Saturday before
        "2026-11-02, 2026-11-02, false", // a one-day Era on the Monday after
        "2026-10-24, 2026-11-02, true", // starts before and ends after: it covers the whole week
        "2020-01-01, open, true", // an old Era that is still going
        "2026-11-02, open, false", // an Era that has not started yet
    })
    void anEraHoldsAWeekWhenItsDatesOverlapIt(String start, String end, boolean expected) {
        assertThat(WeekEras.holds(era(start, end), WEEK)).isEqualTo(expected);
    }

    @Test
    void ofKeepsOnlyTheErasThatHoldTheWeek() {
        Era before = era("2026-01-01", "2026-10-25");
        Era during = era("2026-10-27", "2026-10-29");
        Era after = era("2026-11-02", "open");

        assertThat(WeekEras.of(WEEK, List.of(before, during, after))).containsExactly(during);
    }

    @Test
    void aWeekInNoEraHasAnEmptyListNotNull() {
        assertThat(WeekEras.of(WEEK, List.of(era("2026-01-01", "2026-01-31")))).isEmpty();
        assertThat(WeekEras.of(WEEK, List.of())).isEmpty();
    }

    // The order

    @Test
    void theLatestStartComesFirstWhenNothingIsPinned() {
        Era uni = era("2025-02-24", "open");
        Era job = era("2026-03-01", "2026-12-31");
        Era trip = era("2026-10-28", "2026-10-30");

        assertThat(WeekEras.of(WEEK, List.of(uni, job, trip))).containsExactly(trip, job, uni);
    }

    @Test
    void aPinnedEraComesFirstEvenWhenItStartedEarlier() {
        Era uni = pinned(era("2025-02-24", "open"));
        Era trip = era("2026-10-28", "2026-10-30");

        assertThat(WeekEras.of(WEEK, List.of(trip, uni))).containsExactly(uni, trip);
    }

    @Test
    void amongSeveralPinnedErasTheLatestStartWinsAndThenTheUnpinnedFollowByStart() {
        Era oldPinned = pinned(era("2024-01-01", "open"));
        Era newPinned = pinned(era("2026-06-01", "open"));
        Era newestUnpinned = era("2026-10-28", "2026-10-30");
        Era olderUnpinned = era("2026-03-01", "open");

        assertThat(WeekEras.of(WEEK, List.of(oldPinned, olderUnpinned, newestUnpinned, newPinned)))
                .containsExactly(newPinned, oldPinned, newestUnpinned, olderUnpinned);
    }

    @Test
    void theOrderDoesNotDependOnTheOrderTheErasArriveIn() {
        Era a = pinned(era("2025-01-01", "open"));
        Era b = era("2026-09-01", "open");
        Era c = era("2026-10-01", "open");
        Era d = pinned(era("2026-02-01", "open"));

        List<Era> expected = List.of(d, a, c, b);
        assertThat(WeekEras.of(WEEK, List.of(a, b, c, d))).isEqualTo(expected);
        assertThat(WeekEras.of(WEEK, List.of(d, c, b, a))).isEqualTo(expected);
        assertThat(WeekEras.of(WEEK, List.of(b, d, a, c))).isEqualTo(expected);
    }

    @Test
    void erasThatStartOnTheSameDayComeNewestCreatedFirst() {
        Era older = era("2026-10-01", "open");
        Era newer = era("2026-10-01", "open");
        createdAt(older, "2026-10-02T01:00:00Z");
        createdAt(newer, "2026-10-03T01:00:00Z");

        assertThat(WeekEras.of(WEEK, List.of(older, newer))).containsExactly(newer, older);
        assertThat(WeekEras.of(WEEK, List.of(newer, older))).containsExactly(newer, older);
    }

    @Test
    void theListOrderIsTheLatestStartThenNewestCreatedThenId() {
        // Era.LATEST_START_FIRST also orders GET /eras. Two Eras that are the same in every way still have an order.
        Era a = era("2026-10-01", "open");
        Era b = era("2026-10-01", "open");
        Instant same = Instant.parse("2026-10-02T01:00:00Z");
        ReflectionTestUtils.setField(a, "createdAt", same);
        ReflectionTestUtils.setField(b, "createdAt", same);

        List<Era> sorted = List.of(a, b).stream().sorted(Era.LATEST_START_FIRST).toList();
        List<Era> reversed =
                List.of(b, a).stream().sorted(Era.LATEST_START_FIRST).toList();

        assertThat(sorted).isEqualTo(reversed);
        assertThat(sorted.get(0).getId()).isLessThan(sorted.get(1).getId());
    }

    // Helpers

    private static Era era(String start, String end) {
        LocalDate startDate = LocalDate.parse(start);
        return "open".equals(end) ? Fixtures.era(USER, startDate) : Fixtures.era(USER, startDate, LocalDate.parse(end));
    }

    private static Era pinned(Era era) {
        era.setPinnedInFeed(true);
        return era;
    }

    /** Hibernate fills in {@code createdAt} when it writes the row; here nothing is written, so set it directly. */
    private static void createdAt(Era era, String instant) {
        ReflectionTestUtils.setField(era, "createdAt", Instant.parse(instant));
    }
}

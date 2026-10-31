package com.memoirist.week;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Which week a day belongs to (D-08): Monday to Sunday for everyone, in the user's zone. Plain logic, so no Spring and
 * no Docker. The same rules are checked through the HTTP API in {@code EntryDatesTest}.
 */
class WeekCalendarTest {

    private static final ZoneId MELBOURNE = ZoneId.of("Australia/Melbourne");

    @Test
    void everyWeekStartsOnMondayAndTheRuleLivesInOnePlace() {
        assertThat(WeekCalendar.FIRST_DAY_OF_WEEK).isEqualTo(DayOfWeek.MONDAY);
    }

    @ParameterizedTest(name = "{0} is in the week of {1}")
    @CsvSource({
        "2026-10-26, 2026-10-26", // a Monday is the first day of its own week
        "2026-10-27, 2026-10-26", // Tuesday
        "2026-10-28, 2026-10-26",
        "2026-10-29, 2026-10-26",
        "2026-10-30, 2026-10-26",
        "2026-10-31, 2026-10-26", // Saturday
        "2026-11-01, 2026-10-26", // Sunday is the last day of the week that began on Monday 26th
        "2026-11-02, 2026-11-02", // and the next Monday starts a new one
    })
    void aDayBelongsToTheWeekThatStartsOnTheMondayAtOrBeforeIt(LocalDate day, LocalDate monday) {
        assertThat(WeekCalendar.firstDayOf(day)).isEqualTo(monday);
        assertThat(WeekCalendar.lastDayOf(day)).isEqualTo(monday.plusDays(6));
    }

    @ParameterizedTest(name = "{0} is in the week of {1}")
    @CsvSource({
        "2027-01-01, 2026-12-28", // a week can span two years
        "2028-02-29, 2028-02-28", // a leap day
        "2028-03-01, 2028-02-28", // the week runs on across the end of February
        "1900-01-01, 1900-01-01", // the earliest memory date is itself a Monday
        "1900-01-07, 1900-01-01",
        "2026-04-05, 2026-03-30", // the day Melbourne's daylight saving ended in 2026 (a 25-hour Sunday)
        "2026-10-04, 2026-09-28", // and the day it began (a 23-hour Sunday)
    })
    void edgesOfTheCalendarFollowTheSameRule(LocalDate day, LocalDate monday) {
        assertThat(WeekCalendar.firstDayOf(day)).isEqualTo(monday);
    }

    @Test
    void sundayAt2330InMelbourneIsStillThatWeek() {
        // Sunday 2026-11-01 23:30 in Melbourne (UTC+11) is 12:30 UTC the same day.
        Instant sundayNight = Instant.parse("2026-11-01T12:30:00Z");

        assertThat(WeekCalendar.firstDayOf(sundayNight, MELBOURNE)).isEqualTo(LocalDate.parse("2026-10-26"));
    }

    @Test
    void theSameInstantIsAnotherWeekWhereItIsAlreadyMonday() {
        // 13:30 UTC on Sunday 1 November is 00:30 on Monday 2 November in Melbourne, but still Sunday in London.
        Instant moment = Instant.parse("2026-11-01T13:30:00Z");

        assertThat(WeekCalendar.firstDayOf(moment, MELBOURNE)).isEqualTo(LocalDate.parse("2026-11-02"));
        assertThat(WeekCalendar.firstDayOf(moment, ZoneId.of("UTC"))).isEqualTo(LocalDate.parse("2026-10-26"));
        assertThat(WeekCalendar.firstDayOf(moment, ZoneId.of("Europe/London")))
                .isEqualTo(LocalDate.parse("2026-10-26"));
    }

    @Test
    void aZoneBehindUtcCanBeInTheEarlierWeekWhileUtcIsAlreadyMonday() {
        // 03:00 UTC on Monday 2 November is 19:00 on Sunday 1 November in Los Angeles (daylight saving ended that
        // morning, so UTC-8).
        Instant moment = Instant.parse("2026-11-02T03:00:00Z");

        assertThat(WeekCalendar.firstDayOf(moment, ZoneId.of("America/Los_Angeles")))
                .isEqualTo(LocalDate.parse("2026-10-26"));
        assertThat(WeekCalendar.firstDayOf(moment, ZoneId.of("UTC"))).isEqualTo(LocalDate.parse("2026-11-02"));
    }

    @Test
    void whenDaylightSavingBeginsMidnightArrivesAnHourSoonerInUtc() {
        // Melbourne clocks went forward at 02:00 on Sunday 4 October 2026, so that Sunday had 23 hours. Midnight
        // between Sunday and Monday is 13:00 UTC (UTC+11) and no longer 14:00 UTC (UTC+10).
        assertThat(WeekCalendar.firstDayOf(Instant.parse("2026-10-04T12:59:59Z"), MELBOURNE))
                .as("23:59:59 on the Sunday that began daylight saving")
                .isEqualTo(LocalDate.parse("2026-09-28"));
        assertThat(WeekCalendar.firstDayOf(Instant.parse("2026-10-04T13:00:00Z"), MELBOURNE))
                .as("00:00:00 on the Monday after it")
                .isEqualTo(LocalDate.parse("2026-10-05"));
    }

    @Test
    void whenDaylightSavingEndsMidnightArrivesAnHourLaterInUtc() {
        // Melbourne clocks went back at 03:00 on Sunday 4 April 2027, so that Sunday had 25 hours. Midnight between
        // Sunday and Monday is 14:00 UTC (UTC+10), not 13:00 UTC as it was while the clocks were still forward.
        assertThat(WeekCalendar.firstDayOf(Instant.parse("2027-04-04T13:59:59Z"), MELBOURNE))
                .as("23:59:59 on the 25-hour Sunday")
                .isEqualTo(LocalDate.parse("2027-03-29"));
        assertThat(WeekCalendar.firstDayOf(Instant.parse("2027-04-04T14:00:00Z"), MELBOURNE))
                .as("00:00:00 on the Monday after it")
                .isEqualTo(LocalDate.parse("2027-04-05"));
    }
}

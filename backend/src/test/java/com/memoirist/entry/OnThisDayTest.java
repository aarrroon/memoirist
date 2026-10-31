package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Which memory dates count as "this day in an earlier year" (D-32), as plain logic: no Spring, no Docker. The same
 * rules are checked through the HTTP API in {@code OnThisDayEndpointTest}; here the awkward days are covered with one
 * line each.
 */
class OnThisDayTest {

    @Test
    void anOrdinaryDayLooksForItsOwnMonthAndDayInEarlierYears() {
        OnThisDay.Lookup lookup = OnThisDay.lookupFor(LocalDate.parse("2026-10-31"));

        assertThat(lookup.month()).isEqualTo(Month.OCTOBER);
        assertThat(lookup.days()).containsExactly(31);
        assertThat(lookup.before()).as("only years before this one").isEqualTo(LocalDate.parse("2026-01-01"));
    }

    @ParameterizedTest(name = "{0} looks for February {1}")
    @CsvSource({
        "2027-02-28, '28,29'", // not a leap year: 29 February has no day of its own, so it shows on the 28th
        "2026-02-28, '28,29'",
        "2100-02-28, '28,29'", // a century year is a leap year only if divisible by 400, and 2100 is not
        "2028-02-28, '28'", // a leap year: the 28th is only the 28th, the 29th comes tomorrow
        "2000-02-28, '28'", // divisible by 400, so a leap year
        "2028-02-29, '29'", // the leap day itself looks for earlier leap days only
        "2027-02-27, '27'", // the day before the rule's day is an ordinary day
    })
    void theLeapDayRuleAppliesOnlyOnThe28thOfFebruaryInAYearThatIsNotALeapYear(LocalDate today, String days) {
        OnThisDay.Lookup lookup = OnThisDay.lookupFor(today);

        assertThat(lookup.month()).isEqualTo(Month.FEBRUARY);
        assertThat(lookup.days()).isEqualTo(parse(days));
    }

    @ParameterizedTest(name = "{0} looks for {1} {2}")
    @CsvSource({
        "2027-03-01, MARCH, 1", // 29 February entries do not follow on to 1 March
        "2027-01-28, JANUARY, 28", // 28 January is no 28 February
        "2027-12-31, DECEMBER, 31",
    })
    void otherDaysNearTheEndOfFebruaryOrTheYearMatchOnlyThemselves(LocalDate today, Month month, int day) {
        OnThisDay.Lookup lookup = OnThisDay.lookupFor(today);

        assertThat(lookup.month()).isEqualTo(month);
        assertThat(lookup.days()).containsExactly(day);
    }

    @ParameterizedTest(name = "on {0} an entry dated before {1} is from an earlier year")
    @CsvSource({
        "2026-01-01, 2026-01-01", // the first day of the year: this year's own 1 January is not "earlier"
        "2026-06-15, 2026-01-01",
        "2026-12-31, 2026-01-01",
        "2028-02-29, 2028-01-01",
    })
    void onlyDatesBeforeTheStartOfThisYearAreEarlierYears(LocalDate today, LocalDate before) {
        assertThat(OnThisDay.lookupFor(today).before()).isEqualTo(before);
    }

    private static List<Integer> parse(String days) {
        return Arrays.stream(days.split(","))
                .map(String::strip)
                .map(Integer::valueOf)
                .toList();
    }
}

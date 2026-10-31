package com.memoirist.entry;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

/**
 * The rule behind the On this day strip (D-32), as plain Java with no database: given the user's today, which memory
 * dates count as "this day in an earlier year"? {@link EntryListService#onThisDay()} turns the answer into a query.
 *
 * <p>Kept apart from the query so the awkward part, 29 February, can be tested with a handful of dates and no
 * database, and so the one place that says how the strip treats leap days is a few lines long.
 */
final class OnThisDay {

    private OnThisDay() {}

    /**
     * What to look for: entries whose memory date is in {@code month}, on one of {@code days}, and before
     * {@code before} (the first day of this year, so only earlier years match). Every date the strip may show is in a
     * single month, because the only day that matches more than itself is 28 February, which also matches the 29th.
     */
    record Lookup(Month month, List<Integer> days, LocalDate before) {}

    // TODO(human) PRACTICE: Write lookupFor(today): the month and the day(s) of that month to look for, and the first
    //   day that is too late (the strip shows earlier years only, so today's own entries and anything dated this year
    //   or later must not match). The awkward day is 29 February: it only exists in leap years, so in a year that
    //   is not a leap year its entries have no day of their own to appear on. Decide which day they appear on, and
    //   make sure that rule does not also pull 28 February entries onto the 29th in a leap year, or 29 February
    //   entries onto 1 March.
    // Hint: D-32 says "in a year that isn't a leap year, entries from 29 February show on 28 February". Look at
    //   LocalDate.isLeapYear(), getMonth() and getDayOfMonth(), and LocalDate.withDayOfYear(1). The result is a list
    //   because on one day of the year (which?) two days of the month match. Never use LocalDate.of(year, 2, 29): it
    //   throws in a year that isn't a leap year.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    static Lookup lookupFor(LocalDate today) {
        boolean leapDayHasNoDayOfItsOwn =
                today.getMonth() == Month.FEBRUARY && today.getDayOfMonth() == 28 && !today.isLeapYear();
        List<Integer> days = leapDayHasNoDayOfItsOwn ? List.of(28, 29) : List.of(today.getDayOfMonth());
        return new Lookup(today.getMonth(), days, today.withDayOfYear(1));
    }
    // └── END ANSWER ▲ ──
}

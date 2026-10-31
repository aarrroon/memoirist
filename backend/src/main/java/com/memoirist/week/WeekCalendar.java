package com.memoirist.week;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Which week a day belongs to (D-08). A week runs Monday to Sunday for every user, and the day it starts on lives in
 * exactly one place, {@link #FIRST_DAY_OF_WEEK}, so changing it (it will not change) is a one-line edit.
 *
 * <p>Everything here works on calendar days. A memory date is already a {@link LocalDate} in the user's zone, so
 * the zone matters only where an instant has to become a day first ({@link #firstDayOf(Instant, ZoneId)}). Daylight
 * saving needs no special care: a {@code LocalDate} has no clock time to shift, and a 23-hour or 25-hour Sunday is
 * still one Sunday.
 */
public final class WeekCalendar {

    /** Every week starts on this day, for every user (D-08). The database checks it too (V1, {@code chk_weekly_logs_monday}). */
    public static final DayOfWeek FIRST_DAY_OF_WEEK = DayOfWeek.MONDAY;

    private WeekCalendar() {}

    /** The first day of the week that contains {@code date}: the same day if it is already the first day. */
    public static LocalDate firstDayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(FIRST_DAY_OF_WEEK));
    }

    /** The last day of the week that contains {@code date}, six days after the first. */
    public static LocalDate lastDayOf(LocalDate date) {
        return firstDayOf(date).plusDays(6);
    }

    /**
     * The first day of the week that contains this moment as seen in {@code zone}. The same instant can be Sunday in
     * one zone and Monday in another, so the zone decides the week: 13:30 UTC on a Sunday is already Monday in
     * Melbourne (UTC+11 in summer) and belongs to the next week there.
     */
    public static LocalDate firstDayOf(Instant moment, ZoneId zone) {
        return firstDayOf(moment.atZone(zone).toLocalDate());
    }
}

package com.memoirist;

import com.memoirist.entry.Entry;
import com.memoirist.era.Era;
import com.memoirist.media.Media;
import com.memoirist.tag.Tag;
import com.memoirist.user.User;
import com.memoirist.week.WeeklyLog;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Plain, unsaved test objects. Each factory gives the object a random id and the smallest valid set of fields; a test
 * sets whatever else it is about. Saving them is the test's job, through the repository it is testing.
 */
public final class Fixtures {

    /** The dev user that the {@code local} and {@code test} profiles seed (D-11). */
    public static final UUID DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** A Monday, so a {@link WeeklyLog} starting here satisfies the weekly_logs CHECK constraints. */
    public static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);

    private Fixtures() {}

    /** A new user with a unique email, so tests never collide on the UNIQUE constraint. */
    public static User user() {
        UUID id = UUID.randomUUID();
        return new User(id, "user-" + id + "@memoirist.test", "Test User", "Australia/Melbourne");
    }

    public static WeeklyLog week(UUID userId) {
        return week(userId, MONDAY);
    }

    public static WeeklyLog week(UUID userId, LocalDate monday) {
        return new WeeklyLog(UUID.randomUUID(), userId, monday);
    }

    /** An entry dated on the Monday of its week. */
    public static Entry entry(UUID userId, WeeklyLog week) {
        return new Entry(UUID.randomUUID(), userId, week.getId(), week.getStartDate());
    }

    /** An ongoing Era (no end date, not pinned) that starts on {@code start}. */
    public static Era era(UUID userId, LocalDate start) {
        return new Era(UUID.randomUUID(), userId, "Era from " + start, start);
    }

    /** An Era from {@code start} to {@code end}, both days included. */
    public static Era era(UUID userId, LocalDate start, LocalDate end) {
        Era era = era(userId, start);
        era.setEndDate(end);
        return era;
    }

    public static Tag tag(UUID userId, String name) {
        return new Tag(UUID.randomUUID(), userId, name);
    }

    /** A JPEG photo in position {@code order}, with a key that is unique to this call. */
    public static Media photo(Entry entry, int order) {
        UUID id = UUID.randomUUID();
        return new Media(
                id,
                entry,
                "users/test/entries/" + entry.getId() + "/" + id + ".jpg",
                "image/jpeg",
                Media.PURPOSE_PHOTO,
                order,
                2048);
    }
}

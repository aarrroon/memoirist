package com.memoirist.week;

import com.memoirist.era.Era;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * A week's Eras, worked out from dates and never stored (D-22). The caller loads the user's Eras once, and this picks
 * the ones that hold a week and puts them in the order the API shows.
 *
 * <p>It is plain Java with no database, so the boundary cases (an Era that ends on a Monday, one that starts on a
 * Sunday, one with no end) are unit tests. The same rule in SQL is
 * {@link WeeklyLogRepository#findOverlapping}, which finds the weeks of one Era; a test keeps the two in step.
 */
public final class WeekEras {

    private WeekEras() {}

    // TODO(human) PRACTICE: Write holds() and FEED_ORDER. An Era holds a week when its dates overlap the week's seven
    //   days: it starts on or before the week's Sunday, and has no end date or ends on or after the week's Monday.
    //   Check the edges: an Era that starts on the Sunday, one that ends on the Monday, one that ends the day before
    //   the Monday, and one with no end. FEED_ORDER sorts a week's Eras for the API: pinned Eras first, then the
    //   latest start.
    //   Careful with booleans: false sorts before true.
    // Hint: D-22. Both ends of an Era's dates are included. Comparator.comparing(keyExtractor, keyComparator) and
    //   thenComparing build the order; Era.LATEST_START_FIRST already breaks ties on the same start date.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    /** Pinned Eras first, then the latest start first, then as {@link Era#LATEST_START_FIRST} breaks ties. */
    public static final Comparator<Era> FEED_ORDER =
            Comparator.comparing(Era::isPinnedInFeed, Comparator.reverseOrder()).thenComparing(Era.LATEST_START_FIRST);

    /** Whether the Era's dates overlap the week, both ends included. */
    public static boolean holds(Era era, WeeklyLog week) {
        boolean startsByTheEndOfTheWeek = !era.getStartDate().isAfter(week.getEndDate());
        boolean endsAfterTheWeekBegins =
                era.getEndDate() == null || !era.getEndDate().isBefore(week.getStartDate());
        return startsByTheEndOfTheWeek && endsAfterTheWeekBegins;
    }
    // └── END ANSWER ▲ ──

    /**
     * The Eras among {@code eras} that hold the week, in {@link #FEED_ORDER}. Empty for a week in no Era, never
     * {@code null}. The list the API calls a week's {@code eras}.
     */
    public static List<Era> of(WeeklyLog week, Collection<Era> eras) {
        return eras.stream().filter(era -> holds(era, week)).sorted(FEED_ORDER).toList();
    }
}

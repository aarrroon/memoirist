package com.memoirist.week;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Files memory dates into weeks (D-08). There is no "create week" step for the user: the first entry that lands in
 * a Monday-to-Sunday span creates its week, and every later one finds it.
 *
 * <p>It joins the caller's transaction, so the week row is committed or rolled back together with the entry that
 * needed it.
 */
@Service
@Transactional
public class WeekService {

    private final WeeklyLogRepository weeks;

    public WeekService(WeeklyLogRepository weeks) {
        this.weeks = weeks;
    }

    /**
     * The user's week that contains {@code memoryDate}, created if it isn't there yet.
     *
     * <p>Two steps, as D-08 says: an {@code INSERT ... ON CONFLICT DO NOTHING}, then a select. The insert alone can't
     * return the row when somebody else already made it, and "select, and insert if missing" has a gap between the two
     * in which a second request (a photo dump sends several at once) inserts the same week and fails on the unique
     * key. With {@code ON CONFLICT} the database settles the race: the loser's insert waits for the winner to commit,
     * does nothing, and the select that follows finds the winner's row.
     */
    public WeeklyLog findOrCreate(UUID userId, LocalDate memoryDate) {
        LocalDate firstDay = WeekCalendar.firstDayOf(memoryDate);
        weeks.insertIfAbsent(UUID.randomUUID(), userId, firstDay, WeekCalendar.lastDayOf(memoryDate));
        return weeks.findByUserIdAndStartDate(userId, firstDay)
                .orElseThrow(() -> new IllegalStateException("The week was inserted but can't be read."));
    }
}

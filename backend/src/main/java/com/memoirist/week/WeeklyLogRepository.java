package com.memoirist.week;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Weeks, always looked up together with the owning user (security.md). */
public interface WeeklyLogRepository extends Repository<WeeklyLog, UUID> {

    <S extends WeeklyLog> S save(S week);

    Optional<WeeklyLog> findByIdAndUserId(UUID id, UUID userId);

    /** The user's week that starts on this Monday; backed by the unique constraint on (user_id, start_date). */
    Optional<WeeklyLog> findByUserIdAndStartDate(UUID userId, LocalDate startDate);

    /**
     * The user's weeks that an Era with these dates holds, newest first (D-22): the week ends on or after the Era's
     * first day, and begins on or before its last day, or the Era has no last day. Both ends count, so an Era that
     * ends on a Monday holds that week, and so does one that starts on a Sunday. {@link WeekEras#holds} says the same
     * thing for one week in Java; {@code EraWeekAgreementTest} checks that the two never disagree.
     *
     * <p>It lists the week rows that exist, which is a week for each Monday-to-Sunday span that ever had an entry.
     * Served by {@code uq_weekly_logs_user_start}.
     *
     * @param to the Era's last day, or {@code null} for an Era that is still going
     */
    @Query("""
            select w from WeeklyLog w
            where w.userId = :userId
              and w.endDate >= :from
              and (cast(:to as LocalDate) is null or w.startDate <= :to)
            order by w.startDate desc
            """)
    List<WeeklyLog> findOverlapping(
            @Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}

package com.memoirist.week;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
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
     * Inserts the week unless the user already has one that starts on {@code startDate}, and says nothing if so
     * (D-08). Native SQL, because JPA has no {@code ON CONFLICT}; {@code created_at} and {@code updated_at} take their
     * column defaults. {@code WeekService.findOrCreate} calls it and then selects the row.
     *
     * @return 1 if the week was inserted, 0 if it was already there
     */
    @Modifying
    @Query(value = """
                    INSERT INTO weekly_logs (id, user_id, start_date, end_date)
                    VALUES (:id, :userId, :startDate, :endDate)
                    ON CONFLICT (user_id, start_date) DO NOTHING
                    """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

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

    // TODO(human) PRACTICE: Write the query behind the feed: one page of the user's weeks that have at least one entry,
    //   newest week first, plus the query that counts them for totalItems. A week row is created for every week that
    //   ever had an entry, so a week whose only entries sit in Recently deleted must be left out, and a week whose
    //   entries are all sealed must stay in (a sealed entry is still a row in the feed).
    //   Think about what you would use to ask "has at least one": a join with DISTINCT, IN, or EXISTS.
    // Hint: D-08, D-10, D-30. Entry carries @SQLRestriction("deleted_at IS NULL"), which Hibernate adds to a JPQL
    //   subquery on Entry as well, so you don't write the deleted filter yourself. A paged @Query needs a countQuery
    //   when it isn't a plain select of the entity; write one that has the same where clause.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    /**
     * One page of the user's weeks that have at least one entry, newest first (the feed, D-08). {@code exists} stops at
     * the first entry it finds, so a week with fifty entries costs no more than a week with one, and, unlike a join, it
     * can't repeat a week once per entry. Entries in Recently deleted don't count, because {@code Entry}'s
     * {@code @SQLRestriction} is added to the subquery (D-30); sealed entries do (D-10), since the feed shows them as
     * locked rows. The entry's {@code userId} is checked as well as the week's: the two are always equal, and the extra
     * condition keeps this query user-scoped on its own terms (security.md).
     *
     * <p>The count is its own query: the page query has a {@code limit}, and the total is how many weeks match
     * without one. The weeks come back with no entries; the service loads those for the whole page in one query.
     */
    @Query(value = """
                    select w from WeeklyLog w
                    where w.userId = :userId
                      and exists (select 1 from Entry e where e.weeklyLogId = w.id and e.userId = :userId)
                    order by w.startDate desc
                    """, countQuery = """
                    select count(w) from WeeklyLog w
                    where w.userId = :userId
                      and exists (select 1 from Entry e where e.weeklyLogId = w.id and e.userId = :userId)
                    """)
    Page<WeeklyLog> findNonEmptyByUserId(@Param("userId") UUID userId, Pageable pageable);
    // └── END ANSWER ▲ ──
}

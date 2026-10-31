package com.memoirist.entry;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Entries, always looked up together with the owning user (security.md).
 *
 * <p>Every method that isn't marked "Recently deleted" leaves out deleted entries without saying so, because
 * {@link Entry} carries {@code @SQLRestriction}. Sealed entries are <em>not</em> left out here: the feed still lists
 * them as locked rows, and {@code EntryMapper} hides their content. A count or filter that must ignore sealed entries
 * says so in its own query (D-10).
 */
public interface EntryRepository extends Repository<Entry, UUID> {

    <S extends Entry> S save(S entry);

    /**
     * Saves and sends the INSERT or UPDATE now, instead of at commit. Hibernate fills {@code createdAt} and
     * {@code updatedAt} (and bumps {@code version}) while it writes the row, so a response built from the entity
     * before that would show them empty or old. A lost race on the version fails here too, inside the service, not
     * later at commit.
     */
    <S extends Entry> S saveAndFlush(S entry);

    void delete(Entry entry);

    Optional<Entry> findByIdAndUserId(UUID id, UUID userId);

    /**
     * The same lookup, and the entry's row stays locked until the transaction ends ({@code SELECT ... FOR NO KEY
     * UPDATE}); anyone else who asks for the lock waits. It is how the media calls (upload, reorder, delete) and the
     * delete of an entry take turns: two uploads never pick the same carousel position, and an entry is never erased
     * while a photo is being added to it.
     *
     * <p>The row is not changed, so {@code version} stays where it was: a photo upload must not make the caption's
     * {@code PUT} stale (D-23). Needs a running transaction; the lock is released at its end.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Entry e where e.id = :id and e.userId = :userId")
    Optional<Entry> findForUpdateByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Whether any entry has this id, <em>any user's, Recently deleted included</em>: native SQL, so Hibernate's
     * {@code @SQLRestriction} doesn't hide the deleted ones. The one lookup that isn't scoped to a user, and only for
     * the client supplied {@code id} of a create (D-12, D-30): the service asks it after the user-scoped lookup found
     * nothing, to tell {@code 409 conflict} (taken by someone else, or by the user's own deleted entry) from a fresh
     * id. Never use it to read an entry; the answer reveals nothing but "taken".
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM entries WHERE id = :id)", nativeQuery = true)
    boolean idIsTaken(@Param("id") UUID id);

    /** The user's entries among these ids, for loading the children of a page of weeks in one query. */
    List<Entry> findByIdInAndUserId(Collection<UUID> ids, UUID userId);

    /** A week's entries in feed order. Backed by {@code idx_entries_week_date}. */
    List<Entry> findByWeeklyLogIdAndUserIdOrderByEntryDateAscCreatedAtAsc(UUID weeklyLogId, UUID userId);

    /**
     * The entries of a page of weeks, all in one query, in feed order: by memory date, then by when they were written,
     * oldest first (API_CONTRACT, WeeklyLog). The caller splits the list by {@code weeklyLogId}; because the whole list
     * is sorted by date, the entries of any one week keep that order. Sealed entries are included, since the feed shows
     * them as locked rows and {@code EntryMapper} hides what is private (D-10); entries in Recently deleted are not
     * (D-30). Backed by {@code idx_entries_week_date}.
     *
     * <p>Pass a non-empty list: some databases refuse an empty {@code IN ()}.
     */
    List<Entry> findByWeeklyLogIdInAndUserIdOrderByEntryDateAscCreatedAtAsc(Collection<UUID> weeklyLogIds, UUID userId);

    /**
     * Fills in the {@code media} of the <em>open</em> entries of these weeks, all with one query, so a page of the feed
     * costs the same however many entries it holds. The entries are the ones the caller loaded a moment ago in the same
     * transaction: this query returns those same objects, and the {@code left join fetch} initialises their lazy
     * {@code media} lists on the way. Read the entries from the persistence context, not from the result; it is only
     * returned because a Spring Data query has to return something.
     *
     * <p>Sealed entries are left out in the query (D-10), by the rule {@code EntrySeal} states: an entry is open when it
     * has no unlock date or that date is not after {@code now}. Their media is never read, and the mapper never asks for
     * it. Pass the same {@code now} that goes to {@code EntryMapper}: an entry this query skips but the mapper treats as
     * open would still get its media, only through Hibernate's slower lazy batches.
     *
     * <p>Only one collection is fetched per query: fetch-joining two lists at once fails
     * ({@code MultipleBagFetchException}) and multiplies the rows. The price is that each entry row (with its caption)
     * comes once per photo. Media is ordered by {@code displayOrder} by the {@code @OrderBy} on the collection.
     *
     * <p>Pass a non-empty list: some databases refuse an empty {@code IN ()}.
     */
    @Query("""
            select e from Entry e left join fetch e.media
            where e.weeklyLogId in :weeklyLogIds
              and e.userId = :userId
              and (e.unlockDate is null or e.unlockDate <= :now)
            """)
    List<Entry> fetchMediaOfOpenEntries(
            @Param("weeklyLogIds") Collection<UUID> weeklyLogIds,
            @Param("userId") UUID userId,
            @Param("now") Instant now);

    /**
     * The same for the {@code tags} of the open entries: the second of the two queries that fill in the collections
     * a feed or week needs. See {@link #fetchMediaOfOpenEntries}.
     */
    @Query("""
            select e from Entry e left join fetch e.tags
            where e.weeklyLogId in :weeklyLogIds
              and e.userId = :userId
              and (e.unlockDate is null or e.unlockDate <= :now)
            """)
    List<Entry> fetchTagsOfOpenEntries(
            @Param("weeklyLogIds") Collection<UUID> weeklyLogIds,
            @Param("userId") UUID userId,
            @Param("now") Instant now);

    // The all-entries timeline (task 3.9, D-25). Both queries sort the same way: newest memory date first, then newest
    // written, then the id. The id is there only to make the order total: two entries made in the same instant on the
    // same day (the photo dump does this) would otherwise be free to swap places between one page request and the
    // next, and a client paging through would see one twice and miss another. idx_entries_user_date serves the date;
    // Postgres sorts the few rows of one day by created_at.

    /**
     * One page of the user's entries, sealed ones included as locked rows (D-10), newest first. Entries in Recently
     * deleted are left out by {@code @SQLRestriction} (D-30). The count is its own query because the page query has a
     * {@code limit}; Spring Data runs it only when the page is full.
     */
    @Query(value = """
                    select e from Entry e
                    where e.userId = :userId
                    order by e.entryDate desc, e.createdAt desc, e.id desc
                    """, countQuery = """
                    select count(e) from Entry e
                    where e.userId = :userId
                    """)
    Page<Entry> findTimelinePage(@Param("userId") UUID userId, Pageable pageable);

    // TODO(human) PRACTICE: Write the query behind the timeline's tag filter: one page of the user's entries that carry
    //   a given tag, newest first, plus its count query. A sealed Loot Drop must never come back, and must not be
    //   counted in totalItems either, because a count that moves when the user seals an entry would tell them what is
    //   under the seal. Decide where the sealed rule goes (the service after loading? the query?), how an entry that
    //   has just opened is treated, and whether a join to the tags can repeat an entry.
    // Hint: D-10 and security.md ("leave them out in the query itself"). EntrySeal says when an entry is open: no
    //   unlock date, or one that is not after now (an entry is open at the very instant). entry_tags has a primary key
    //   on (entry_id, tag_id), so one tag matches an entry at most once. Keep the same order as findTimelinePage, and
    //   pass the same `now` that goes to the mapper.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    /**
     * One page of the user's entries that carry this tag, <em>open entries only</em>, newest first (D-10): a sealed
     * Loot Drop is not listed and is not counted, so the total never moves because of a seal. An entry whose unlock
     * date is not after {@code now} is open and is included, the same rule as {@code EntrySeal}. The join to the tags
     * can't repeat an entry, because {@code entry_tags} has one row per (entry, tag). Entries in Recently deleted are
     * left out by {@code @SQLRestriction} (D-30). Served by {@code idx_entry_tags_tag}.
     */
    @Query(value = """
                    select e from Entry e join e.tags t
                    where e.userId = :userId
                      and t.id = :tagId
                      and (e.unlockDate is null or e.unlockDate <= :now)
                    order by e.entryDate desc, e.createdAt desc, e.id desc
                    """, countQuery = """
                    select count(e) from Entry e join e.tags t
                    where e.userId = :userId
                      and t.id = :tagId
                      and (e.unlockDate is null or e.unlockDate <= :now)
                    """)
    Page<Entry> findOpenTimelinePageByTag(
            @Param("userId") UUID userId, @Param("tagId") UUID tagId, @Param("now") Instant now, Pageable pageable);
    // └── END ANSWER ▲ ──

    /**
     * Fills in the {@code media} of the <em>open</em> entries among these ids, with one query: what a page of a list
     * needs for its photo and voice note counts. Like {@link #fetchMediaOfOpenEntries}, it returns the entries the caller
     * loaded a moment ago in the same transaction, with the lazy list initialised on the way; read the entries from the
     * persistence context, not from the result. Sealed entries are left out in the query (D-10), by the same rule and
     * the same {@code now} as the mapper. Pass a non-empty list.
     */
    @Query("""
            select e from Entry e left join fetch e.media
            where e.id in :entryIds
              and e.userId = :userId
              and (e.unlockDate is null or e.unlockDate <= :now)
            """)
    List<Entry> fetchMediaOfOpenEntriesByIds(
            @Param("entryIds") Collection<UUID> entryIds, @Param("userId") UUID userId, @Param("now") Instant now);

    /** The same for the {@code tags}. See {@link #fetchMediaOfOpenEntriesByIds}. */
    @Query("""
            select e from Entry e left join fetch e.tags
            where e.id in :entryIds
              and e.userId = :userId
              and (e.unlockDate is null or e.unlockDate <= :now)
            """)
    List<Entry> fetchTagsOfOpenEntriesByIds(
            @Param("entryIds") Collection<UUID> entryIds, @Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * The Opened strip (task 3.10, D-10): the user's Loot Drops that have unlocked and that they haven't marked seen,
     * the one that opened most recently first. Every entry returned is open by {@code EntrySeal}'s rule (its unlock
     * date is not after {@code now}), so the mapper shows it whole; a sealed one can't match, and neither can an entry
     * that was never sealed (no unlock date). Entries in Recently deleted are left out by {@code @SQLRestriction}
     * (D-30). The id is the last sort key only to make the order total when two Loot Drops open in the same instant.
     *
     * <p>Spring Data turns {@link Limit} into {@code LIMIT}, so only the strip's few rows are read, however many seen
     * Loot Drops the user has. The predicate is the partial index's own: {@code idx_entries_opened_unseen} holds
     * exactly the entries with an unlock date and no seen mark, ordered by user and unlock date, so Postgres reads it
     * backwards and stops after {@code limit} rows.
     */
    @Query("""
            select e from Entry e
            where e.userId = :userId
              and e.unlockDate is not null
              and e.unlockDate <= :now
              and e.unlockSeenAt is null
            order by e.unlockDate desc, e.id desc
            """)
    List<Entry> findOpenedUnseen(@Param("userId") UUID userId, @Param("now") Instant now, Limit limit);

    // TODO(human) PRACTICE: Write the update behind mark-seen as one conditional UPDATE statement, instead of
    //   loading the entry, calling a setter and saving it. It must change only the user's own entry, only once it has
    //   opened and only if it hasn't been marked yet, and it must say how many rows it changed. Then decide what the
    //   statement must NOT touch (think about `version` and `updatedAt`), and why running it twice is harmless.
    // Hint: D-10, D-23 and security.md (Ownership). `unlock_seen_at` is view state, not writing, so a client that
    //   marks an entry seen and then saves a caption it read earlier must not be told its copy is stale. A JPQL
    //   bulk update goes straight to SQL and skips the entity's @Version. Put every rule in the WHERE clause, so
    //   the check and the write are one atomic step, and let the service read the returned count if it needs it.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    /**
     * Records that the user has seen an opened Loot Drop, with one conditional UPDATE: it writes only where the entry
     * is the user's, has an unlock date that is not after {@code now}, and has no seen mark yet. It returns the number
     * of rows changed, {@code 1} the first time and {@code 0} on every repeat, so a second tap keeps the first seen
     * time and a re-sealed or foreign entry is untouched.
     *
     * <p>A bulk update leaves {@code version} and {@code updated_at} as they were: the mark is not an edit (D-23), so
     * it can't make a caption's {@code PUT} stale. It also needs no read-then-write, so there is nothing to race with
     * a seal that lands in between (the seal moves the unlock date into the future, and this condition then fails).
     * The persistence context is cleared afterwards, because an entity loaded earlier in the transaction would still
     * hold the old mark.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Entry e set e.unlockSeenAt = :now
            where e.id = :id
              and e.userId = :userId
              and e.unlockDate is not null
              and e.unlockDate <= :now
              and e.unlockSeenAt is null
            """)
    int markSeen(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);
    // └── END ANSWER ▲ ──

    /**
     * The On this day strip (task 3.12, D-32): the user's entries dated in {@code month} on one of {@code days} and
     * before {@code before} (the first day of this year), newest first. {@link OnThisDay} works out the three values
     * from the user's today, including the 28 and 29 February rule, so this query only matches dates.
     *
     * <p><b>Open entries only (D-10).</b> A sealed Loot Drop is not listed and not returned at all, not even as a locked
     * row: the strip must not hint that one is waiting. An entry is open when it has no unlock date or the date is not
     * after {@code now}, the rule of {@code EntrySeal}; pass the same {@code now} that goes to the mapper. Entries in
     * Recently deleted are left out by {@code @SQLRestriction} (D-30).
     *
     * <p>The order is the timeline's, {@code entry_date} then {@code created_at} then the id (a total order, so entries
     * from one day never swap places between two requests). Because every row has the same month and day, dates sort
     * by year, so this is "newest year first". {@link Limit} becomes {@code fetch first ? rows only}. The month and day
     * are tested on each row {@code idx_entries_user_date} hands back in date order; there is no index on them.
     */
    @Query("""
            select e from Entry e
            where e.userId = :userId
              and extract(month from e.entryDate) = :month
              and extract(day from e.entryDate) in :days
              and e.entryDate < :before
              and (e.unlockDate is null or e.unlockDate <= :now)
            order by e.entryDate desc, e.createdAt desc, e.id desc
            """)
    List<Entry> findOnThisDay(
            @Param("userId") UUID userId,
            @Param("month") int month,
            @Param("days") Collection<Integer> days,
            @Param("before") LocalDate before,
            @Param("now") Instant now,
            Limit limit);

    /**
     * How many entries each week has among the entries whose memory date is on or after {@code from} and, when
     * {@code to} is given, on or before it: an Era's dates (D-22). Both ends count. A week with none isn't returned.
     *
     * <p>Leaves out sealed entries in the query itself (D-10): an entry counts only if it has no unlock date or that
     * date is not after {@code now}, so an opened Loot Drop counts and the moment it opens is the moment it starts
     * to. Entries in Recently deleted are left out by {@code @SQLRestriction} (D-30). Served by
     * {@code idx_entries_user_date}.
     *
     * @param to the last day, or {@code null} for no end (an ongoing Era)
     */
    @Query("""
            select new com.memoirist.entry.EntryCountByWeek(e.weeklyLogId, count(e))
            from Entry e
            where e.userId = :userId
              and e.entryDate >= :from
              and (cast(:to as LocalDate) is null or e.entryDate <= :to)
              and (e.unlockDate is null or e.unlockDate <= :now)
            group by e.weeklyLogId
            """)
    List<EntryCountByWeek> countOpenEntriesByWeek(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("now") Instant now);

    /**
     * How many of the user's entries carry each tag: the {@code entryCount} of {@code GET /tags}. A tag that no entry
     * carries isn't returned, so the caller treats a missing tag as {@code 0}.
     *
     * <p>Leaves out sealed entries in the query itself (D-10), by the same rule as {@link #countOpenEntriesByWeek}: an
     * entry counts only if it has no unlock date or that date is not after {@code now}. Entries in Recently deleted are
     * left out by {@code @SQLRestriction} (D-30). Served by {@code idx_entry_tags_tag} and the entry's primary key.
     */
    @Query("""
            select new com.memoirist.entry.EntryCountByTag(t.id, count(e))
            from Entry e join e.tags t
            where e.userId = :userId
              and (e.unlockDate is null or e.unlockDate <= :now)
            group by t.id
            """)
    List<EntryCountByTag> countOpenEntriesByTag(@Param("userId") UUID userId, @Param("now") Instant now);

    /** The same count for one tag, for the answer of a create or a rename. See {@link #countOpenEntriesByTag}. */
    @Query("""
            select count(e)
            from Entry e join e.tags t
            where e.userId = :userId
              and t.id = :tagId
              and (e.unlockDate is null or e.unlockDate <= :now)
            """)
    long countOpenEntriesWithTag(@Param("userId") UUID userId, @Param("tagId") UUID tagId, @Param("now") Instant now);

    // Recently deleted (D-30, task 3.11). Native SQL, because Hibernate adds the @SQLRestriction to JPQL and derived
    // queries but never rewrites native ones. These five are the only reads of deleted entries, and the partial index
    // idx_entries_deleted (user_id, deleted_at WHERE deleted_at IS NOT NULL) holds exactly the rows they look at.
    // Every one of them says "deleted_at IS NOT NULL" itself: a native query for a live entry would find deleted ones.

    /**
     * One entry in Recently deleted, unlocked: a plain read. A live entry, or another user's, is not found. Restore and
     * erase use {@link #findDeletedForUpdateByIdAndUserId} instead.
     */
    @Query(
            value = "SELECT * FROM entries WHERE id = :id AND user_id = :userId AND deleted_at IS NOT NULL",
            nativeQuery = true)
    Optional<Entry> findDeletedByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * The same entry, and its row stays locked until the transaction ends ({@code FOR NO KEY UPDATE}). Restore and
     * erase take it so two requests for one entry take turns: whichever comes second waits, and when the first has
     * committed Postgres re-checks {@code deleted_at IS NOT NULL} on the new version of the row. A restored entry then
     * no longer matches and an erased one is gone, so the second request finds nothing and answers {@code 404}. Without
     * the lock a restore and an erase that overlap would both succeed. Needs a running transaction.
     */
    @Query(value = """
                    SELECT * FROM entries
                    WHERE id = :id AND user_id = :userId AND deleted_at IS NOT NULL
                    FOR NO KEY UPDATE
                    """, nativeQuery = true)
    Optional<Entry> findDeletedForUpdateByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * One page of the user's Recently deleted list, the entry deleted most recently first. The id is the last sort key
     * so the order is total: entries deleted in the same instant (a clock that ticks in whole seconds, or two taps on a
     * fast phone) never swap places between one page request and the next. Hibernate adds {@code LIMIT}/{@code OFFSET}
     * for the page; the count is its own query, run by Spring Data only when the page is full.
     */
    @Query(
            value = """
                    SELECT * FROM entries
                    WHERE user_id = :userId AND deleted_at IS NOT NULL
                    ORDER BY deleted_at DESC, id DESC
                    """,
            countQuery = "SELECT count(*) FROM entries WHERE user_id = :userId AND deleted_at IS NOT NULL",
            nativeQuery = true)
    Page<Entry> findDeletedPageByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * The ids of every entry, <em>of any user</em>, that has been in Recently deleted for more than 30 days by the
     * {@code cutoff} (that is, deleted before it), the one that has waited longest first. The sweep's list of work
     * (D-30); the only query here with no user, because the sweep has no request and no current user.
     */
    @Query(
            value = "SELECT id FROM entries WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff"
                    + " ORDER BY deleted_at, id",
            nativeQuery = true)
    List<UUID> findDeletedIdsBefore(@Param("cutoff") Instant cutoff);

    /**
     * One entry that is <em>still</em> due for the sweep, locked and not waited for: the row is returned only if it is
     * in Recently deleted, was deleted before the {@code cutoff}, and no other transaction holds it
     * ({@code FOR NO KEY UPDATE SKIP LOCKED}). The sweep read its list of ids a moment ago without a lock, so
     * each of these things may have changed since:
     * <ul>
     *   <li>the user restored the entry: {@code deleted_at} is null now, and the row is not returned;
     *   <li>the user erased it, or another sweep did: the row is gone;
     *   <li>a restore or an erase is running at this moment: the row is locked, it is skipped instead of waited for, and
     *       whichever way that request ends there is nothing left for the sweep to do.
     * </ul>
     * Needs a running transaction; the lock ends with it.
     */
    @Query(value = """
                    SELECT * FROM entries
                    WHERE id = :id AND deleted_at IS NOT NULL AND deleted_at < :cutoff
                    FOR NO KEY UPDATE SKIP LOCKED
                    """, nativeQuery = true)
    Optional<Entry> findDeletedForSweep(@Param("id") UUID id, @Param("cutoff") Instant cutoff);
}

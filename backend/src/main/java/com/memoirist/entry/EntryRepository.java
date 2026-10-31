package com.memoirist.entry;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
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

    // Recently deleted (D-30). Native SQL, because Hibernate adds the @SQLRestriction to JPQL and derived queries but
    // never rewrites native ones. These two, restore/erase and the daily sweep are the only reads of deleted entries,
    // and they are served by idx_entries_deleted.

    /** One entry in Recently deleted, for restore and erase. A live entry, or another user's, is not found. */
    @Query(
            value = "SELECT * FROM entries WHERE id = :id AND user_id = :userId AND deleted_at IS NOT NULL",
            nativeQuery = true)
    Optional<Entry> findDeletedByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /** The user's Recently deleted list, most recently deleted first. */
    @Query(
            value = "SELECT * FROM entries WHERE user_id = :userId AND deleted_at IS NOT NULL ORDER BY deleted_at DESC",
            nativeQuery = true)
    List<Entry> findDeletedByUserIdOrderByDeletedAtDesc(@Param("userId") UUID userId);
}

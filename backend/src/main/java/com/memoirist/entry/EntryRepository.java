package com.memoirist.entry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    void delete(Entry entry);

    Optional<Entry> findByIdAndUserId(UUID id, UUID userId);

    /** The user's entries among these ids, for loading the children of a page of weeks in one query. */
    List<Entry> findByIdInAndUserId(Collection<UUID> ids, UUID userId);

    /** A week's entries in feed order. Backed by {@code idx_entries_week_date}. */
    List<Entry> findByWeeklyLogIdAndUserIdOrderByEntryDateAscCreatedAtAsc(UUID weeklyLogId, UUID userId);

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

package com.memoirist.media;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Media is owned through its entry, so every lookup goes through {@code entry.userId}. That join also applies the
 * entry's Recently deleted filter (D-30): the media of a deleted entry is not found, so nothing presigns it.
 *
 * <p>These finders do not check the seal (D-10): they return the media of a sealed Loot Drop. The service must answer
 * 409 on writes and must not presign a sealed entry's media on reads.
 */
public interface MediaRepository extends Repository<Media, UUID> {

    <S extends Media> S save(S media);

    /**
     * Saves and sends the INSERT now, instead of at commit. Hibernate fills {@code uploadedAt} while it writes the row,
     * so a response built from the entity before that would show it empty.
     */
    <S extends Media> S saveAndFlush(S media);

    void delete(Media media);

    Optional<Media> findByIdAndEntryUserId(UUID id, UUID userId);

    /**
     * The same lookup with the media row locked <em>for sharing</em> until the transaction ends, and its entry loaded
     * with it. It is how an Era checks its cover (D-36): any number of Eras can look at the same photo at once, but
     * deleting that photo, or erasing its entry, waits until the Era has been written. Without the lock the delete
     * could land between the check and the insert and the foreign key would fail with a {@code 500}.
     *
     * <p>Needs a running transaction. The entry's Recently deleted filter applies to the join, so the media of a
     * deleted entry is not found (D-30).
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select m from Media m join fetch m.entry e
            where m.id = :id and e.userId = :userId
            """)
    Optional<Media> findAndLockByIdAndEntryUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Several of the user's media items at once, each with its entry loaded (one query, whatever the number): the
     * covers of a list of Eras. Those whose entry is in Recently deleted, or is someone else's, are not in the answer.
     * Like every finder here it does not check the seal; the caller decides what a sealed entry may show.
     */
    @Query("""
            select m from Media m join fetch m.entry e
            where m.id in :ids and e.userId = :userId
            """)
    List<Media> findAllWithEntryByIdInAndUserId(@Param("ids") Collection<UUID> ids, @Param("userId") UUID userId);

    List<Media> findByEntryIdAndEntryUserIdOrderByDisplayOrderAsc(UUID entryId, UUID userId);

    /**
     * The object keys of an entry's media, in carousel order, and nothing else. Used to erase an entry (D-30), where
     * loading the {@link Media} entities themselves would do harm: see {@code EntryErasure}.
     *
     * <p><b>Native SQL on purpose.</b> The same query in JPQL ({@code where m.entry.id = :entryId}) still joins
     * {@code entries} and adds its {@code deleted_at IS NULL} (Hibernate 7 applies {@code @SQLRestriction} to the join),
     * so for an entry in Recently deleted it found no keys at all, and an erase deleted the rows and left every file
     * behind without any error. Hibernate never rewrites a native query. Like the Recently deleted lookups in
     * {@code EntryRepository}, this one reads rows that the entry's restriction would hide.
     */
    @Query(value = "SELECT object_key FROM media WHERE entry_id = :entryId ORDER BY display_order", nativeQuery = true)
    List<String> findObjectKeysByEntryId(@Param("entryId") UUID entryId);

    /** How many photos and voice notes the entry has. The caller has already checked that the entry is the user's. */
    long countByEntryId(UUID entryId);
}

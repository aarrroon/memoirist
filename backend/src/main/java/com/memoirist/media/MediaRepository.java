package com.memoirist.media;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

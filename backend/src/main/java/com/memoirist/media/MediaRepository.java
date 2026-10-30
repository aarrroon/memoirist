package com.memoirist.media;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Media is owned through its entry, so every lookup goes through {@code entry.userId}. That join also applies the
 * entry's Recently deleted filter (D-30): the media of a deleted entry is not found, so nothing presigns it.
 *
 * <p>These finders do not check the seal (D-10): they return the media of a sealed Loot Drop. The service must answer
 * 409 on writes and must not presign a sealed entry's media on reads.
 */
public interface MediaRepository extends Repository<Media, UUID> {

    <S extends Media> S save(S media);

    void delete(Media media);

    Optional<Media> findByIdAndEntryUserId(UUID id, UUID userId);

    List<Media> findByEntryIdAndEntryUserIdOrderByDisplayOrderAsc(UUID entryId, UUID userId);
}

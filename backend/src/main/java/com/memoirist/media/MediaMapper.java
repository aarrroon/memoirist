package com.memoirist.media;

import com.memoirist.media.dto.MediaResponse;
import com.memoirist.storage.StorageService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The only place a {@link Media} row becomes a {@link MediaResponse}, and so the only place that signs a file URL
 * (non-negotiable 5: the database holds object keys, the API shows presigned URLs).
 *
 * <p>It does not know about the seal. <b>The caller decides whether the user may see this media</b>: {@code EntryMapper}
 * never calls it for a sealed entry (D-10), and {@code MediaService} refuses a sealed entry before it maps anything.
 * {@code EntryMapperIsTheOnlyPathTest} fails if another class builds a {@code MediaResponse}.
 *
 * <p>It reads nothing lazy: the entry's id is passed in, so mapping a row costs no query.
 */
@Component
public class MediaMapper {

    private final StorageService storage;

    public MediaMapper(StorageService storage) {
        this.storage = storage;
    }

    public MediaResponse toResponse(UUID entryId, Media media) {
        return new MediaResponse(
                media.getId(),
                entryId,
                storage.presignGet(media.getObjectKey()),
                media.getMediaType(),
                media.getPurpose(),
                media.getDisplayOrder(),
                media.getFileSizeBytes(),
                media.getUploadedAt());
    }
}

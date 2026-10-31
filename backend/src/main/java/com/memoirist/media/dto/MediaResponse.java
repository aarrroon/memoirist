package com.memoirist.media.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One photo, diary-page photo or voice note as the API shows it (API_CONTRACT, Media).
 *
 * @param fileUrl a presigned GET URL, valid for 60 minutes; never the object key or the storage host on its own
 * @param purpose {@code PHOTO}, or {@code PAGE_SCAN} for a photo of a paper diary page (D-20)
 */
public record MediaResponse(
        UUID id,
        UUID entryId,
        String fileUrl,
        String mediaType,
        String purpose,
        int displayOrder,
        long fileSizeBytes,
        Instant uploadedAt) {}

package com.memoirist.media.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One photo, diary-page photo or voice note as the API shows it (API_CONTRACT, Media).
 *
 * @param fileUrl a presigned GET URL, valid for 60 minutes; never the object key or the storage host on its own
 * @param purpose {@code PHOTO}, or {@code PAGE_SCAN} for a photo of a paper diary page (D-20)
 * @param width the picture's width in pixels, read from its header at upload (D-17); {@code null} for a voice note, an
 *     image whose header couldn't be read, and anything uploaded before task 4.5
 * @param height the picture's height in pixels; {@code null} exactly when {@code width} is
 * @param thumbnailUrl a presigned GET URL of the thumbnail, 480 px on the longest side (D-43), for every small picture;
 *     {@code null} when there is none (a voice note, a photo no bigger than a thumbnail, one uploaded before task 4.18)
 * @param screenUrl a presigned GET URL of the screen copy, 1600 px on the longest side (D-43), for the feed's swipe and an
 *     Era page's cover; {@code null} as for the thumbnail
 */
public record MediaResponse(
        UUID id,
        UUID entryId,
        String fileUrl,
        String mediaType,
        String purpose,
        int displayOrder,
        long fileSizeBytes,
        Instant uploadedAt,
        @Schema(nullable = true) Integer width,
        @Schema(nullable = true) Integer height,
        @Schema(nullable = true) String thumbnailUrl,
        @Schema(nullable = true) String screenUrl) {}

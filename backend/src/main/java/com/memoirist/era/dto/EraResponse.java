package com.memoirist.era.dto;

import com.memoirist.media.dto.MediaResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An Era as the API shows it (API_CONTRACT, Eras).
 *
 * @param description {@code null} when the Era has none
 * @param soundtrack a Spotify URI (D-35), or {@code null}
 * @param endDate {@code null} when the Era is ongoing
 * @param cover the cover photo with a presigned URL (D-36), or {@code null} when there is none or its entry is
 *     sealed or in Recently deleted
 */
public record EraResponse(
        UUID id,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        String soundtrack,
        MediaResponse cover,
        boolean pinnedInFeed,
        Instant createdAt,
        Instant updatedAt) {}

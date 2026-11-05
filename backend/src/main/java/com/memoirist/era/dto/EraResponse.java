package com.memoirist.era.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An Era as the API shows it (API_CONTRACT, Eras). {@code cover} (task 3.15) is not in it yet.
 *
 * @param description {@code null} when the Era has none
 * @param soundtrack a Spotify URI (D-35), or {@code null}
 * @param endDate {@code null} when the Era is ongoing
 */
public record EraResponse(
        UUID id,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        String soundtrack,
        boolean pinnedInFeed,
        Instant createdAt,
        Instant updatedAt) {}

package com.memoirist.era.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An Era as the API shows it (API_CONTRACT, Eras). {@code soundtrack} (task 3.14) and {@code cover} (task 3.15) are
 * not in it yet.
 *
 * @param description {@code null} when the Era has none
 * @param endDate {@code null} when the Era is ongoing
 */
public record EraResponse(
        UUID id,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean pinnedInFeed,
        Instant createdAt,
        Instant updatedAt) {}

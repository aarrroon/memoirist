package com.memoirist.era.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/eras/{eraId}}: the Era's own fields plus the weeks its dates overlap, newest first. A record
 * can't extend another, so the fields repeat {@link EraResponse}'s.
 */
public record EraDetailResponse(
        UUID id,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean pinnedInFeed,
        Instant createdAt,
        Instant updatedAt,
        List<EraWeekResponse> weeklyLogs) {}

package com.memoirist.era.dto;

import com.memoirist.media.dto.MediaResponse;
import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(nullable = true) String description,
        LocalDate startDate,
        @Schema(nullable = true) LocalDate endDate,
        @Schema(nullable = true) String soundtrack,
        @Schema(nullable = true) MediaResponse cover,
        boolean pinnedInFeed,
        Instant createdAt,
        Instant updatedAt,
        List<EraWeekResponse> weeklyLogs) {}

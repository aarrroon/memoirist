package com.memoirist.week.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.memoirist.entry.dto.EntryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A week as the feed and week detail show it (API_CONTRACT, WeeklyLog).
 *
 * @param title {@code null} until the user names the week
 * @param closed the JSON name is {@code isClosed}
 * @param soundtrack a Spotify URI (D-35), or {@code null}
 * @param eras every Era whose dates overlap the week: pinned first, then the latest start (D-22). Empty for a week in
 *     no Era, never {@code null}
 * @param entries the week's entries, oldest memory date first, then oldest written first, so the week reads as a
 *     story. A sealed Loot Drop is in the list as a locked row, redacted by {@code EntryMapper} (D-10); entries in
 *     Recently deleted are not in it (D-30)
 */
public record WeeklyLogResponse(
        UUID id,
        @Schema(nullable = true) String title,
        LocalDate startDate,
        LocalDate endDate,
        @JsonProperty("isClosed") boolean closed,
        @Schema(nullable = true) String soundtrack,
        List<WeekEraResponse> eras,
        List<EntryResponse> entries) {}

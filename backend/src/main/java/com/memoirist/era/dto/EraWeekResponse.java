package com.memoirist.era.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One week an Era holds, in {@code GET /api/v1/eras/{eraId}} (API_CONTRACT, Eras).
 *
 * @param title {@code null} until the user names the week
 * @param closed the JSON name is {@code isClosed}
 * @param entryCount the week's entries that are inside the Era's dates, not sealed and not in Recently deleted
 */
public record EraWeekResponse(
        UUID id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        @JsonProperty("isClosed") boolean closed,
        long entryCount) {}

package com.memoirist.week.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One of a week's Eras, as listed in {@link WeeklyLogResponse#eras()}: enough for a chip or a section heading, not the
 * whole Era (API_CONTRACT, WeeklyLog).
 *
 * @param endDate {@code null} when the Era is ongoing
 * @param pinnedInFeed whether the user pinned it to win in the feed (D-22)
 */
public record WeekEraResponse(UUID id, String title, LocalDate startDate, LocalDate endDate, boolean pinnedInFeed) {}

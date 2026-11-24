package com.memoirist.entry.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * The body of {@code POST /api/v1/entries/{entryId}/seal} (API_CONTRACT, Entries). The client turns the day the user
 * picked into 09:00 in their time zone (D-23), so the server only sees the instant.
 *
 * <p>Whether it is far enough ahead, and whether an already sealed entry may move to it, depends on the clock and on
 * the entry, so {@code EntryService} checks that.
 *
 * @param unlockDate when the Loot Drop opens
 */
public record EntrySealRequest(@NotNull Instant unlockDate) {}

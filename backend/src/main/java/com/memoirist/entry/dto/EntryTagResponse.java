package com.memoirist.entry.dto;

import java.util.UUID;

/**
 * A tag as it appears on an {@code Entry} (API_CONTRACT, Entries): not the {@code Tag} of the tag endpoints, which
 * has an {@code entryCount} and no {@code source}.
 *
 * @param source {@code USER}, or from Phase 4 {@code AI} for an accepted suggestion (D-26)
 */
public record EntryTagResponse(UUID id, String name, String source) {}

package com.memoirist.entry;

import java.util.UUID;

/** How many entries carry a tag, as {@link EntryRepository#countOpenEntriesByTag} counts them. */
public record EntryCountByTag(UUID tagId, long entryCount) {}

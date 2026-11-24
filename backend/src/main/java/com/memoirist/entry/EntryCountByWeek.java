package com.memoirist.entry;

import java.util.UUID;

/** How many entries a week has, as {@link EntryRepository#countOpenEntriesByWeek} counts them. */
public record EntryCountByWeek(UUID weeklyLogId, long entryCount) {}

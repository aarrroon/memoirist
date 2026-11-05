package com.memoirist.tag.dto;

import java.util.UUID;

/**
 * A tag as the tag endpoints show it (API_CONTRACT, Tags). There is no {@code userId}: whose it is is never sent. An
 * {@code Entry} lists its tags as {@code EntryTagResponse} instead, which has a {@code source} and no count.
 *
 * @param entryCount how many of the user's entries carry the tag, leaving out sealed entries (D-10) and entries in
 *     Recently deleted (D-30)
 */
public record TagResponse(UUID id, String name, long entryCount) {}

package com.memoirist.entry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.memoirist.media.MediaKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One row of an entry list (API_CONTRACT, EntrySummary): the all-entries timeline, the Opened strip and Recently deleted,
 * and later On this day. No caption and no media URLs. <b>Only {@code EntryMapper} builds one</b>, for the same reason
 * as {@link EntryResponse}: it is the class that hides everything private about a sealed Loot Drop (D-10), and
 * {@code EntryMapperIsTheOnlyPathTest} fails if another source file builds it.
 *
 * <p>For a sealed entry {@code title}, {@code previewLine}, {@code photoCount}, {@code voiceNoteCount} and
 * {@code firstMediaKind} are {@code null} and {@code tags} is empty; the id, week, memory date, creation time, unlock
 * date, {@code dateUnknown} and {@code deletedAt} are not private and stay.
 *
 * @param previewLine the first line of the caption that isn't a heading, or the first heading if there is nothing else
 *     (D-24), at most 120 characters
 * @param photoCount every image, diary pages included
 * @param firstMediaKind the kind of the first media item, or {@code null} when there is none
 * @param locked JSON name {@code isLocked}
 * @param deletedAt set only in the Recently deleted list (task 3.11), {@code null} everywhere else
 */
public record EntrySummaryResponse(
        UUID id,
        UUID weeklyLogId,
        LocalDate entryDate,
        String title,
        String previewLine,
        Integer photoCount,
        Integer voiceNoteCount,
        MediaKind firstMediaKind,
        List<EntryTagResponse> tags,
        Instant createdAt,
        Instant unlockDate,
        @JsonProperty("isLocked") boolean locked,
        boolean dateUnknown,
        Instant deletedAt) {}

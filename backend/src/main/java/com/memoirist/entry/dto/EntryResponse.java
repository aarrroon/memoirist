package com.memoirist.entry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.memoirist.media.dto.MediaResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * An entry as the API shows it (API_CONTRACT, Entry). <b>Only {@code EntryMapper} builds one.</b> It is the class that
 * hides everything private about a sealed Loot Drop (D-10), and a second place that built this record would be a
 * second place that could forget to; a test fails if another source file does.
 *
 * <p>For a sealed entry {@code templateId}, {@code title}, {@code contentJson}, {@code contentText}, {@code soundtrack}
 * and {@code replyToEntryId} are {@code null}, {@code tags}, {@code replyIds} and {@code media} are empty, and
 * {@code isLocked} is {@code true}. The id, week, memory date, timestamps, unlock date, {@code dateUnknown} and
 * {@code version} are not private and stay.
 *
 * @param locked JSON name {@code isLocked}
 * @param replyIds empty until task 3.13 can record a reply
 * @param media in carousel order; each {@code fileUrl} is presigned for this response
 */
public record EntryResponse(
        UUID id,
        UUID weeklyLogId,
        LocalDate entryDate,
        UUID templateId,
        String title,
        JsonNode contentJson,
        String contentText,
        List<EntryTagResponse> tags,
        String soundtrack,
        UUID replyToEntryId,
        List<UUID> replyIds,
        Instant createdAt,
        Instant updatedAt,
        Instant unlockDate,
        @JsonProperty("isLocked") boolean locked,
        boolean dateUnknown,
        long version,
        List<MediaResponse> media) {}

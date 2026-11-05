package com.memoirist.entry.dto;

import com.memoirist.entry.Entry;
import com.memoirist.music.SpotifyUri;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * The body of {@code PUT /api/v1/entries/{entryId}} (API_CONTRACT, Entries): a full replace of what the user can
 * edit. A {@code templateId}, {@code title}, {@code contentJson}, {@code soundtrack} or {@code replyToEntryId} that is
 * left out is cleared; {@code version} and {@code entryDate} are required.
 *
 * <p>{@code version} is the one the client last read (D-23). The service compares it with the stored one and never
 * copies it onto the entry. {@code dateUnknown} is not a field: the server clears it when the date changes and no
 * client sets it on an update (D-34).
 *
 * <p>{@code tagIds} is required, unlike the other optional fields, because a full replace that forgot it would quietly
 * strip every tag the entry has. {@code []} is how a client says "no tags" (the same reason {@code subheadings} is
 * required on a template).
 *
 * @param version the {@code version} of the entry as the client last read it
 * @param entryDate the memory date
 * @param templateId one of the user's own templates, or {@code null}
 * @param title 1 to {@value Entry#MAX_TITLE_LENGTH} characters once stripped, or {@code null} for no title
 * @param contentJson a TipTap document, or {@code null} for no caption
 * @param tagIds the user's own tags (D-26): the whole set the entry will have, at most {@value Entry#MAX_TAGS} items as
 *     sent. A {@code null} item is reported by its position, such as {@code tagIds[1]}
 * @param soundtrack a Spotify track, album or playlist URI (D-35), or {@code null} for none; anything else is
 *     {@code 400} ({@code fieldErrors.soundtrack})
 * @param replyToEntryId the user's own opened Loot Drop this entry answers (D-33), or {@code null} for none. Checked
 *     only when it changes, so an entry can still be edited after its target has been sealed again
 */
public record EntryUpdateRequest(
        @NotNull @PositiveOrZero Long version,

        @NotNull LocalDate entryDate,

        UUID templateId,

        @Size(min = 1, max = Entry.MAX_TITLE_LENGTH) String title,

        @Schema(nullable = true) JsonNode contentJson,

        @NotNull @Size(max = Entry.MAX_TAGS) List<@NotNull UUID> tagIds,

        @SpotifyUri String soundtrack,

        UUID replyToEntryId) {

    public EntryUpdateRequest {
        title = EntryText.title(title);
        contentJson = EntryText.content(contentJson);
    }
}

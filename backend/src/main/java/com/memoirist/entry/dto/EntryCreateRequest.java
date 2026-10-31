package com.memoirist.entry.dto;

import com.memoirist.entry.Entry;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * The body of {@code POST /api/v1/entries} (API_CONTRACT, Entries). Every field is optional: an entry may be created
 * empty, because its photos are uploaded afterwards (D-23).
 *
 * <p>{@code id} is the client's own UUID, so a retry of the same request finds the entry it already made (D-12).
 * {@code entryDate}, the date range and the content rules are checked in {@code EntryService}, because they need the
 * user's time zone or the whole document, and so are the tags, which have to be the user's own. {@code soundtrack} and
 * {@code replyToEntryId} arrive with tasks 3.14 and 3.13; until then a body that names them is read and the fields are
 * ignored, like any other unknown field. So are {@code userId}, {@code unlockDate}, {@code contentText} and
 * {@code version}: the server decides those.
 *
 * @param entryDate the memory date; today in the user's zone when left out
 * @param templateId one of the user's own templates, or {@code null}
 * @param title 1 to {@value Entry#MAX_TITLE_LENGTH} characters once stripped, or {@code null} for no title
 * @param contentJson a TipTap document, or {@code null} (also when the field is missing) for no caption
 * @param tagIds the user's own tags (D-26), at most {@value Entry#MAX_TAGS} items as sent; missing, {@code null} and
 *     {@code []} all mean no tags. A {@code null} item is reported by its position, such as {@code tagIds[1]}
 * @param dateUnknown {@code true} when the date is a guess (the photo dump, D-34); {@code null} means {@code false}
 */
public record EntryCreateRequest(
        UUID id,
        LocalDate entryDate,
        UUID templateId,

        @Size(min = 1, max = Entry.MAX_TITLE_LENGTH) String title,

        JsonNode contentJson,

        @Size(max = Entry.MAX_TAGS) List<@NotNull UUID> tagIds,

        Boolean dateUnknown) {

    public EntryCreateRequest {
        title = EntryText.title(title);
        contentJson = EntryText.content(contentJson);
    }
}

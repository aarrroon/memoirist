package com.memoirist.entry;

import com.memoirist.common.CreateResult;
import com.memoirist.entry.dto.EntryCreateRequest;
import com.memoirist.entry.dto.EntryResponse;
import com.memoirist.entry.dto.EntrySealRequest;
import com.memoirist.entry.dto.EntryUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/entries}: the user's entries (API_CONTRACT, Entries). HTTP only: the service finds the user, checks
 * that the entry is theirs and applies the seal rules, and the mapper hides what a sealed entry must not show. No user
 * id comes from the request (D-11).
 *
 * <p>The list, the Opened strip, Recently deleted and On this day (tasks 3.9 to 3.12) are added here later.
 */
@RestController
@RequestMapping("/api/v1/entries")
@Tag(name = "Entries")
class EntryController {

    private final EntryService entries;

    EntryController(EntryService entries) {
        this.entries = entries;
    }

    @PostMapping
    @Operation(
            summary = "Create an entry",
            description = "Filed into the week of its memory date, which is created if it is the first entry in it."
                    + " Every field is optional: media are uploaded afterwards.")
    @ApiResponse(responseCode = "201", description = "The new entry")
    @ApiResponse(
            responseCode = "200",
            description = "The user already has an entry with this id: the stored entry, unchanged"
                    + " (redacted if it is sealed)")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed with fieldErrors (title, entryDate, contentJson), or malformed-request"
                    + " (unreadable JSON, a bad X-Dev-User-Id)")
    @ApiResponse(
            responseCode = "404",
            description = "not-found: the header names a user who doesn't exist, or templateId isn't the user's")
    @ApiResponse(
            responseCode = "409",
            description = "conflict: the id belongs to another user's entry, or to one in Recently deleted")
    @ApiResponse(responseCode = "413", description = "payload-too-large: contentJson is over 200 KB")
    @ApiResponse(responseCode = "415", description = "unsupported-media-type: the body is not application/json")
    ResponseEntity<EntryResponse> create(@Valid @RequestBody EntryCreateRequest request) {
        CreateResult<EntryResponse> result = entries.create(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.resource());
    }

    @GetMapping("/{entryId}")
    @Operation(
            summary = "One entry",
            description = "A sealed Loot Drop comes back redacted: isLocked is true and everything private is empty.")
    @ApiResponse(responseCode = "200", description = "The entry")
    @ApiResponse(responseCode = "400", description = "malformed-request: the id is not a UUID")
    @ApiResponse(
            responseCode = "404",
            description = "not-found: no such entry, it is another user's, or it is in Recently deleted")
    EntryResponse get(@PathVariable UUID entryId) {
        return entries.get(entryId);
    }

    @PutMapping("/{entryId}")
    @Operation(
            summary = "Replace an entry's date, template, title and caption",
            description = "A full replace: a field that is left out is cleared. version must be the one last read."
                    + " A new date moves the entry to the matching week and clears dateUnknown.")
    @ApiResponse(responseCode = "200", description = "The entry as saved, with the next version")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed with fieldErrors (version, entryDate, title, contentJson),"
                    + " or malformed-request (unreadable JSON, a bad id)")
    @ApiResponse(
            responseCode = "404",
            description = "not-found: no such entry, it is another user's, it is in Recently deleted,"
                    + " or templateId isn't the user's")
    @ApiResponse(
            responseCode = "409",
            description = "entry-sealed: the entry is a sealed Loot Drop; or entry-stale: version is not the stored"
                    + " one, so the entry changed elsewhere and nothing was saved")
    @ApiResponse(responseCode = "413", description = "payload-too-large: contentJson is over 200 KB")
    @ApiResponse(responseCode = "415", description = "unsupported-media-type: the body is not application/json")
    EntryResponse update(@PathVariable UUID entryId, @Valid @RequestBody EntryUpdateRequest request) {
        return entries.update(entryId, request);
    }

    @DeleteMapping("/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete an entry",
            description = "Moves it to Recently deleted for 30 days; its media files stay until it is erased."
                    + " Allowed when it is sealed. An entry with no title, caption or media is erased at once.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "400", description = "malformed-request: the id is not a UUID")
    @ApiResponse(
            responseCode = "404",
            description = "not-found: no such entry, it is another user's, or it is already in Recently deleted")
    void delete(@PathVariable UUID entryId) {
        entries.delete(entryId);
    }

    @PostMapping("/{entryId}/seal")
    @Operation(
            summary = "Seal an entry as a Loot Drop",
            description = "It opens at unlockDate. On an entry that is already sealed the date can only move later.")
    @ApiResponse(responseCode = "200", description = "The entry, now sealed and redacted")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed: unlockDate is missing, not in the future, or more than 100 years ahead;"
                    + " or malformed-request (unreadable JSON, a bad id)")
    @ApiResponse(responseCode = "404", description = "not-found: no such entry, or it is another user's")
    @ApiResponse(
            responseCode = "409",
            description = "entry-sealed: the entry is sealed and unlockDate is not later than its current one")
    @ApiResponse(responseCode = "415", description = "unsupported-media-type: the body is not application/json")
    EntryResponse seal(@PathVariable UUID entryId, @Valid @RequestBody EntrySealRequest request) {
        return entries.seal(entryId, request);
    }
}

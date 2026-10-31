package com.memoirist.media;

import com.memoirist.media.dto.MediaOrderRequest;
import com.memoirist.media.dto.MediaResponse;
import com.memoirist.media.dto.MediaUploadForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The photos, diary pages and voice notes of an entry (API_CONTRACT, Media). HTTP only: the service finds the user,
 * checks that the entry is theirs and not sealed, and does the storing. No user id comes from the request (D-11).
 *
 * <p>The paths sit under two prefixes (an entry's media, and one media item), so the class maps {@code /api/v1} and
 * each method adds the rest.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Media")
class MediaController {

    private final MediaService media;

    MediaController(MediaService media) {
        this.media = media;
    }

    @PostMapping(path = "/entries/{entryId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add a photo, a diary page or a voice note",
            description = "A multipart form with the file, and optionally where to put it and what it is. The"
                    + " type is the Content-Type of the file part: JPEG, PNG or WebP images, WebM, MP4 or MP3"
                    + " audio. At most 25 MB per file and 20 items per entry.")
    // The form's parts are read as separate parameters (see MediaUploadForm), so springdoc is told the body's shape
    // here.
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content =
                    @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = MediaUploadForm.class)))
    @ApiResponse(responseCode = "201", description = "The stored item, with a presigned fileUrl")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed with fieldErrors: file (missing, empty, or the entry already has 20"
                    + " items), purpose (not PHOTO or PAGE_SCAN), displayOrder (negative, or past the end);"
                    + " or malformed-request (a bad id, displayOrder that isn't a whole number)")
    @ApiResponse(
            responseCode = "404",
            description = "not-found: no such entry, it is another user's, or it is in Recently deleted")
    @ApiResponse(responseCode = "409", description = "entry-sealed: the entry is a sealed Loot Drop")
    @ApiResponse(responseCode = "413", description = "payload-too-large: the file is over 25 MB")
    @ApiResponse(
            responseCode = "415",
            description = "unsupported-media-type: the file's type isn't allowed, a PAGE_SCAN isn't an image, or the"
                    + " request isn't multipart/form-data")
    @ApiResponse(responseCode = "503", description = "storage-unavailable: the file could not be stored")
    MediaResponse upload(
            @PathVariable UUID entryId,
            @Parameter(hidden = true) @RequestParam(name = "file", required = false) MultipartFile file,
            @Parameter(hidden = true) @RequestParam(name = "displayOrder", required = false) Integer displayOrder,
            @Parameter(hidden = true) @RequestParam(name = "purpose", required = false) String purpose) {
        return media.upload(entryId, file, displayOrder, purpose);
    }

    @PutMapping("/entries/{entryId}/media/order")
    @Operation(
            summary = "Reorder an entry's media",
            description = "mediaIds must list every one of the entry's items exactly once, in the new order.")
    @ApiResponse(responseCode = "200", description = "All the entry's media in the new order")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed with fieldErrors.mediaIds: missing, an id repeated, an id that isn't one"
                    + " of the entry's media, or one left out; or malformed-request (unreadable JSON, a bad id)")
    @ApiResponse(
            responseCode = "404",
            description = "not-found: no such entry, it is another user's, or it is in Recently deleted")
    @ApiResponse(responseCode = "409", description = "entry-sealed: the entry is a sealed Loot Drop")
    @ApiResponse(responseCode = "415", description = "unsupported-media-type: the body is not application/json")
    List<MediaResponse> reorder(@PathVariable UUID entryId, @Valid @RequestBody MediaOrderRequest request) {
        return media.reorder(entryId, request);
    }

    @DeleteMapping("/media/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a photo, a diary page or a voice note",
            description = "Removes the row and then the stored file. The items after it move up to close the gap.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "400", description = "malformed-request: the id is not a UUID")
    @ApiResponse(
            responseCode = "404",
            description = "not-found: no such item, its entry is another user's, or its entry is in Recently deleted")
    @ApiResponse(responseCode = "409", description = "entry-sealed: the entry is a sealed Loot Drop")
    void delete(@PathVariable UUID mediaId) {
        media.delete(mediaId);
    }
}

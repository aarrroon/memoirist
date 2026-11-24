package com.memoirist.tag;

import com.memoirist.common.CreateResult;
import com.memoirist.tag.dto.TagRequest;
import com.memoirist.tag.dto.TagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
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
 * {@code /api/v1/tags}: the user's tags (API_CONTRACT, Tags). HTTP only: the service finds the user and checks that the
 * tag is theirs, and no user id comes from the request (D-11). The swagger {@code @Tag} is written out in full because
 * this package has a class called {@code Tag} too.
 */
@RestController
@RequestMapping("/api/v1/tags")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tags")
class TagController {

    private final TagService tags;

    TagController(TagService tags) {
        this.tags = tags;
    }

    @PostMapping
    @Operation(
            summary = "Create a tag",
            description = "A name of 1 to 50 characters. A name the user already has, ignoring case, answers with"
                    + " the existing tag instead.")
    @ApiResponse(responseCode = "201", description = "The new tag")
    @ApiResponse(
            responseCode = "200",
            description = "The user already has a tag with this name, ignoring case: the stored tag, unchanged")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed with fieldErrors (name), or malformed-request (unreadable JSON, a bad"
                    + " X-Dev-User-Id)")
    @ApiResponse(responseCode = "404", description = "not-found: the header names a user who doesn't exist")
    @ApiResponse(responseCode = "415", description = "unsupported-media-type: the body is not application/json")
    ResponseEntity<TagResponse> create(@Valid @RequestBody TagRequest request) {
        CreateResult<TagResponse> result = tags.create(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.resource());
    }

    @GetMapping
    @Operation(
            summary = "The user's tags, by name",
            description = "Each tag has an entryCount that leaves out sealed entries and entries in Recently deleted.")
    @ApiResponse(responseCode = "200", description = "The tags; empty when there are none")
    @ApiResponse(responseCode = "400", description = "malformed-request: the X-Dev-User-Id header is not a UUID")
    List<TagResponse> list() {
        return tags.list();
    }

    @PutMapping("/{tagId}")
    @Operation(
            summary = "Rename a tag",
            description = "Only the name changes. Entries that carry the tag show the new name.")
    @ApiResponse(responseCode = "200", description = "The tag as saved")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed with fieldErrors, or malformed-request (unreadable JSON, a bad id)")
    @ApiResponse(responseCode = "404", description = "not-found: no such tag, or it is another user's")
    @ApiResponse(responseCode = "409", description = "conflict: another of the user's tags has this name")
    @ApiResponse(responseCode = "415", description = "unsupported-media-type: the body is not application/json")
    TagResponse update(@PathVariable UUID tagId, @Valid @RequestBody TagRequest request) {
        return tags.update(tagId, request);
    }

    @DeleteMapping("/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a tag", description = "Entries keep their content (sealed ones too) and lose the tag.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "400", description = "malformed-request: the id is not a UUID")
    @ApiResponse(responseCode = "404", description = "not-found: no such tag, or it is another user's")
    void delete(@PathVariable UUID tagId) {
        tags.delete(tagId);
    }
}

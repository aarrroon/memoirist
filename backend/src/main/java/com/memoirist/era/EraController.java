package com.memoirist.era;

import com.memoirist.common.CreateResult;
import com.memoirist.era.dto.EraCreateRequest;
import com.memoirist.era.dto.EraDetailResponse;
import com.memoirist.era.dto.EraResponse;
import com.memoirist.era.dto.EraUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * {@code /api/v1/eras}: the user's Eras (API_CONTRACT, Eras). HTTP only: the service finds the user and checks that
 * the Era is theirs, and no user id comes from the request (D-11).
 */
@RestController
@RequestMapping("/api/v1/eras")
@Tag(name = "Eras")
class EraController {

    private final EraService eras;

    EraController(EraService eras) {
        this.eras = eras;
    }

    @PostMapping
    @Operation(
            summary = "Create an Era",
            description = "It holds every week its dates overlap, existing weeks included.")
    @ApiResponse(responseCode = "201", description = "The new Era")
    @ApiResponse(
            responseCode = "200",
            description = "The user already has an Era with this id: the stored Era, unchanged")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed with fieldErrors (title, description, startDate, endDate),"
                    + " or malformed-request (unreadable JSON, a bad X-Dev-User-Id)")
    @ApiResponse(responseCode = "404", description = "not-found: the header names a user who doesn't exist")
    @ApiResponse(responseCode = "409", description = "conflict: the id belongs to another user's Era")
    @ApiResponse(responseCode = "415", description = "unsupported-media-type: the body is not application/json")
    ResponseEntity<EraResponse> create(@Valid @RequestBody EraCreateRequest request) {
        CreateResult<EraResponse> result = eras.create(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.resource());
    }

    @GetMapping
    @Operation(summary = "The user's Eras, latest start first")
    @ApiResponse(responseCode = "200", description = "The Eras; empty when there are none")
    @ApiResponse(responseCode = "400", description = "malformed-request: the X-Dev-User-Id header is not a UUID")
    List<EraResponse> list() {
        return eras.list();
    }

    @GetMapping("/{eraId}")
    @Operation(
            summary = "One Era with the weeks its dates overlap",
            description = "entryCount counts only the entries inside the Era's dates, leaving out sealed entries and"
                    + " entries in Recently deleted.")
    @ApiResponse(responseCode = "200", description = "The Era and its weeks, newest first")
    @ApiResponse(responseCode = "400", description = "malformed-request: the id is not a UUID")
    @ApiResponse(responseCode = "404", description = "not-found: no such Era, or it is another user's")
    EraDetailResponse get(@PathVariable UUID eraId) {
        return eras.get(eraId);
    }

    @PutMapping("/{eraId}")
    @Operation(
            summary = "Replace an Era",
            description = "A full replace: a field that is left out is reset. New dates apply to every week at once.")
    @ApiResponse(responseCode = "200", description = "The Era as saved")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed with fieldErrors, or malformed-request (unreadable JSON, a bad id)")
    @ApiResponse(responseCode = "404", description = "not-found: no such Era, or it is another user's")
    @ApiResponse(responseCode = "415", description = "unsupported-media-type: the body is not application/json")
    EraResponse update(@PathVariable UUID eraId, @Valid @RequestBody EraUpdateRequest request) {
        return eras.update(eraId, request);
    }

    @DeleteMapping("/{eraId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an Era", description = "Only the Era goes; its weeks, entries and other Eras stay.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "400", description = "malformed-request: the id is not a UUID")
    @ApiResponse(responseCode = "404", description = "not-found: no such Era, or it is another user's")
    void delete(@PathVariable UUID eraId) {
        eras.delete(eraId);
    }
}

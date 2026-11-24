package com.memoirist.template;

import com.memoirist.common.CreateResult;
import com.memoirist.template.dto.TemplateCreateRequest;
import com.memoirist.template.dto.TemplateResponse;
import com.memoirist.template.dto.TemplateUpdateRequest;
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
 * {@code /api/v1/templates}: the user's templates (API_CONTRACT, Templates). HTTP only: the service finds the user and
 * checks that the template is theirs, and no user id comes from the request (D-11).
 */
@RestController
@RequestMapping("/api/v1/templates")
@Tag(name = "Templates")
class TemplateController {

    private final TemplateService templates;

    TemplateController(TemplateService templates) {
        this.templates = templates;
    }

    @PostMapping
    @Operation(
            summary = "Create a template",
            description = "A name and up to 20 prompts. Also how a starter template is added to the user's own.")
    @ApiResponse(responseCode = "201", description = "The new template")
    @ApiResponse(
            responseCode = "200",
            description = "The user already has a template with this id: the stored template, unchanged")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed with fieldErrors (name, subheadings, subheadings[n]),"
                    + " or malformed-request (unreadable JSON, a bad X-Dev-User-Id)")
    @ApiResponse(responseCode = "404", description = "not-found: the header names a user who doesn't exist")
    @ApiResponse(responseCode = "409", description = "conflict: the id belongs to another user's template")
    @ApiResponse(responseCode = "415", description = "unsupported-media-type: the body is not application/json")
    ResponseEntity<TemplateResponse> create(@Valid @RequestBody TemplateCreateRequest request) {
        CreateResult<TemplateResponse> result = templates.create(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.resource());
    }

    @GetMapping
    @Operation(summary = "The user's templates, by name")
    @ApiResponse(responseCode = "200", description = "The templates; empty when there are none")
    @ApiResponse(responseCode = "400", description = "malformed-request: the X-Dev-User-Id header is not a UUID")
    List<TemplateResponse> list() {
        return templates.list();
    }

    @GetMapping("/{templateId}")
    @Operation(summary = "One template")
    @ApiResponse(responseCode = "200", description = "The template")
    @ApiResponse(responseCode = "400", description = "malformed-request: the id is not a UUID")
    @ApiResponse(responseCode = "404", description = "not-found: no such template, or it is another user's")
    TemplateResponse get(@PathVariable UUID templateId) {
        return templates.get(templateId);
    }

    @PutMapping("/{templateId}")
    @Operation(
            summary = "Replace a template",
            description = "A full replace: the name and the whole list of subheadings. Entries already written from"
                    + " the template are unaffected.")
    @ApiResponse(responseCode = "200", description = "The template as saved")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed with fieldErrors, or malformed-request (unreadable JSON, a bad id)")
    @ApiResponse(responseCode = "404", description = "not-found: no such template, or it is another user's")
    @ApiResponse(responseCode = "415", description = "unsupported-media-type: the body is not application/json")
    TemplateResponse update(@PathVariable UUID templateId, @Valid @RequestBody TemplateUpdateRequest request) {
        return templates.update(templateId, request);
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a template",
            description = "Entries keep their content (sealed ones too); their templateId becomes null.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "400", description = "malformed-request: the id is not a UUID")
    @ApiResponse(responseCode = "404", description = "not-found: no such template, or it is another user's")
    void delete(@PathVariable UUID templateId) {
        templates.delete(templateId);
    }
}

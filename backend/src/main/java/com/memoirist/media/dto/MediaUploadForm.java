package com.memoirist.media.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * <b>Documentation only: nothing reads this record.</b> It describes the multipart form of
 * {@code POST /entries/{entryId}/media} for {@code /v3/api-docs} and the types generated from it. The controller reads
 * the three parts as separate parameters instead of binding them to a record, because a value that doesn't convert
 * (a {@code displayOrder} of {@code first}) would then be reported by Spring's data binder with the rejected value in
 * its message, and an error response never repeats what the client sent. springdoc shows a simple
 * {@code @RequestParam} of a multipart form as a query parameter, which is wrong for a form field, so the controller
 * hides those and points the request body at this record.
 *
 * <p>{@code MediaApiDescriptionTest} keeps the field names here equal to the parameter names there.
 *
 * @param file the photo, diary page or voice note: JPEG, PNG or WebP images, WebM, MP4 or MP3 audio; at most 25 MB
 * @param displayOrder where to put it, 0 for first; left out, it goes last
 * @param purpose {@code PHOTO} (the default) or {@code PAGE_SCAN}, a photo of a paper diary page, which must be an image
 */
@Schema(name = "MediaUploadForm")
public record MediaUploadForm(
        @Schema(type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
        String file,

        @Schema(minimum = "0", maximum = "19", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer displayOrder,

        @Schema(
                allowableValues = {"PHOTO", "PAGE_SCAN"},
                defaultValue = "PHOTO",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String purpose) {}

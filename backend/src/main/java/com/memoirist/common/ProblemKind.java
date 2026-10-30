package com.memoirist.common;

import org.springframework.http.HttpStatus;

/**
 * The problem types of API_CONTRACT (Errors): the {@code type} suffix, the status and the title of each, and a
 * default {@code detail} for the ones Spring raises itself.
 *
 * <p>This is the one place the list lives. The advice builds every response from it, and the contract and the tests
 * pin the same strings, so renaming a constant changes nothing the client sees.
 */
enum ProblemKind {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed", "The request is not valid."),
    MALFORMED_REQUEST(
            HttpStatus.BAD_REQUEST,
            "malformed-request",
            "Malformed request",
            "The request can't be understood. Check its shape and try again."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "not-found", "Not found", "Nothing exists at this address."),
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "method-not-allowed",
            "Method not allowed",
            "This address doesn't accept that HTTP method. The Allow header lists the ones it does."),
    NOT_ACCEPTABLE(
            HttpStatus.NOT_ACCEPTABLE,
            "not-acceptable",
            "Not acceptable",
            "The API can't send any of the formats the Accept header asks for."),
    ENTRY_SEALED(HttpStatus.CONFLICT, "entry-sealed", "Entry is sealed", "This entry is sealed."),
    ENTRY_STALE(
            HttpStatus.CONFLICT,
            "entry-stale",
            "Entry has changed",
            "The entry was changed elsewhere after this copy was loaded. Load the latest version and try again."),
    CONFLICT(HttpStatus.CONFLICT, "conflict", "Conflict", "The request conflicts with existing data."),
    PAYLOAD_TOO_LARGE(
            HttpStatus.CONTENT_TOO_LARGE,
            "payload-too-large",
            "Payload too large",
            "The request is larger than this server accepts."),
    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "unsupported-media-type",
            "Unsupported media type",
            "This content type isn't supported here."),
    STORAGE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "storage-unavailable",
            "Storage unavailable",
            "File storage isn't available right now. Please try again."),
    MUSIC_SEARCH_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "music-search-unavailable",
            "Music search unavailable",
            "Music search isn't available right now."),
    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal-error",
            "Internal error",
            "Something went wrong on our side. Please try again.");

    private final HttpStatus status;
    private final String typeSuffix;
    private final String title;
    private final String defaultDetail;

    ProblemKind(HttpStatus status, String typeSuffix, String title, String defaultDetail) {
        this.status = status;
        this.typeSuffix = typeSuffix;
        this.title = title;
        this.defaultDetail = defaultDetail;
    }

    HttpStatus status() {
        return status;
    }

    String typeSuffix() {
        return typeSuffix;
    }

    String title() {
        return title;
    }

    String defaultDetail() {
        return defaultDetail;
    }
}

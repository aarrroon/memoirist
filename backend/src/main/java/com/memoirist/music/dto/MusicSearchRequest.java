package com.memoirist.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The body of {@code POST /api/v1/music/search} (API_CONTRACT, Music). The words go in the body and never in the URL,
 * so they don't reach an access log or the browser's history (D-29, D-35).
 *
 * <p><b>The rules (1 to 200 characters once stripped) are checked in {@code MusicSearchService}, not with
 * {@code @NotBlank} and {@code @Size} here.</b> When bean validation fails, Spring's
 * {@code MethodArgumentNotValidException} carries a message that includes the <em>rejected value</em>, and Spring
 * logs that exception at DEBUG ("Resolved [...]"). For a search box that would put what the user typed into a log
 * line. Our own {@code ValidationFailedException} names the field and the rule and never the value. The
 * {@link Schema} annotation keeps the limits in the OpenAPI description.
 *
 * <p>The words are stripped of spaces at both ends, so a query of only spaces is empty. {@link #toString()} hides
 * them: a record prints every component, and anything that logs the request object (Spring does, at TRACE) would
 * otherwise print what the user searched for.
 *
 * @param query the words to search for
 */
public record MusicSearchRequest(
        @Schema(minLength = 1, maxLength = 200) String query) {

    public MusicSearchRequest {
        query = query == null ? null : query.strip();
    }

    @Override
    public String toString() {
        return "MusicSearchRequest[query=******]";
    }
}

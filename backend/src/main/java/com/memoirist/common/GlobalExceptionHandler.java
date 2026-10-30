package com.memoirist.common;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions into RFC 9457 problem responses with the types in API_CONTRACT (Errors).
 *
 * <p>Only {@link MalformedRequestException} is handled so far, because task 2.5 needs a {@code 400} to answer with.
 * Task 2.6 adds the rest (validation, not found, conflicts, stale entries, size limits, the catch-all) to this class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_TYPE_PREFIX = "urn:memoirist:problem:";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MalformedRequestException.class)
    ProblemDetail malformedRequest(MalformedRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request", exception.getMessage());
    }

    /**
     * A problem with the fields every error response carries. Spring fills {@code instance} with the request path
     * and writes the body as {@code application/problem+json}.
     */
    private ProblemDetail problem(HttpStatus status, String typeSuffix, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_TYPE_PREFIX + typeSuffix));
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now(clock));
        return problem;
    }
}

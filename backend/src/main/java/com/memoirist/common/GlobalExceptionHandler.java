package com.memoirist.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The one place an exception becomes an HTTP response: an RFC 9457 problem with the types in API_CONTRACT (Errors).
 *
 * <p>Every body carries {@code type} ({@code urn:memoirist:problem:<suffix>}), {@code title}, {@code status},
 * {@code detail}, {@code instance} (the request path, which Spring fills in) and {@code timestamp} (from the
 * {@link Clock}). {@code fieldErrors} is added for {@code validation-failed}.
 *
 * <p>Three rules hold for every response, and the tests pin them:
 * <ol>
 *   <li>No stack trace, class name, SQL or constraint name reaches the client. A {@code 500} always gets the same
 *       sentence. The log gets the exception classes and stack frames of the cause, never its message
 *       ({@link ScrubbedException}), because a message can hold entry text.
 *   <li>The client's own input is never echoed back in {@code detail} or {@code fieldErrors}.
 *   <li>Spring's own errors (an unknown path, a wrong method) keep their status. They are <em>not</em> turned into
 *       {@code 500} by the catch-all below.
 * </ol>
 *
 * <p>The class extends {@link ResponseEntityExceptionHandler}, which already handles the exceptions Spring MVC raises
 * itself (unreadable body, wrong method, unknown path, over-size upload) with the correct status. Without it, the
 * {@code @ExceptionHandler(Exception.class)} at the bottom would catch those too and answer {@code 500} for a request
 * that is simply wrong. Spring picks the handler with the closest exception type, so the specific ones win.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String PROBLEM_TYPE_PREFIX = "urn:memoirist:problem:";

    /** The type Spring gives a problem it built itself: RFC 9457's "nothing to add to the status code". */
    private static final URI ABOUT_BLANK = URI.create("about:blank");

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    // ---- Our own exceptions: services throw these ---------------------------------------------------------------

    @ExceptionHandler(MalformedRequestException.class)
    ProblemDetail malformedRequest(MalformedRequestException exception) {
        return problem(ProblemKind.MALFORMED_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ValidationFailedException.class)
    ProblemDetail validationFailed(ValidationFailedException exception) {
        ProblemDetail problem = problem(ProblemKind.VALIDATION_FAILED, exception.getMessage());
        if (!exception.getFieldErrors().isEmpty()) {
            problem.setProperty("fieldErrors", exception.getFieldErrors());
        }
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception) {
        return problem(ProblemKind.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(EntrySealedException.class)
    ProblemDetail entrySealed(EntrySealedException exception) {
        return problem(ProblemKind.ENTRY_SEALED, exception.getMessage());
    }

    /**
     * Two ways to the same answer (D-23). {@link EntryStaleException}: the service compared the versions and they
     * differed. {@link ObjectOptimisticLockingFailureException}: the service's check passed, then another request
     * committed first and Hibernate's {@code UPDATE ... WHERE version = ?} matched no row.
     *
     * <p>Only {@code Entry} has a {@code @Version} in Phase 1, so any optimistic-locking failure is an entry (a test
     * fails when a second versioned entity appears, so this can't go stale silently). The detail is fixed: the
     * exception's own message names the entity class and the row id.
     */
    @ExceptionHandler({EntryStaleException.class, ObjectOptimisticLockingFailureException.class})
    ProblemDetail entryStale(Exception exception) {
        return problem(ProblemKind.ENTRY_STALE, ProblemKind.ENTRY_STALE.defaultDetail());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException exception) {
        return problem(ProblemKind.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ProblemDetail payloadTooLarge(PayloadTooLargeException exception) {
        return problem(ProblemKind.PAYLOAD_TOO_LARGE, exception.getMessage());
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    ProblemDetail unsupportedFileType(UnsupportedFileTypeException exception) {
        return problem(ProblemKind.UNSUPPORTED_MEDIA_TYPE, exception.getMessage());
    }

    @ExceptionHandler(StorageUnavailableException.class)
    ProblemDetail storageUnavailable(StorageUnavailableException exception) {
        // The cause can name the storage host and bucket, so the client never gets it and the log gets no message.
        log.error("Object storage failed", ScrubbedException.of(exception));
        return problem(ProblemKind.STORAGE_UNAVAILABLE, ProblemKind.STORAGE_UNAVAILABLE.defaultDetail());
    }

    @ExceptionHandler(MusicSearchUnavailableException.class)
    ProblemDetail musicSearchUnavailable(MusicSearchUnavailableException exception) {
        if (exception.getCause() != null) {
            log.warn("Music search failed", ScrubbedException.of(exception));
        }
        return problem(ProblemKind.MUSIC_SEARCH_UNAVAILABLE, exception.getMessage());
    }

    /**
     * Anything else is a bug or an outage on our side: {@code 500}. The client gets a fixed sentence. The log gets the
     * exception classes (the causes too) and the stack frames, but no message, because a message can hold what the
     * user wrote (see {@link ScrubbedException}). Only the path is logged, never the query string or the body, for the
     * same reason.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception, HttpServletRequest request) {
        log.error(
                "Unhandled exception on {} {}",
                request.getMethod(),
                request.getRequestURI(),
                ScrubbedException.of(exception));
        return problem(ProblemKind.INTERNAL_ERROR, ProblemKind.INTERNAL_ERROR.defaultDetail());
    }

    // ---- Spring MVC's exceptions: the ones that need more than their default ------------------------------------

    /** A {@code @Valid @RequestBody} failed: one entry in {@code fieldErrors} for each field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        FieldErrors errors = new FieldErrors();
        exception
                .getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.add(error.getField(), error.getDefaultMessage()));
        exception.getBindingResult().getGlobalErrors().forEach(error -> errors.addGeneral(error.getDefaultMessage()));
        return validationFailed(exception, errors, headers, status, request);
    }

    /**
     * A constraint on a controller parameter failed ({@code @RequestParam @Min(0) int page}, a path variable, a
     * header). The parameter's name stands in for the field.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        FieldErrors errors = new FieldErrors();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors
                        .getFieldErrors()
                        .forEach(error -> errors.add(error.getField(), error.getDefaultMessage()));
                parameterErrors.getGlobalErrors().forEach(error -> errors.addGeneral(error.getDefaultMessage()));
            } else {
                String name = result.getMethodParameter().getParameterName();
                String field = name != null
                        ? name
                        : "parameter" + result.getMethodParameter().getParameterIndex();
                result.getResolvableErrors().forEach(error -> errors.add(field, error.getDefaultMessage()));
            }
        }
        return validationFailed(exception, errors, headers, status, request);
    }

    /**
     * A path variable or query parameter could not be converted, such as {@code /entries/not-a-uuid}. The default
     * detail repeats the rejected value and the Java type, so this one names only the parameter (which is our own
     * identifier) and says nothing else.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? "Parameter '" + mismatch.getName() + "' is not in the expected format."
                : ProblemKind.MALFORMED_REQUEST.defaultDetail();
        return handleExceptionInternal(
                exception, problem(ProblemKind.MALFORMED_REQUEST, detail), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String detail = "Required parameter '" + exception.getParameterName() + "' is missing.";
        return handleExceptionInternal(
                exception, problem(ProblemKind.MALFORMED_REQUEST, detail), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
            MissingServletRequestPartException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String detail = "Required part '" + exception.getRequestPartName() + "' is missing.";
        return handleExceptionInternal(
                exception, problem(ProblemKind.MALFORMED_REQUEST, detail), headers, status, request);
    }

    /**
     * Every response Spring builds for its own exceptions passes through here (and so do the ones above). A problem
     * Spring built has no type (clients read that as {@code about:blank}), a title from the HTTP reason phrase and a detail that
     * sometimes repeats what the client sent ("Method 'DELETE' is not supported", "No static resource ..."). It gets
     * our type, title and a fixed detail for its status, and a {@code timestamp}. A problem made by this class already
     * has our type and is left alone.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            completeSpringProblem(problem, status);
            if (status.is5xxServerError()) {
                log.error("Server error on {}", request.getDescription(false), ScrubbedException.of(exception));
            }
        }
        return response;
    }

    // ---- Building problems --------------------------------------------------------------------------------------

    private ResponseEntity<Object> validationFailed(
            Exception exception, FieldErrors errors, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String summary = errors.summary();
        ProblemDetail problem = problem(
                ProblemKind.VALIDATION_FAILED,
                summary.isEmpty() ? ProblemKind.VALIDATION_FAILED.defaultDetail() : summary);
        if (!errors.hasNoFields()) {
            problem.setProperty("fieldErrors", errors.asMap());
        }
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    /**
     * A problem with the fields every error response carries. Spring fills {@code instance} with the request path
     * and writes the body as {@code application/problem+json}.
     */
    private ProblemDetail problem(ProblemKind kind, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(kind.status(), detail);
        problem.setType(URI.create(PROBLEM_TYPE_PREFIX + kind.typeSuffix()));
        problem.setTitle(kind.title());
        problem.setProperty("timestamp", Instant.now(clock));
        return problem;
    }

    private void completeSpringProblem(ProblemDetail problem, HttpStatusCode status) {
        if (problem.getProperties() == null || !problem.getProperties().containsKey("timestamp")) {
            problem.setProperty("timestamp", Instant.now(clock));
        }
        if (hasOurType(problem)) {
            return;
        }
        ProblemKind kind = kindFor(status);
        if (kind != null) {
            problem.setType(URI.create(PROBLEM_TYPE_PREFIX + kind.typeSuffix()));
            problem.setTitle(kind.title());
            problem.setDetail(kind.defaultDetail());
        }
    }

    /**
     * A problem Spring built has no type: in Spring 7 the field stays {@code null}, and Jackson then writes nothing,
     * which clients read as {@code about:blank}. A problem made by this class has one of ours.
     */
    private static boolean hasOurType(ProblemDetail problem) {
        URI type = problem.getType();
        return type != null && !ABOUT_BLANK.equals(type);
    }

    /**
     * Which of our types fits a status that Spring chose. Statuses the contract doesn't list (a {@code 503} from an
     * async timeout, say) return {@code null}, and the problem keeps {@code about:blank}, which RFC 9457 defines for
     * exactly that. {@code internal-error} is only ever a {@code 500}.
     */
    private static ProblemKind kindFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ProblemKind.MALFORMED_REQUEST;
            case 404 -> ProblemKind.NOT_FOUND;
            case 405 -> ProblemKind.METHOD_NOT_ALLOWED;
            case 406 -> ProblemKind.NOT_ACCEPTABLE;
            case 413 -> ProblemKind.PAYLOAD_TOO_LARGE;
            case 415 -> ProblemKind.UNSUPPORTED_MEDIA_TYPE;
            case 500 -> ProblemKind.INTERNAL_ERROR;
            default -> null;
        };
    }
}

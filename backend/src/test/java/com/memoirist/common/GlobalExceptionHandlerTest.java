package com.memoirist.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Every error the API can answer with (API_CONTRACT, Errors), checked over HTTP in a web slice: Spring MVC, the advice
 * and a controller that exists only in this test. No database and no Docker.
 *
 * <p>The assertions are written as literal strings ({@code "urn:memoirist:problem:not-found"}), not as constants from
 * the main code. If someone renames a type in the code, these tests fail, which is the point: the type strings are a
 * promise to the client.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import({
    GlobalExceptionHandlerTest.ProbeController.class,
    GlobalExceptionHandler.class,
    GlobalExceptionHandlerTest.FixedClockConfig.class
})
class GlobalExceptionHandlerTest {

    private static final String PREFIX = "urn:memoirist:problem:";
    private static final Instant FIXED_NOW = Instant.parse("2026-10-30T01:02:03Z");

    /** A fixed clock, so the tests can assert the exact timestamp and prove it comes from the injected Clock. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    RestTestClient client;

    // ---- Each type in the contract: status, type and title, for the exception a service would throw ------------

    static Stream<Arguments> everyType() {
        return Stream.of(
                // kind (what the probe throws), HTTP status, type suffix, title
                Arguments.of("malformed", 400, "malformed-request", "Malformed request"),
                Arguments.of("validation", 400, "validation-failed", "Validation failed"),
                Arguments.of("not-found", 404, "not-found", "Not found"),
                Arguments.of("entry-sealed", 409, "entry-sealed", "Entry is sealed"),
                Arguments.of("entry-stale", 409, "entry-stale", "Entry has changed"),
                Arguments.of("optimistic-lock", 409, "entry-stale", "Entry has changed"),
                Arguments.of("conflict", 409, "conflict", "Conflict"),
                Arguments.of("too-large", 413, "payload-too-large", "Payload too large"),
                Arguments.of("file-type", 415, "unsupported-media-type", "Unsupported media type"),
                Arguments.of("storage", 503, "storage-unavailable", "Storage unavailable"),
                Arguments.of("music", 503, "music-search-unavailable", "Music search unavailable"),
                Arguments.of("unexpected", 500, "internal-error", "Internal error"),
                // An Error is not an Exception. Spring wraps it in a ServletException, and it still ends as a 500.
                Arguments.of("error", 500, "internal-error", "Internal error"));
    }

    @ParameterizedTest(name = "{0} -> {1} {2}")
    @MethodSource("everyType")
    void everyTypeInTheContractHasItsStatusTypeAndTitle(String kind, int status, String suffix, String title) {
        assertProblem(client.get().uri("/test-support/throw/" + kind).exchange(), status, suffix, title, json -> {});
    }

    // ---- 400 with fieldErrors -----------------------------------------------------------------------------------

    @Test
    void aBodyThatFailsBeanValidationIs400WithOneFieldErrorPerField() {
        String body = """
                {"title": "", "name": " ", "code": "SECRET-VALUE-123", "tags": [{"name": "ok"}, {"name": ""}]}
                """;

        assertProblem(postJson("/test-support/notes", body), 400, "validation-failed", "Validation failed", json -> {
            // Nested fields use the path; a field with two failures gets both, sorted and joined.
            assertThat(json.<Map<String, String>>read("$.fieldErrors"))
                    .containsExactly(
                            Map.entry("code", "must match \"[a-z]+\""),
                            Map.entry("name", "must not be blank; size must be between 3 and 150"),
                            Map.entry("tags[1].name", "must not be blank"),
                            Map.entry("title", "must not be blank"));
            assertThat(json.<String>read("$.detail"))
                    .isEqualTo("code must match \"[a-z]+\"; name must not be blank; name size must be between"
                            + " 3 and 150; tags[1].name must not be blank; title must not be blank");
        });
    }

    @Test
    void theRejectedValueIsNeverEchoedBack() {
        // assertProblem already checks the whole body for stack-trace words. This one checks the client's own text.
        String body = """
                {"title": "fine", "name": "fine", "code": "SECRET-VALUE-123"}
                """;

        client.post()
                .uri("/test-support/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .value(response -> assertThat(response).doesNotContain("SECRET-VALUE-123"));
    }

    @Test
    void aConstraintOnAQueryParameterIsAlsoValidationFailedWithTheParameterNameAsTheField() {
        assertProblem(
                client.get().uri("/test-support/page?page=-1").exchange(),
                400,
                "validation-failed",
                "Validation failed",
                json -> assertThat(json.<Map<String, String>>read("$.fieldErrors"))
                        .containsExactly(Map.entry("page", "must be greater than or equal to 0")));
    }

    @Test
    void aBusinessRuleThatNamesAFieldHasFieldErrorsToo() {
        assertProblem(
                client.get().uri("/test-support/throw/validation-field").exchange(),
                400,
                "validation-failed",
                "Validation failed",
                json -> {
                    assertThat(json.<String>read("$.detail")).isEqualTo("entryDate must not be after today");
                    assertThat(json.<Map<String, String>>read("$.fieldErrors"))
                            .containsExactly(Map.entry("entryDate", "must not be after today"));
                });
    }

    @Test
    void aBusinessRuleWithoutAFieldHasNoFieldErrors() {
        assertProblem(
                client.get().uri("/test-support/throw/validation").exchange(),
                400,
                "validation-failed",
                "Validation failed",
                json -> assertThat(json.jsonString()).doesNotContain("fieldErrors"));
    }

    // ---- 400 malformed-request, from Spring's own exceptions ------------------------------------------------------

    @Test
    void jsonThatCannotBeReadIs400MalformedRequest() {
        assertProblem(
                postJson("/test-support/notes", "{this is not json"),
                400,
                "malformed-request",
                "Malformed request",
                json -> assertThat(json.<String>read("$.detail")).doesNotContain("this is not json"));
    }

    @Test
    void aBadUuidInThePathIs400AndNamesOnlyTheParameter() {
        assertProblem(
                client.get().uri("/test-support/items/not-a-uuid-SECRET").exchange(),
                400,
                "malformed-request",
                "Malformed request",
                json -> assertThat(json.<String>read("$.detail"))
                        .isEqualTo("Parameter 'id' is not in the expected format."));
    }

    @Test
    void aMissingRequiredParameterIs400() {
        assertProblem(
                client.get().uri("/test-support/needs").exchange(),
                400,
                "malformed-request",
                "Malformed request",
                json -> assertThat(json.<String>read("$.detail")).isEqualTo("Required parameter 'x' is missing."));
    }

    // ---- Spring's own 404, 405, 415: they keep their status and get our type ---------------------------------------

    @Test
    void anUnknownPathIs404NotFoundAndNotA500() {
        assertProblem(
                client.get().uri("/test-support/nothing-here").exchange(),
                404,
                "not-found",
                "Not found",
                json -> assertThat(json.<String>read("$.detail")).isEqualTo("Nothing exists at this address."));
    }

    @Test
    void aWrongMethodIs405AndKeepsTheAllowHeader() {
        assertProblem(
                client.delete().uri("/test-support/needs").exchange(),
                405,
                "method-not-allowed",
                "Method not allowed",
                json -> assertThat(json.<String>read("$.detail")).doesNotContain("DELETE"));

        client.delete()
                .uri("/test-support/needs")
                .exchange()
                .expectHeader()
                .value("Allow", allow -> assertThat(allow).contains("GET"));
    }

    @Test
    void aWrongContentTypeIs415UnsupportedMediaType() {
        assertProblem(
                client.post()
                        .uri("/test-support/notes")
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("hello")
                        .exchange(),
                415,
                "unsupported-media-type",
                "Unsupported media type",
                json -> assertThat(json.<String>read("$.detail")).doesNotContain("text/plain"));
    }

    @Test
    void anAcceptHeaderTheApiCannotSatisfyIs406WithAProblemBodyAllTheSame() {
        // The client asks for XML and the endpoint only sends JSON. The error body is still application/problem+json:
        // the client said it can't read XML, but a problem is the one thing the API sends whatever the Accept says,
        // and assertProblem checks that it arrives instead of an empty 406.
        assertProblem(
                client.get()
                        .uri("/test-support/json-only")
                        .header("Accept", "application/xml")
                        .exchange(),
                406,
                "not-acceptable",
                "Not acceptable",
                json -> assertThat(json.<String>read("$.detail"))
                        .isEqualTo("The API can't send any of the formats the Accept header asks for.")
                        .doesNotContain("xml"));
    }

    @Test
    void aStatusThatIsNotInTheContractKeepsItsStatusAndIsNotCalledInternalError() {
        // Spring answers an async timeout with 503. internal-error is the contract's 500, so this stays a plain
        // Spring problem (no type, which clients read as about:blank), still with the timestamp.
        client.get()
                .uri("/test-support/throw/async-timeout")
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody(String.class)
                .value(body -> {
                    DocumentContext json = JsonPath.parse(body);
                    assertThat(body).doesNotContain("urn:memoirist:problem:");
                    assertThat(json.<Integer>read("$.status")).isEqualTo(503);
                    assertThat(json.<String>read("$.timestamp")).isEqualTo(FIXED_NOW.toString());
                });
    }

    @Test
    void anUploadOverTheSizeLimitIs413() {
        // Spring raises MaxUploadSizeExceededException; here the probe throws it, because MockMvc enforces no limit.
        // ErrorHandlingIntegrationTest sends a real 25 MB + 1 KB file to the real server.
        assertProblem(
                client.get().uri("/test-support/throw/max-upload").exchange(),
                413,
                "payload-too-large",
                "Payload too large",
                json -> assertThat(json.<String>read("$.detail"))
                        .isEqualTo("The request is larger than this server accepts."));
    }

    // ---- 500: no leak to the client, no message in the log
    // -----------------------------------------------------------

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aServerErrorHidesItsMessageFromTheClientAndTheLogKeepsTheClassAndTheStackFramesButNotTheMessage(
            CapturedOutput output) {
        // TODO(human) PRACTICE: Prove both sides of a 500. The client sees a fixed sentence and none of what went
        //   wrong inside. The server log still says which exception it was and where it was thrown, so someone can
        //   debug it, but it does not hold the exception's message. The probe throws
        //   RuntimeException("db password=hunter2 host=internal-db").
        // Hint: OutputCaptureExtension gives you CapturedOutput, the text written to System.out and System.err so
        //   far. A test that only checks the body passes even if nobody logs anything, and a 500 nobody can
        //   investigate is as bad as a leaky one. security.md says never to log entry content: a database message
        //   can hold a whole row, so the log gets the class and the frames, not getMessage(). Assert what is in the
        //   log and what must not be.
        // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
        assertProblem(
                client.get().uri("/test-support/throw/unexpected-secret").exchange(),
                500,
                "internal-error",
                "Internal error",
                json -> assertThat(json.<String>read("$.detail"))
                        .isEqualTo("Something went wrong on our side. Please try again."));

        assertThat(output.getAll())
                .contains("Unhandled exception on GET /test-support/throw/unexpected-secret")
                .contains("ScrubbedException: java.lang.RuntimeException")
                .contains("at com.memoirist.common.GlobalExceptionHandlerTest$ProbeController.throwIt")
                .doesNotContain("hunter2")
                .doesNotContain("internal-db");
        // └── END ANSWER ▲ ──
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aStorageFailureLogsItsClassesAndItsCauseButNeitherMessage(CapturedOutput output) {
        // The probe throws StorageUnavailableException("put failed", RuntimeException("connect to
        // storage.internal...")).
        client.get()
                .uri("/test-support/throw/storage")
                .exchange()
                .expectStatus()
                .isEqualTo(503);

        assertThat(output.getAll())
                .contains("Object storage failed")
                .contains("ScrubbedException: com.memoirist.common.StorageUnavailableException")
                .contains("Caused by: com.memoirist.common.ScrubbedException: java.lang.RuntimeException")
                .doesNotContain("put failed")
                .doesNotContain("storage.internal");
    }

    @Test
    void theStorageCauseIsNeverSent() {
        // Storage failures carry the host and bucket in their cause. The client gets the fixed sentence.
        client.get()
                .uri("/test-support/throw/storage")
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .doesNotContain("storage.internal")
                        .contains("File storage isn't available right now. Please try again."));
    }

    // ---- Things true of every response
    // -------------------------------------------------------------------------------

    @Test
    void theMessagesStayInEnglishWhateverLanguageTheBrowserAsksFor() {
        client.post()
                .uri("/test-support/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", "fr")
                .body("""
                        {"title": "", "name": "fine"}
                        """)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.fieldErrors.title")
                .isEqualTo("must not be blank");
    }

    // ---- Helpers
    // -----------------------------------------------------------------------------------------------------

    private RestTestClient.ResponseSpec postJson(String uri, String body) {
        return client.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    /**
     * What every error response must look like, whatever caused it: the status, an RFC 9457 content type, our type and
     * title, the request path as {@code instance}, the clock's {@code timestamp}, and no sign of the code behind it.
     */
    private void assertProblem(
            RestTestClient.ResponseSpec response,
            int status,
            String suffix,
            String title,
            Consumer<DocumentContext> more) {
        response.expectStatus()
                .isEqualTo(status)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class)
                .value(body -> {
                    DocumentContext json = JsonPath.parse(body);
                    assertThat(json.<String>read("$.type")).isEqualTo(PREFIX + suffix);
                    assertThat(json.<String>read("$.title")).isEqualTo(title);
                    assertThat(json.<Integer>read("$.status")).isEqualTo(status);
                    assertThat(json.<String>read("$.detail")).isNotBlank();
                    assertThat(json.<String>read("$.instance")).startsWith("/test-support/");
                    assertThat(json.<String>read("$.timestamp")).isEqualTo(FIXED_NOW.toString());
                    // No stack trace, class name, package or file name in any response.
                    assertThat(body)
                            .doesNotContain("Exception")
                            .doesNotContain("com.memoirist")
                            .doesNotContain("org.springframework")
                            .doesNotContain("java.lang")
                            .doesNotContain(".java")
                            .doesNotContain("\"trace\"")
                            .doesNotContain("hunter2")
                            .doesNotContain("storage.internal");
                    more.accept(json);
                });
    }

    // ---- The controller that stands in for real endpoints
    // ------------------------------------------------------------

    record NoteRequest(
            @NotBlank String title,
            @NotBlank @Size(min = 3, max = 150) String name,
            @Pattern(regexp = "[a-z]+") String code,
            @Valid List<TagRef> tags) {}

    record TagRef(@NotBlank String name) {}

    /** Endpoints that only throw or validate. Test code only: {@code @TestComponent} keeps them out of the app. */
    @TestComponent
    @RestController
    static class ProbeController {

        @GetMapping("/test-support/throw/{kind}")
        void throwIt(@PathVariable String kind) {
            switch (kind) {
                case "malformed" -> throw new MalformedRequestException("The X-Test header is not usable.");
                case "validation" -> throw new ValidationFailedException("The dates don't line up.");
                case "validation-field" -> throw new ValidationFailedException("entryDate", "must not be after today");
                case "not-found" -> throw new NotFoundException("Entry");
                case "entry-sealed" -> throw new EntrySealedException("This entry is sealed until its unlock date.");
                case "entry-stale" -> throw new EntryStaleException();
                case "optimistic-lock" ->
                    throw new ObjectOptimisticLockingFailureException(
                            "com.memoirist.entry.Entry", UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
                case "conflict" -> throw new ConflictException("That id is already in use.");
                case "too-large" -> throw new PayloadTooLargeException("Entry content is over 200 KB.");
                case "max-upload" -> throw new MaxUploadSizeExceededException(-1);
                case "file-type" -> throw new UnsupportedFileTypeException("That file type isn't supported.");
                case "storage" ->
                    throw new StorageUnavailableException(
                            "put failed", new RuntimeException("connect to storage.internal:8333 refused"));
                case "music" -> throw new MusicSearchUnavailableException("Music search isn't set up.");
                case "unexpected" -> throw new IllegalStateException("something broke");
                case "unexpected-secret" -> throw new RuntimeException("db password=hunter2 host=internal-db");
                case "error" -> throw new AssertionError("an Error, not an Exception");
                case "async-timeout" -> throw new AsyncRequestTimeoutException();
                default -> throw new NotFoundException("Probe");
            }
        }

        @PostMapping(path = "/test-support/notes", consumes = MediaType.APPLICATION_JSON_VALUE)
        Map<String, String> note(@Valid @RequestBody NoteRequest request) {
            return Map.of("ok", "true");
        }

        @GetMapping("/test-support/page")
        String page(@RequestParam @Min(0) int page) {
            return "page " + page;
        }

        @GetMapping("/test-support/items/{id}")
        String item(@PathVariable UUID id) {
            return id.toString();
        }

        @GetMapping(path = "/test-support/json-only", produces = MediaType.APPLICATION_JSON_VALUE)
        Map<String, String> jsonOnly() {
            return Map.of("ok", "true");
        }

        @GetMapping("/test-support/needs")
        String needs(@RequestParam String x) {
            return x;
        }
    }
}

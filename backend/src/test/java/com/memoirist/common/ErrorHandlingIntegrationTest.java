package com.memoirist.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.entry.Entry;
import com.memoirist.entry.EntryRepository;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The errors that only a real app produces, with the advice wired in the way the app wires it: a real Tomcat on a
 * random port, a real Postgres, and Hibernate's own exceptions.
 *
 * <p>{@link GlobalExceptionHandlerTest} proves the mapping with hand-made exceptions. That leaves questions it
 * can't answer, and each test here answers one:
 * <ul>
 *   <li>Is the exception Hibernate really throws for a stale edit the one the advice listens for?
 *   <li>Does a real file over 25 MB, sent to the real multipart limit, reach the advice as a {@code 413}?
 *   <li>Does a real database error (SQL, a constraint name, a row's value) stay out of the response, and the row's
 *       text out of the log (PostgreSQL's {@code Failing row contains (...)}), whoever writes the log line?
 *   <li>Does the advice leave springdoc's API description alone?
 * </ul>
 *
 * <p>The class is not {@code @Transactional}: a stale edit needs two transactions that both commit.
 */
@IntegrationTest
@Import(ErrorHandlingIntegrationTest.ProbeController.class)
class ErrorHandlingIntegrationTest {

    /** 25 MB, the per-file limit in application.yml. Spring's MB is 1024 x 1024 bytes. */
    private static final int LIMIT = 25 * 1024 * 1024;

    /** Text that stands for what a user wrote. No log line may ever hold it. */
    static final String SECRET_ENTRY_TEXT = "private diary line SECRET-7f3a91";

    @LocalServerPort
    int port;

    @Autowired
    RestTestClient client;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    UserRepository users;

    @Autowired
    WeeklyLogRepository weeks;

    @Autowired
    EntryRepository entries;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcClient jdbc;

    UUID userId;

    @AfterEach
    void removeTheTestUser() {
        if (userId != null) {
            jdbc.sql("DELETE FROM users WHERE id = :id").param("id", userId).update();
        }
    }

    // 409 entry-stale, from a real Hibernate failure

    @Test
    void anEditThatLosesARaceIs409EntryStaleAndTheOtherDevicesEditSurvives() {
        UUID entryId = anEntryAtVersionZero();

        client.post()
                .uri("/test-support/lose-the-race/{user}/{entry}", userId, entryId)
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class)
                .value(body -> {
                    DocumentContext json = JsonPath.parse(body);
                    assertThat(json.<String>read("$.type")).isEqualTo("urn:memoirist:problem:entry-stale");
                    assertThat(json.<String>read("$.title")).isEqualTo("Entry has changed");
                    // Hibernate's own message names the entity class and the row id. None of it reaches the client.
                    // (The instance is the request path, which holds the id because this probe puts it there.)
                    assertThat(json.<String>read("$.detail"))
                            .doesNotContain("Row was updated")
                            .doesNotContain("com.memoirist")
                            .doesNotContain(entryId.toString());
                    assertThat(body).doesNotContain("Row was updated").doesNotContain("com.memoirist");
                });

        assertThat(titleInDatabase(entryId)).isEqualTo("The other device got there first");
    }

    @Test
    void entryIsTheOnlyEntityWithAVersion() {
        // The advice answers every optimistic-locking failure as "entry-stale". That is only true while Entry is the
        // only entity that has a @Version. A second one would make this test fail, which is the reminder to tell the
        // two failures apart in GlobalExceptionHandler.
        List<String> versioned = entityManagerFactory.getMetamodel().getEntities().stream()
                .filter(entity -> entity.hasVersionAttribute())
                .map(entity -> entity.getJavaType().getSimpleName())
                .toList();

        assertThat(versioned).containsExactly("Entry");
    }

    // 413 from the real multipart limit

    @Test
    void aFileOver25MbIs413PayloadTooLarge() throws Exception {
        // One kilobyte over the limit, so Tomcat stops reading almost at the end and the response gets through.
        HttpResponse<String> response = postFile(LIMIT + 1024);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        DocumentContext json = JsonPath.parse(response.body());
        assertThat(json.<String>read("$.type")).isEqualTo("urn:memoirist:problem:payload-too-large");
        assertThat(json.<String>read("$.title")).isEqualTo("Payload too large");
        assertThat(json.<Integer>read("$.status")).isEqualTo(413);
    }

    @Test
    void aFileJustUnderTheLimitIsAccepted() throws Exception {
        // The control: the same endpoint, a file that fits. Without it, a broken endpoint would pass the test above.
        HttpResponse<String> response = postFile(LIMIT - 1024);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(String.valueOf(LIMIT - 1024));
    }

    @Test
    void aMultipartRequestWithoutTheFilePartIs400AndNamesThePart() throws Exception {
        // The endpoint wants a part called "file". This one sends a part called "other".
        HttpResponse<String> response = postPart("other", 10);

        assertThat(response.statusCode()).isEqualTo(400);
        DocumentContext json = JsonPath.parse(response.body());
        assertThat(json.<String>read("$.type")).isEqualTo("urn:memoirist:problem:malformed-request");
        assertThat(json.<String>read("$.detail")).isEqualTo("Required part 'file' is missing.");
    }

    // 500 from a real database error

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aDatabaseErrorIs500AndNeverShowsTheSqlOrTheConstraint(CapturedOutput output) {
        // The probe inserts a second user with the dev user's email. Postgres refuses it, and the exception message
        // holds the SQL, the constraint name and the email.
        client.post()
                .uri("/test-support/break-a-constraint")
                .exchange()
                .expectStatus()
                .isEqualTo(500)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class)
                .value(body -> {
                    DocumentContext json = JsonPath.parse(body);
                    assertThat(json.<String>read("$.type")).isEqualTo("urn:memoirist:problem:internal-error");
                    assertThat(json.<String>read("$.detail"))
                            .isEqualTo("Something went wrong on our side. Please try again.");
                    assertThat(body)
                            .doesNotContain("INSERT")
                            .doesNotContain("users_email_key")
                            .doesNotContain("dev@memoirist.local")
                            .doesNotContain("duplicate key")
                            .doesNotContain("PSQL")
                            .doesNotContain("\"trace\"");
                });

        // The log keeps the classes, the SQLState (23505 is a duplicate key) and the frames. It does not keep the
        // message, which for a unique-key failure holds the value: "Key (email)=(dev@memoirist.local) already exists".
        assertThat(output.getAll())
                .contains("Unhandled exception on POST /test-support/break-a-constraint")
                .contains("PSQLException (SQLState 23505)")
                .contains("at com.memoirist.common.ErrorHandlingIntegrationTest$ProbeController.breakAConstraint")
                .doesNotContain("dev@memoirist.local")
                .doesNotContain("already exists");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aCheckConstraintFailureNeverPutsTheRowsTextInTheLog(CapturedOutput output) {
        // PostgreSQL ends a CHECK failure with "Detail: Failing row contains (...)", and that row holds the entry's
        // title and text. A sealed Loot Drop's would be there too (D-10). The probe saves an entry with a title of
        // only spaces (chk_entries_title) and a recognisable text, through Hibernate like a real service would.
        UUID weekId = aWeek();

        client.post()
                .uri("/test-support/break-a-check/{user}/{week}", userId, weekId)
                .exchange()
                .expectStatus()
                .isEqualTo(500)
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .doesNotContain(SECRET_ENTRY_TEXT)
                        .doesNotContain("Failing row")
                        .doesNotContain("chk_entries_title"));

        String log = output.getAll();
        // The failure was logged, with its classes and the SQLState (23514 is a failed CHECK) ...
        assertThat(log)
                .contains("Unhandled exception on POST /test-support/break-a-check/")
                .contains("PSQLException (SQLState 23514)")
                // ... and nothing in the log, ours or a library's, holds the row.
                .doesNotContain(SECRET_ENTRY_TEXT)
                .doesNotContain("Failing row contains");
    }

    // Spring's own errors and the API description, in the whole app

    @Test
    void anUnknownPathInTheRealAppIs404WithOurProblemBody() {
        client.get()
                .uri("/api/v1/nothing-here")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:not-found")
                .jsonPath("$.title")
                .isEqualTo("Not found");
    }

    @Test
    void theAdviceAddsNoMadeUpResponsesToTheApiDescription() {
        // springdoc copies the @ResponseStatus of every @ControllerAdvice handler into every operation. Our handlers
        // have none, so an operation lists only what its own controller says. If a handler ever gets one, every
        // endpoint in /v3/api-docs would suddenly claim that error. Error responses are documented per endpoint with
        // @ApiResponse (.claude/rules/api.md).
        client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> {
                    Map<String, Object> responses =
                            JsonPath.parse(body).read("$.paths['/test-support/openapi-probe'].get.responses");
                    assertThat(responses.keySet()).containsExactly("200");
                });
    }

    // Helpers

    /**
     * Sends one multipart file with a known {@code Content-Length}, the way a browser's {@code FormData} upload does.
     * Spring's own test client streams the body as chunked, and then an early {@code 413} can surface as a dropped
     * connection (see the journal), so it would test the client's habits instead of the server.
     */
    private HttpResponse<String> postFile(int fileBytes) throws Exception {
        return postPart("file", fileBytes);
    }

    private HttpResponse<String> postPart(String partName, int fileBytes) throws Exception {
        String boundary = "memoirist-test-boundary";
        byte[] head = ("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"" + partName + "\"; filename=\"photo.jpg\"\r\n"
                        + "Content-Type: image/jpeg\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/test-support/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head, new byte[fileBytes], tail)))
                .build();
        try (HttpClient http =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private UUID aWeek() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            User user = users.save(Fixtures.user());
            WeeklyLog week = weeks.save(Fixtures.week(user.getId()));
            userId = user.getId();
            return week.getId();
        });
    }

    private UUID anEntryAtVersionZero() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            User user = users.save(Fixtures.user());
            WeeklyLog week = weeks.save(Fixtures.week(user.getId()));
            Entry entry = entries.save(Fixtures.entry(user.getId(), week));
            userId = user.getId();
            return entry.getId();
        });
    }

    private String titleInDatabase(UUID entryId) {
        return jdbc.sql("SELECT title FROM entries WHERE id = :id")
                .param("id", entryId)
                .query(String.class)
                .single();
    }

    // Endpoints that exist only for this test

    /** Test code only: {@code @TestComponent} keeps it out of the app and out of every other test's context. */
    @TestComponent
    @RestController
    static class ProbeController {

        private final EntryRepository entries;
        private final JdbcClient jdbc;
        private final TransactionTemplate tx;
        private final TransactionTemplate otherDevice;

        ProbeController(EntryRepository entries, JdbcClient jdbc, PlatformTransactionManager transactionManager) {
            this.entries = entries;
            this.jdbc = jdbc;
            this.tx = new TransactionTemplate(transactionManager);
            // REQUIRES_NEW: a second transaction on its own connection, even in the middle of the first. It plays
            // "the other device" committing while this request is still working.
            this.otherDevice = new TransactionTemplate(transactionManager);
            this.otherDevice.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        /**
         * What a service does when the version check passed and then a second request commits first: this request
         * loads the entry at version 0, the other device edits it and commits, and this one's commit finds the row
         * changed.
         */
        @PostMapping("/test-support/lose-the-race/{userId}/{entryId}")
        String loseTheRace(@PathVariable UUID userId, @PathVariable UUID entryId) {
            tx.executeWithoutResult(status -> {
                Entry mine = entries.findByIdAndUserId(entryId, userId).orElseThrow();
                otherDevice.executeWithoutResult(other -> entries.findByIdAndUserId(entryId, userId)
                        .orElseThrow()
                        .setTitle("The other device got there first"));
                mine.setTitle("Mine, based on stale data");
            });
            return "unreachable: the commit above throws";
        }

        @PostMapping(path = "/test-support/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        String upload(@RequestPart("file") MultipartFile file) {
            return String.valueOf(file.getSize());
        }

        /** A CHECK failure through Hibernate: a title of only spaces breaks {@code chk_entries_title}. */
        @PostMapping("/test-support/break-a-check/{userId}/{weekId}")
        String breakACheck(@PathVariable UUID userId, @PathVariable UUID weekId) {
            tx.executeWithoutResult(status -> {
                Entry entry = new Entry(UUID.randomUUID(), userId, weekId, Fixtures.MONDAY);
                entry.setTitle("   ");
                entry.setContentText(SECRET_ENTRY_TEXT);
                entries.save(entry);
            });
            return "unreachable: the commit throws";
        }

        @PostMapping("/test-support/break-a-constraint")
        String breakAConstraint() {
            jdbc.sql("INSERT INTO users (id, email) VALUES (:id, 'dev@memoirist.local')")
                    .param("id", UUID.randomUUID())
                    .update();
            return "unreachable: the insert throws";
        }

        @GetMapping("/test-support/openapi-probe")
        Map<String, String> openApiProbe() {
            return Map.of("ok", "true");
        }
    }
}

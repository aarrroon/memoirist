package com.memoirist.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.common.GlobalExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current user over HTTP: the header goes in, the provider reads it from the live request, and a bad header comes
 * out as the {@code 400 malformed-request} problem.
 *
 * <p>This is a web slice ({@code @WebMvcTest}): Spring MVC with the beans listed in {@code @Import} and nothing else, so
 * there is no database and no Docker. There are no real endpoints yet, so the test brings its own, {@link
 * WhoAmIController}. It is a {@code @TestComponent}, which component scanning skips, so it never appears in the app or
 * in another test's context.
 */
@WebMvcTest(controllers = CurrentUserHttpTest.WhoAmIController.class)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import({
    CurrentUserHttpTest.WhoAmIController.class,
    DevHeaderCurrentUserProvider.class,
    GlobalExceptionHandler.class,
    CurrentUserHttpTest.FixedClockConfig.class
})
class CurrentUserHttpTest {

    private static final String ME = "/test-support/me";
    private static final Instant FIXED_NOW = Instant.parse("2026-10-30T01:02:03Z");

    /** A fixed clock, so the test asserts the exact timestamp and proves it comes from the injected Clock. */
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClockConfig {
        @org.springframework.context.annotation.Bean
        Clock clock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    RestTestClient client;

    @Test
    void theHeaderNamesTheUser() {
        UUID someone = UUID.randomUUID();

        client.get()
                .uri(ME)
                .header("X-Dev-User-Id", someone.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.userId")
                .isEqualTo(someone.toString());
    }

    @Test
    void withoutTheHeaderItIsTheSeededDevUser() {
        client.get()
                .uri(ME)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.userId")
                .isEqualTo(Fixtures.DEV_USER_ID.toString());
    }

    @Test
    void aMalformedHeaderIsA400MalformedRequestProblem() {
        // TODO(human) PRACTICE: Prove over HTTP that a bad X-Dev-User-Id is refused. Send "evil-value-123" and
        //   check the status, the content type, the problem's type URI, title and instance, that it has a
        //   timestamp, and that the body does not repeat the value that was sent.
        // Hint: API_CONTRACT (Errors) gives the body's shape: type "urn:memoirist:problem:malformed-request", a
        //   timestamp as an ISO-8601 instant with Z, and an RFC 9457 content type. Parse the timestamp rather than only
        //   checking that it exists. A test that only checks status 400 passes for any other 400 as well.
        // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
        client.get()
                .uri(ME)
                .header("X-Dev-User-Id", "evil-value-123")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:malformed-request")
                .jsonPath("$.title")
                .isEqualTo("Malformed request")
                .jsonPath("$.status")
                .isEqualTo(400)
                .jsonPath("$.instance")
                .isEqualTo(ME)
                .jsonPath("$.detail")
                .value(
                        String.class,
                        detail -> assertThat(detail).contains("X-Dev-User-Id").doesNotContain("evil-value-123"))
                .jsonPath("$.timestamp")
                .isEqualTo(FIXED_NOW.toString());
        // └── END ANSWER ▲ ──
    }

    @Test
    void anEmptyHeaderIsAlsoA400NotTheDevUser() {
        client.get()
                .uri(ME)
                .header("X-Dev-User-Id", "")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:malformed-request");
    }

    @Test
    void theErrorBodyHasNoStackTrace() {
        client.get()
                .uri(ME)
                .header("X-Dev-User-Id", "nope")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .doesNotContain("MalformedRequestException")
                        .doesNotContain("com.memoirist")
                        .doesNotContain("at java."));
    }

    @Test
    void requestsAtTheSameTimeEachSeeTheirOwnHeader() throws Exception {
        // The provider is one object shared by every request. If it kept the request (or the answer) in a field, two
        // requests in flight would see each other's user. Release 64 requests at once, each with its own id.
        int requests = 64;
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService threads = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < requests; i++) {
                UUID mine = UUID.randomUUID();
                results.add(threads.submit(() -> {
                    go.await();
                    Map<?, ?> body = client.get()
                            .uri(ME)
                            .header("X-Dev-User-Id", mine.toString())
                            .exchange()
                            .expectStatus()
                            .isOk()
                            .expectBody(Map.class)
                            .returnResult()
                            .getResponseBody();
                    return mine.toString().equals(body.get("userId"));
                }));
            }
            go.countDown();
            for (Future<Boolean> result : results) {
                try {
                    assertThat(result.get())
                            .as("a request saw another request's user")
                            .isTrue();
                } catch (ExecutionException e) {
                    throw new AssertionError("a concurrent request failed", e.getCause());
                }
            }
        }
    }

    /** An endpoint that only says who the current user is. Test code only. */
    @TestComponent
    @RestController
    static class WhoAmIController {

        private final CurrentUserProvider currentUser;

        WhoAmIController(CurrentUserProvider currentUser) {
            this.currentUser = currentUser;
        }

        @GetMapping(ME)
        Map<String, UUID> me() {
            return Map.of("userId", currentUser.currentUserId());
        }
    }
}

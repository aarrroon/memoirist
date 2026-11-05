package com.memoirist.music;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.EverythingLogged;
import com.memoirist.MutableClock;
import com.memoirist.common.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.client.RestClient;

/**
 * {@code POST /api/v1/music/search} over HTTP (API_CONTRACT, Music) with the real controller, the real advice and the
 * real {@link MusicSearchService}, talking to a stubbed Spotify. A web slice: no database, no Docker.
 *
 * <p>The service lives as long as the test context, so its token cache does too. {@link #aFreshTokenEveryTest} moves
 * the clock past the token's end before each test, so every test logs in again against its own stubs.
 */
@WebMvcTest(controllers = MusicController.class)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import({GlobalExceptionHandler.class, MusicSearchHttpTest.StubbedSpotify.class})
class MusicSearchHttpTest {

    private static final String SEARCH = "/api/v1/music/search";
    private static final String TRACK = "spotify:track:1a2B3c4D5e6F7g8H9i0J1k";

    /** The service with a stub in place of Spotify, and the handles a test needs to drive it. */
    @TestConfiguration(proxyBeanMethods = false)
    static class StubbedSpotify {

        @Bean
        MutableClock clock() {
            return new MutableClock(Instant.parse("2026-10-31T01:00:00Z"));
        }

        @Bean
        MockRestServiceServer spotify(RestClientHolder holder) {
            return holder.server();
        }

        @Bean
        RestClientHolder holder() {
            RestClient.Builder builder = RestClient.builder();
            return new RestClientHolder(
                    builder, MockRestServiceServer.bindTo(builder).build());
        }

        @Bean
        MusicSearchService musicSearchService(MutableClock clock, RestClientHolder holder) {
            return new MusicSearchService(new SpotifyProperties("id", "secret"), clock, holder.builder());
        }
    }

    record RestClientHolder(RestClient.Builder builder, MockRestServiceServer server) {}

    @Autowired
    RestTestClient client;

    @Autowired
    MockRestServiceServer spotify;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void aFreshTokenEveryTest() {
        spotify.reset();
        clock.advance(Duration.ofHours(2));
    }

    // 200

    @Test
    void aSearchAnswersWithTheResultsInTheContractShape() {
        spotifyAnswers(searchAnswer());

        String body = new String(client.post()
                .uri(SEARCH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", "queen"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .returnResult()
                .getResponseBody());

        assertThat(JsonPath.<Map<String, Object>>read(body, "$")).containsOnlyKeys("items");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.items[0]"))
                .containsOnlyKeys("uri", "kind", "title", "subtitle", "imageUrl")
                .containsEntry("uri", TRACK)
                .containsEntry("kind", "TRACK")
                .containsEntry("title", "Bohemian Rhapsody")
                .containsEntry("subtitle", "Queen")
                .containsEntry("imageUrl", "https://i.scdn.co/image/abc");
        spotify.verify();
    }

    @Test
    void theWordsAreStrippedBeforeTheyAreSentOn() {
        spotify.expect(
                        manyTimes(),
                        request -> assertThat(request.getURI().getRawPath()).isEqualTo("/api/token"))
                .andRespond(withSuccess("{\"access_token\":\"t\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        spotify.expect(request -> assertThat(request.getURI().getRawQuery()).startsWith("q=queen&type="))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.post()
                .uri(SEARCH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", "   queen \t"))
                .exchange()
                .expectStatus()
                .isOk();

        spotify.verify();
    }

    @Test
    void aQueryOf200CharactersIsAccepted() {
        spotifyAnswers("{}");

        client.post()
                .uri(SEARCH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", "q".repeat(200)))
                .exchange()
                .expectStatus()
                .isOk();
    }

    // 400 and 415

    @Test
    void aMissingBlankOrTooLongQueryIs400WithFieldErrorsAndNeverEchoesTheWords() {
        String tooLong = "private-words-" + "x".repeat(200);
        for (Object query : new Object[] {null, "", "   ", tooLong}) {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("query", query);

            String answer = new String(client.post()
                    .uri(SEARCH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange()
                    .expectStatus()
                    .isBadRequest()
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody()
                    .returnResult()
                    .getResponseBody());

            assertThat(JsonPath.<String>read(answer, "$.type")).isEqualTo("urn:memoirist:problem:validation-failed");
            assertThat(JsonPath.<Map<String, String>>read(answer, "$.fieldErrors"))
                    .containsOnlyKeys("query");
            assertThat(answer).doesNotContain("private-words");
        }
        // Spotify was never called for any of them: the mock has no expectation, so a call would have failed.
        spotify.verify();
    }

    @Test
    void anEmptyBodyOrBrokenJsonIs400MalformedRequest() {
        for (String body : new String[] {"", "{", "[\"queen\"]"}) {
            client.post()
                    .uri(SEARCH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody()
                    .jsonPath("$.type")
                    .isEqualTo("urn:memoirist:problem:malformed-request");
        }
    }

    @Test
    void aBodyThatIsNotJsonIs415() {
        client.post()
                .uri(SEARCH)
                .contentType(MediaType.TEXT_PLAIN)
                .body("queen")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:unsupported-media-type");
    }

    // The words are in the body and nowhere in the URL

    @Test
    void theWordsAreReadFromTheBodyAndNeverFromTheUrl() {
        // Words in the query string and none in the body: refused, so a client that put them in the URL finds out.
        client.post().uri(SEARCH + "?query=queen").exchange().expectStatus().is4xxClientError();
        client.post()
                .uri(SEARCH + "?query=queen")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void aGetIsNotAnOperationSoThereIsNoUrlThatTakesWords() {
        client.get().uri(SEARCH + "?query=queen").exchange().expectStatus().isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        client.get().uri("/api/v1/music/search/queen").exchange().expectStatus().isNotFound();
    }

    @Test
    void theRequestObjectPrintsWithoutTheWords() {
        assertThat(new com.memoirist.music.dto.MusicSearchRequest("private words").toString())
                .doesNotContain("private")
                .contains("MusicSearchRequest");
    }

    // 503

    @Test
    void a429FromSpotifyIs503MusicSearchUnavailableWithAFixedSentence() {
        spotifyLogsIn();
        spotify.expect(request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        client.post()
                .uri(SEARCH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", "private words"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:music-search-unavailable")
                .jsonPath("$.title")
                .isEqualTo("Music search unavailable")
                .jsonPath("$.status")
                .isEqualTo(503)
                .jsonPath("$.detail")
                .isEqualTo("Spotify isn't answering searches right now.")
                .jsonPath("$.instance")
                .isEqualTo(SEARCH);
    }

    @Test
    void noLogLineOfASuccessfulOrAFailedRequestHoldsTheWords() {
        String words = "my-private-WORDS-9913";
        spotifyLogsIn();
        spotify.expect(request -> assertThat(request.getURI().getRawQuery()).contains("ok-" + words))
                .andRespond(withSuccess(searchAnswer(), MediaType.APPLICATION_JSON));
        spotify.expect(request -> assertThat(request.getURI().getRawQuery()).contains("limited-" + words))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        try (EverythingLogged log = EverythingLogged.capture()) {
            for (String query :
                    new String[] {"ok-" + words, "limited-" + words, "", words.repeat(30), words + "\", \"x\": [1,"}) {
                client.post()
                        .uri(SEARCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        // Bytes, not a Map: the TEST's own client logs a Map body at DEBUG, in this same JVM, under
                        // the same logger name as the app's RestClient. It prints a byte[] as its type only.
                        .body(("{\"query\":\"" + query + "\"}").getBytes(StandardCharsets.UTF_8))
                        .exchange();
            }

            assertThat(log.lines())
                    .as("something was logged, so the check is not empty")
                    .isNotEmpty();
            assertThat(log.text()).doesNotContain(words);
        }
    }

    // Helpers

    private void spotifyLogsIn() {
        spotify.expect(
                        manyTimes(),
                        request -> assertThat(request.getURI().getRawPath()).isEqualTo("/api/token"))
                .andRespond(withSuccess("{\"access_token\":\"t\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
    }

    private void spotifyAnswers(String json) {
        spotifyLogsIn();
        spotify.expect(request -> assertThat(request.getURI().getRawPath()).isEqualTo("/v1/search"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static String searchAnswer() {
        return """
                {"tracks":{"items":[{"uri":"%s","name":"Bohemian Rhapsody","artists":[{"name":"Queen"}],
                  "album":{"images":[{"url":"https://i.scdn.co/image/abc"}]}}]}}
                """.formatted(TRACK);
    }
}

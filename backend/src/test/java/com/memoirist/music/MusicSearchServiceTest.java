package com.memoirist.music;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.memoirist.EverythingLogged;
import com.memoirist.MutableClock;
import com.memoirist.common.MusicSearchUnavailableException;
import com.memoirist.common.ValidationFailedException;
import com.memoirist.music.dto.MusicKind;
import com.memoirist.music.dto.MusicResult;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

/**
 * {@link MusicSearchService} against a stubbed Spotify: a {@code MockRestServiceServer} bound to the
 * {@code RestClient.Builder} the service is built from, so no request ever leaves the JVM and no Spotify account is
 * needed. Each test says what Spotify would answer ({@code server.expect}) and then what our side did.
 *
 * <p>The server is set to {@code ignoreExpectOrder}, because several tests care how many times a call is made and not
 * which comes first, and every test ends with {@code server.verify()}: a call that was expected but never made
 * fails, and a call nobody expected fails when it is made.
 */
class MusicSearchServiceTest {

    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret-ZZ";
    private static final Instant START = Instant.parse("2026-10-31T01:00:00Z");

    private MutableClock clock;
    private MockRestServiceServer spotify;
    private MusicSearchService service;

    @BeforeEach
    void aServiceTalkingToAStubbedSpotify() {
        clock = new MutableClock(START);
        RestClient.Builder builder = RestClient.builder();
        spotify = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        service = new MusicSearchService(new SpotifyProperties(CLIENT_ID, CLIENT_SECRET), clock, builder);
    }

    // What we send to Spotify

    @Test
    void itLogsInWithTheClientCredentialsFlowAndSearchesWithTheToken() {
        spotify.expect(once(), request -> assertThat(request.getURI()).hasToString(MusicSearchService.TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    HttpHeaders headers = request.getHeaders();
                    String basic = Base64.getEncoder()
                            .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
                    assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Basic " + basic);
                    assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_FORM_URLENCODED);
                    assertThat(body(request)).isEqualTo("grant_type=client_credentials");
                })
                .andRespond(withSuccess(token("the-token", 3600), MediaType.APPLICATION_JSON));
        spotify.expect(once(), searchFor("bohemian rhapsody"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                        .isEqualTo("Bearer the-token"))
                .andRespond(withSuccess(answer(1, 1, 1), MediaType.APPLICATION_JSON));

        List<MusicResult> items = service.search("bohemian rhapsody").items();

        assertThat(items)
                .extracting(MusicResult::kind)
                .containsExactly(MusicKind.TRACK, MusicKind.ALBUM, MusicKind.PLAYLIST);
        spotify.verify();
    }

    @Test
    void theSearchAsksForTheThreeKindsAtTheMostSpotifyAllowsEach() {
        loginAnswers(1);
        spotify.expect(once(), request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/v1/search");
                    assertThat(params(request.getURI()))
                            .containsEntry("type", "track,album,playlist")
                            .containsEntry("limit", "10");
                })
                .andRespond(withSuccess(answer(0, 0, 0), MediaType.APPLICATION_JSON));

        service.search("anything");

        spotify.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "rock & roll",
                "a&type=artist&limit=50",
                "1+1=2",
                "100% pure #1 \"hits\"",
                "Beyoncé – Halo (café)",
                "a%26type%3Dartist"
            })
    void theWordsStayOneQueryParameterWhateverTheyHold(String words) {
        loginAnswers(1);
        spotify.expect(once(), request -> {
                    // Decoded, the parameter is exactly what the user typed, and no parameter was added or changed.
                    assertThat(params(request.getURI()))
                            .containsOnlyKeys("q", "type", "limit")
                            .containsEntry("q", words)
                            .containsEntry("type", "track,album,playlist")
                            .containsEntry("limit", "10");
                })
                .andRespond(withSuccess(answer(0, 0, 0), MediaType.APPLICATION_JSON));

        service.search(words);

        spotify.verify();
    }

    // What comes back

    @Test
    void atMostTenResultsComeBackWhateverSpotifyReturns() {
        loginAnswers(1);
        spotify.expect(once(), searchFor("queen"))
                .andRespond(withSuccess(answer(10, 10, 10), MediaType.APPLICATION_JSON));

        List<MusicResult> items = service.search("queen").items();

        assertThat(items).hasSize(10);
        assertThat(items).extracting(MusicResult::uri).doesNotHaveDuplicates();
        // They take turns, so ten tracks do not crowd out the albums and playlists.
        assertThat(items)
                .extracting(MusicResult::kind)
                .containsExactly(
                        MusicKind.TRACK,
                        MusicKind.ALBUM,
                        MusicKind.PLAYLIST,
                        MusicKind.TRACK,
                        MusicKind.ALBUM,
                        MusicKind.PLAYLIST,
                        MusicKind.TRACK,
                        MusicKind.ALBUM,
                        MusicKind.PLAYLIST,
                        MusicKind.TRACK);
    }

    @Test
    void ifSpotifyHasOnlyTracksAllTenAreTracks() {
        loginAnswers(1);
        spotify.expect(once(), searchFor("queen"))
                .andRespond(withSuccess(answer(10, 0, 0), MediaType.APPLICATION_JSON));

        assertThat(service.search("queen").items()).hasSize(10).allMatch(result -> result.kind() == MusicKind.TRACK);
    }

    @Test
    void fewerThanTenAreAllReturnedAndNothingIsAnEmptyListNotAnError() {
        loginAnswers(2);
        spotify.expect(once(), searchFor("a")).andRespond(withSuccess(answer(2, 1, 0), MediaType.APPLICATION_JSON));
        spotify.expect(once(), searchFor("zzzz")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(service.search("a").items()).hasSize(3);
        assertThat(service.search("zzzz").items()).isNotNull().isEmpty();
    }

    // The token

    @Test
    void theTokenIsFetchedOnceAndReusedForLaterSearches() {
        // TODO(human) PRACTICE: Prove the token is fetched once and reused. Stub Spotify with ONE login answer and
        //   THREE search answers, run three searches, and check that every search carried the same token and that
        //   Spotify saw exactly one login. Then say what would make this test fail if accessToken() had no cache.
        // Hint: MockRestServiceServer.expect(once(), ...) and expect(times(3), ...) count calls, and verify() fails
        //   when the counts are off. A fourth request nobody expected fails by itself. The clock is a MutableClock
        //   that stands still, so no time passes between the searches; the next test moves it.
        // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
        spotify.expect(once(), request -> assertThat(request.getURI()).hasToString(MusicSearchService.TOKEN_URL))
                .andRespond(withSuccess(token("one-token-for-all", 3600), MediaType.APPLICATION_JSON));
        spotify.expect(
                        times(3),
                        request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/search"))
                .andExpect(request -> assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                        .isEqualTo("Bearer one-token-for-all"))
                .andRespond(withSuccess(answer(1, 0, 0), MediaType.APPLICATION_JSON));

        service.search("one");
        service.search("two");
        service.search("three");

        spotify.verify();
        // └── END ANSWER ▲ ──
    }

    @Test
    void aTokenIsReplacedOneMinuteBeforeSpotifySaysItEnds() {
        spotify.expect(times(2), request -> assertThat(request.getURI()).hasToString(MusicSearchService.TOKEN_URL))
                .andRespond(withSuccess(token("t", 3600), MediaType.APPLICATION_JSON));
        spotify.expect(
                        times(3),
                        request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/search"))
                .andRespond(withSuccess(answer(0, 0, 0), MediaType.APPLICATION_JSON));

        service.search("first");
        clock.advance(Duration.ofSeconds(3600 - 60 - 1));
        service.search("one second before the margin: still the same token");
        clock.advance(Duration.ofSeconds(1));
        service.search("at the margin: a new token");

        spotify.verify();
    }

    @Test
    void manySearchesAtTheSameMomentCauseOneLogin() throws Exception {
        int searches = 8;
        spotify.expect(once(), request -> assertThat(request.getURI()).hasToString(MusicSearchService.TOKEN_URL))
                .andRespond(withSuccess(token("shared", 3600), MediaType.APPLICATION_JSON));
        spotify.expect(
                        times(searches),
                        request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/search"))
                .andRespond(withSuccess(answer(1, 0, 0), MediaType.APPLICATION_JSON));
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(searches);
        try {
            List<Future<?>> running = new ArrayList<>();
            IntStream.range(0, searches)
                    .forEach(i -> running.add(pool.submit(() -> {
                        go.await();
                        return service.search("burst " + i);
                    })));
            go.countDown();
            for (Future<?> search : running) {
                search.get();
            }
        } finally {
            pool.shutdownNow();
        }

        spotify.verify();
    }

    @Test
    void aTokenWithoutALifetimeIsUsedForThatSearchOnly() {
        spotify.expect(times(2), request -> assertThat(request.getURI()).hasToString(MusicSearchService.TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"short\"}", MediaType.APPLICATION_JSON));
        spotify.expect(
                        times(2),
                        request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/search"))
                .andRespond(withSuccess(answer(0, 0, 0), MediaType.APPLICATION_JSON));

        service.search("first");
        service.search("second");

        spotify.verify();
    }

    // When it is off or Spotify says no

    @ParameterizedTest
    @CsvSource(
            nullValues = "NULL",
            value = {"NULL,NULL", "'',''", "id,''", "'',secret", "'  ',secret", "id,'  '", "id,NULL"})
    void withoutBothSettingsSearchIsUnavailableAndSpotifyIsNeverCalled(String id, String secret) {
        MusicSearchService off = new MusicSearchService(new SpotifyProperties(id, secret), clock, RestClient.builder());

        assertThatThrownBy(() -> off.search("anything"))
                .isInstanceOf(MusicSearchUnavailableException.class)
                .hasMessage("Music search isn't set up on this server.")
                .hasNoCause();
        // The mock server of the other tests is not bound to this builder: a real call would have failed with an
        // I/O error (a cause), and there is none.
    }

    @Test
    void a429FromTheSearchIsUnavailable() {
        loginAnswers(1);
        spotify.expect(once(), searchFor("busy"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "30"));

        assertThatThrownBy(() -> service.search("busy"))
                .isInstanceOf(MusicSearchUnavailableException.class)
                .hasMessage("Spotify isn't answering searches right now.")
                .hasCauseInstanceOf(org.springframework.web.client.HttpClientErrorException.TooManyRequests.class);
        spotify.verify();
    }

    @Test
    void a429FromTheLoginIsUnavailableToo() {
        spotify.expect(once(), request -> assertThat(request.getURI()).hasToString(MusicSearchService.TOKEN_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> service.search("busy"))
                .isInstanceOf(MusicSearchUnavailableException.class)
                .hasCauseInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
        spotify.verify();
    }

    @ParameterizedTest
    @CsvSource({"400", "403", "404", "500", "502", "503"})
    void anyOtherRefusalIsUnavailable(int status) {
        loginAnswers(1);
        spotify.expect(once(), searchFor("x")).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> service.search("x")).isInstanceOf(MusicSearchUnavailableException.class);
    }

    @Test
    void aNetworkFailureIsUnavailableAndItsMessageMayHoldTheUrlSoOnlyTheCauseCarriesIt() {
        loginAnswers(1);
        spotify.expect(once(), searchFor("my secret words")).andRespond(request -> {
            throw new IOException("connection reset");
        });

        assertThatThrownBy(() -> service.search("my secret words"))
                .isInstanceOf(MusicSearchUnavailableException.class)
                // The message that reaches the client names no word of the query.
                .hasMessage("Spotify isn't answering searches right now.")
                .hasCauseInstanceOf(org.springframework.web.client.ResourceAccessException.class);
    }

    @Test
    void aLoginAnswerWithoutAnAccessTokenIsUnavailable() {
        spotify.expect(once(), request -> assertThat(request.getURI()).hasToString(MusicSearchService.TOKEN_URL))
                .andRespond(withSuccess("{\"error\":\"invalid_client\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.search("x")).isInstanceOf(MusicSearchUnavailableException.class);
    }

    @Test
    void anAnswerThatIsNotJsonIsUnavailable() {
        loginAnswers(1);
        spotify.expect(once(), searchFor("x")).andRespond(withSuccess("<html>oops</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> service.search("x")).isInstanceOf(MusicSearchUnavailableException.class);
    }

    @Test
    void aTokenSpotifyRefusesWith401IsDroppedSoTheNextSearchLogsInAgain() {
        spotify.expect(times(2), request -> assertThat(request.getURI()).hasToString(MusicSearchService.TOKEN_URL))
                .andRespond(withSuccess(token("revoked-or-fresh", 3600), MediaType.APPLICATION_JSON));
        spotify.expect(once(), searchFor("first")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        spotify.expect(once(), searchFor("second"))
                .andRespond(withSuccess(answer(1, 0, 0), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.search("first")).isInstanceOf(MusicSearchUnavailableException.class);
        assertThat(service.search("second").items()).hasSize(1);

        spotify.verify();
    }

    @Test
    void aFailureDoesNotKeepTheTokenFromBeingReused() {
        loginAnswers(1);
        spotify.expect(once(), searchFor("limited")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        spotify.expect(once(), searchFor("later")).andRespond(withSuccess(answer(1, 0, 0), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.search("limited")).isInstanceOf(MusicSearchUnavailableException.class);
        assertThat(service.search("later").items()).hasSize(1);

        spotify.verify();
    }

    // The words

    @ParameterizedTest
    @CsvSource(
            nullValues = "NULL",
            value = {"NULL,must not be blank", "'',must not be blank", "'   ',must not be blank"})
    void aMissingOrBlankQueryIsRefusedBeforeAnythingIsSent(String query, String message) {
        assertThatThrownBy(() -> service.search(query))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> assertThat(((ValidationFailedException) e).getFieldErrors())
                        .containsExactly(Map.entry("query", message)));
        spotify.verify();
    }

    @Test
    void aQueryOver200CharactersIsRefusedAndOneOf200IsNot() {
        String tooLong = "private-words-" + "x".repeat(200);
        assertThatThrownBy(() -> service.search(tooLong))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessage("query size must be between 1 and 200")
                .satisfies(e -> assertThat(((ValidationFailedException) e).getFieldErrors())
                        .containsExactly(Map.entry("query", "size must be between 1 and 200")));

        loginAnswers(1);
        spotify.expect(once(), searchFor("q".repeat(200)))
                .andRespond(withSuccess(answer(0, 0, 0), MediaType.APPLICATION_JSON));
        assertThat(service.search("q".repeat(200)).items()).isEmpty();
    }

    @Test
    void aBadQueryIsRefusedEvenWhenSearchIsNotSetUp() {
        MusicSearchService off = new MusicSearchService(new SpotifyProperties("", ""), clock, RestClient.builder());

        assertThatThrownBy(() -> off.search(" ")).isInstanceOf(ValidationFailedException.class);
    }

    // Nothing private in the logs

    @Test
    void noLogLineHoldsTheWordsTheTokenOrTheSecretWhateverTheLoggingLevel() {
        String words = "my-very-private-search-WORDS-4471";
        loginAnswers(1);
        for (String fine : List.of("fine one", "fine two " + words, "fine three")) {
            spotify.expect(once(), searchFor(fine))
                    .andRespond(withSuccess(answer(2, 2, 2), MediaType.APPLICATION_JSON));
        }
        spotify.expect(once(), searchFor(words)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        spotify.expect(once(), searchFor("io-" + words)).andRespond(request -> {
            throw new IOException("reset while reading https://api.spotify.com/v1/search?q=io-" + words);
        });

        try (EverythingLogged log = EverythingLogged.capture()) {
            service.search("fine one");
            service.search("fine two " + words);
            service.search("fine three");
            assertThatThrownBy(() -> service.search(words)).isInstanceOf(MusicSearchUnavailableException.class);
            assertThatThrownBy(() -> service.search("io-" + words)).isInstanceOf(MusicSearchUnavailableException.class);

            assertThat(log.text())
                    .as(
                            "everything logged by every logger at TRACE, %d lines",
                            log.lines().size())
                    .doesNotContain(words)
                    .doesNotContain(CLIENT_SECRET)
                    .doesNotContain("the-token");
        }
    }

    // Helpers

    /** Spotify answers the login {@code times} times with a token that lasts an hour. */
    private void loginAnswers(int logins) {
        spotify.expect(times(logins), request -> assertThat(request.getURI()).hasToString(MusicSearchService.TOKEN_URL))
                .andRespond(withSuccess(token("the-token", 3600), MediaType.APPLICATION_JSON));
    }

    /** Matches the search call whose {@code q} is exactly these words. */
    private static RequestMatcher searchFor(String words) {
        return request -> {
            assertThat(request.getURI().getPath()).isEqualTo("/v1/search");
            assertThat(params(request.getURI())).containsEntry("q", words);
        };
    }

    /**
     * The query parameters as they arrived: split on the raw {@code &} first, then each value percent-decoded, so a
     * {@code &} or {@code =} the user typed (sent as {@code %26}, {@code %3D}) is part of a value and not a separator.
     */
    private static Map<String, String> params(URI uri) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            int equals = pair.indexOf('=');
            params.put(pair.substring(0, equals), UriUtils.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    private static String body(org.springframework.http.client.ClientHttpRequest request) {
        return ((MockClientHttpRequest) request).getBodyAsString();
    }

    private static String token(String value, int expiresIn) {
        return "{\"access_token\":\"%s\",\"token_type\":\"Bearer\",\"expires_in\":%d}".formatted(value, expiresIn);
    }

    /** A search answer with this many tracks, albums and playlists, in Spotify's shape. */
    private static String answer(int tracks, int albums, int playlists) {
        return "{\"tracks\":%s,\"albums\":%s,\"playlists\":%s}"
                .formatted(section(tracks, "track"), section(albums, "album"), section(playlists, "playlist"));
    }

    private static String section(int count, String type) {
        String items = IntStream.rangeClosed(1, count)
                .mapToObj(i -> item(type, i))
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"items\":[" + items + "]}";
    }

    private static String item(String type, int i) {
        // A 22-character id: the type's first letter, then the counter padded with zeros.
        String id = type.charAt(0) + "%021d".formatted(i);
        return switch (type) {
            case "track" ->
                ("{\"uri\":\"spotify:track:%s\",\"name\":\"Song %d\",\"artists\":[{\"name\":\"Artist %d\"}],"
                                + "\"album\":{\"images\":[{\"url\":\"https://i.scdn.co/image/t%d\"}]}}")
                        .formatted(id, i, i, i);
            case "album" ->
                ("{\"uri\":\"spotify:album:%s\",\"name\":\"Album %d\",\"artists\":[{\"name\":\"Artist %d\"}],"
                                + "\"images\":[{\"url\":\"https://i.scdn.co/image/a%d\"}]}")
                        .formatted(id, i, i, i);
            default ->
                ("{\"uri\":\"spotify:playlist:%s\",\"name\":\"List %d\",\"owner\":{\"display_name\":\"Owner %d\"},"
                                + "\"images\":[{\"url\":\"https://i.scdn.co/image/p%d\"}]}")
                        .formatted(id, i, i, i);
        };
    }
}

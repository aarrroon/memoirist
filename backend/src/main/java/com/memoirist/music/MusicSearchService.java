package com.memoirist.music;

import com.memoirist.common.MusicSearchUnavailableException;
import com.memoirist.common.ValidationFailedException;
import com.memoirist.music.dto.MusicSearchResponse;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Searches Spotify for tracks, albums and playlists on the user's behalf (API_CONTRACT, Music; D-35).
 *
 * <ul>
 *   <li><b>App-only token.</b> The server logs in to Spotify as the app, with the client credentials flow, and keeps
 *       the token until shortly before it expires, so a burst of searches costs one login. The user's own Spotify
 *       account is never involved, and neither the secret nor the token ever reaches the browser.
 *   <li><b>Private input.</b> The user's words travel in our request body, and from here to Spotify in the query of
 *       one HTTPS request (Spotify's search is a {@code GET}). They are never logged: this class logs nothing, and
 *       failures leave as {@link MusicSearchUnavailableException} whose cause the advice logs without any message,
 *       because the message of an I/O failure can hold the whole URL.
 *   <li><b>When Spotify says no</b> (not set up, a {@code 429} from a small quota, any other refusal or a timeout) the
 *       answer is {@code 503 music-search-unavailable}, and the client falls back to pasting a link.
 *   <li><b>At most 10 results,</b> mixed across the three kinds. Spotify's {@code limit} is per kind, so asking for
 *       the maximum of each and taking turns gives a spread rather than ten tracks.
 * </ul>
 *
 * <p>There is no database here, so no transaction. The tests never call Spotify: they bind a
 * {@code MockRestServiceServer} to the builder given to the second constructor.
 */
@Service
public class MusicSearchService {

    static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    static final String SEARCH_URL = "https://api.spotify.com/v1/search";

    /** The most results one search returns (API_CONTRACT, Music), and Spotify's own maximum for each kind. */
    static final int MAX_RESULTS = 10;

    /** The longest query (API_CONTRACT, Music). */
    static final int MAX_QUERY_LENGTH = 200;

    /** A token is dropped this long before Spotify says it ends, so a search never starts with one about to expire. */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private static final String NOT_SET_UP = "Music search isn't set up on this server.";
    private static final String REFUSED = "Spotify isn't answering searches right now.";

    private final SpotifyProperties properties;
    private final Clock clock;
    private final RestClient client;

    /** The token in use, and when to stop using it. Guarded by {@code this}. */
    private Token cached;

    @Autowired
    public MusicSearchService(SpotifyProperties properties, Clock clock) {
        this(properties, clock, RestClient.builder().requestFactory(withTimeouts()));
    }

    /** For tests: the caller supplies the builder, so it can bind a mock server to it. */
    MusicSearchService(SpotifyProperties properties, Clock clock, RestClient.Builder builder) {
        this.properties = properties;
        this.clock = clock;
        this.client = builder.build();
    }

    /** Spotify gets a few seconds to answer; a user waiting behind a stuck call would rather paste a link. */
    private static JdkClientHttpRequestFactory withTimeouts() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    /**
     * Finds up to {@value #MAX_RESULTS} results for the words.
     *
     * @throws ValidationFailedException if the words are missing, empty or over {@value #MAX_QUERY_LENGTH} characters
     *     (checked first, so a bad query is {@code 400} whether or not search is set up)
     * @throws MusicSearchUnavailableException if the credentials are missing, or Spotify refuses, fails or times out
     */
    public MusicSearchResponse search(String query) {
        requireQuery(query);
        if (!properties.isConfigured()) {
            throw new MusicSearchUnavailableException(NOT_SET_UP);
        }
        String token = null;
        try {
            token = accessToken();
            JsonNode body = client.get()
                    // {q} is a URI variable: Spring percent-encodes its value completely, so a query holding "&" or
                    // "=" stays one parameter and can't add a type or a limit of its own.
                    .uri(SEARCH_URL + "?q={q}&type=track,album,playlist&limit={limit}", query, MAX_RESULTS)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new MusicSearchUnavailableException(REFUSED);
            }
            return new MusicSearchResponse(SpotifyResults.merge(body, MAX_RESULTS));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401 && token != null) {
                forget(token);
            }
            throw new MusicSearchUnavailableException(REFUSED, e);
        } catch (RestClientException e) {
            throw new MusicSearchUnavailableException(REFUSED, e);
        }
    }

    /**
     * 1 to {@value #MAX_QUERY_LENGTH} characters. The messages are the ones bean validation would give, so the
     * {@code fieldErrors} are what the contract says; none of them repeats the words (they are private, D-29).
     */
    private static void requireQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new ValidationFailedException("query", "must not be blank");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new ValidationFailedException("query", "size must be between 1 and " + MAX_QUERY_LENGTH);
        }
    }

    // TODO(human) PRACTICE: Write accessToken(): hand back a valid Spotify token, asking Spotify for a new one only
    //   when the one we hold is missing or about to end. Many searches can arrive at the same moment, and they must
    //   cause one login between them, not one each. Use the injected Clock, not Instant.now().
    // Hint: D-35 and TECH_STACK (Music). fetchToken(now) does the HTTP call and returns the token with its end time
    //   (already shortened by EXPIRY_MARGIN). Decide what guards the field: a synchronized method is simplest, and the
    //   price is that searches wait while one thread logs in. Compare with a volatile field plus a lock taken only
    //   when the token is stale (double-checked locking), and say what you would pick for a busy service.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    private synchronized String accessToken() {
        Instant now = clock.instant();
        if (cached == null || !now.isBefore(cached.expiresAt())) {
            cached = fetchToken(now);
        }
        return cached.value();
    }
    // └── END ANSWER ▲ ──

    /** Drops the token that Spotify refused, unless another thread has already replaced it. */
    private synchronized void forget(String refused) {
        if (cached != null && cached.value().equals(refused)) {
            cached = null;
        }
    }

    /** One login with the client credentials flow: the id and secret in a Basic header, the grant in a form. */
    private Token fetchToken(Instant now) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        JsonNode body = client.post()
                .uri(TOKEN_URL)
                .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String value = body == null ? "" : body.path("access_token").asString("");
        if (value.isBlank()) {
            throw new MusicSearchUnavailableException(REFUSED);
        }
        long seconds = body.path("expires_in").asLong(0);
        Duration lifetime = Duration.ofSeconds(Math.max(seconds, 0)).minus(EXPIRY_MARGIN);
        return new Token(value, now.plus(lifetime.isNegative() ? Duration.ZERO : lifetime));
    }

    private record Token(String value, Instant expiresAt) {

        /** The default {@code toString()} would print the token. */
        @Override
        public String toString() {
            return "Token[expiresAt=" + expiresAt + "]";
        }
    }
}

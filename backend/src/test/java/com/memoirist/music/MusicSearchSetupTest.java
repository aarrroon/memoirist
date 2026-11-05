package com.memoirist.music;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The whole app with Spotify's two settings empty, which is how it starts on a machine that has no Spotify developer
 * app (and in CI): the app runs, and search says it is off. The settings are set to empty here on purpose, so that a
 * developer who has {@code SPOTIFY_CLIENT_ID} in their shell can't make this test call the real Spotify.
 *
 * <p>It also proves the wiring that the web-slice test skips: {@code application.yml} binds {@code spotify.*} to
 * {@link SpotifyProperties}, and the real controller and advice answer on the real path.
 */
@IntegrationTest
@TestPropertySource(properties = {"spotify.client-id=", "spotify.client-secret="})
class MusicSearchSetupTest {

    @Autowired
    RestTestClient client;

    @Autowired
    SpotifyProperties properties;

    @Test
    void theSettingsAreBoundAndEmpty() {
        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    void searchIs503MusicSearchUnavailableAndSaysItIsNotSetUp() {
        client.post()
                .uri("/api/v1/music/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", "queen"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:music-search-unavailable")
                .jsonPath("$.detail")
                .isEqualTo("Music search isn't set up on this server.")
                .jsonPath("$.instance")
                .isEqualTo("/api/v1/music/search");
    }

    @Test
    void aBadQueryIsStill400BeforeAnythingElse() {
        client.post()
                .uri("/api/v1/music/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", " "))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void theSecretNeverShowsInTheSettingsToString() {
        assertThat(new SpotifyProperties("the-id", "the-secret").toString())
                .contains("the-id")
                .doesNotContain("the-secret");
        assertThat(new SpotifyProperties("", "").toString()).contains("(not set)");
    }
}

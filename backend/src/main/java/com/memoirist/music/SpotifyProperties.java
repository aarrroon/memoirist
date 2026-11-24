package com.memoirist.music;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The app-only credentials for Spotify's search (D-35), from {@code SPOTIFY_CLIENT_ID} and
 * {@code SPOTIFY_CLIENT_SECRET} (locally, in {@code .env}).
 *
 * <p>Unlike the storage settings, both are optional: without them the app starts, soundtracks pasted as links still
 * work, and {@code POST /music/search} answers {@code 503 music-search-unavailable}. A value that is blank counts as
 * missing, because {@code application.yml} turns an unset variable into an empty string.
 *
 * @param clientId the Spotify developer app's client id
 * @param clientSecret the client secret; it is only ever sent to Spotify's token endpoint, and {@link #toString()}
 *     never shows it
 */
@ConfigurationProperties("spotify")
public record SpotifyProperties(String clientId, String clientSecret) {

    /** Both settings are there, so a token can be asked for. */
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    /** The generated {@code toString()} would print the secret; logging the settings must not. */
    @Override
    public String toString() {
        return "SpotifyProperties[clientId=%s, clientSecret=%s]"
                .formatted(clientId, isConfigured() ? "******" : "(not set)");
    }
}

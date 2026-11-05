package com.memoirist.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One search result (API_CONTRACT, Music).
 *
 * @param uri a Spotify URI that passes {@code Soundtrack.isValid}, ready to be sent as a {@code soundtrack}
 * @param subtitle the artist (a track, an album) or the owner (a playlist); empty when Spotify gave none
 * @param imageUrl a cover image on Spotify's image host, or {@code null}. It is shown only once the user has searched,
 *     and the browser loads it from Spotify then (D-35)
 */
public record MusicResult(
        String uri,
        MusicKind kind,
        String title,
        String subtitle,
        @Schema(nullable = true) String imageUrl) {}

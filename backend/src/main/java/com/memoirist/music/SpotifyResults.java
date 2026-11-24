package com.memoirist.music;

import com.memoirist.music.dto.MusicKind;
import com.memoirist.music.dto.MusicResult;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

/**
 * Spotify's search answer turned into our results: pure functions, no HTTP, so they are tested with plain JSON.
 *
 * <p>Spotify's answer is read leniently, because it is someone else's data: a list can hold {@code null} (playlists
 * are known for it), an image or an artist can be missing, and a result whose URI isn't one we would store is dropped
 * rather than offered to the user and refused later on save.
 */
final class SpotifyResults {

    private SpotifyResults() {}

    /** Takes turns between the tracks, albums and playlists until {@code max} results are collected or all run out. */
    static List<MusicResult> merge(JsonNode answer, int max) {
        List<Iterator<MusicResult>> sources = List.of(
                read(answer.path("tracks"), MusicKind.TRACK).iterator(),
                read(answer.path("albums"), MusicKind.ALBUM).iterator(),
                read(answer.path("playlists"), MusicKind.PLAYLIST).iterator());
        List<MusicResult> merged = new ArrayList<>();
        boolean tookOne = true;
        while (merged.size() < max && tookOne) {
            tookOne = false;
            for (Iterator<MusicResult> source : sources) {
                if (merged.size() < max && source.hasNext()) {
                    merged.add(source.next());
                    tookOne = true;
                }
            }
        }
        return List.copyOf(merged);
    }

    private static List<MusicResult> read(JsonNode section, MusicKind kind) {
        List<MusicResult> results = new ArrayList<>();
        for (JsonNode item : section.path("items")) {
            MusicResult result = item.isObject() ? toResult(item, kind) : null;
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    /** {@code null} when the item can't be offered: no usable URI of this kind, or no name to show. */
    private static MusicResult toResult(JsonNode item, MusicKind kind) {
        String uri = item.path("uri").asString("");
        String title = item.path("name").asString("").strip();
        String prefix = "spotify:" + kind.name().toLowerCase(Locale.ROOT) + ":";
        if (!Soundtrack.isValid(uri) || !uri.startsWith(prefix) || title.isEmpty()) {
            return null;
        }
        // A track has no picture of its own: it uses its album's.
        JsonNode images = kind == MusicKind.TRACK ? item.path("album").path("images") : item.path("images");
        return new MusicResult(uri, kind, title, subtitle(item, kind), imageUrl(images));
    }

    private static String subtitle(JsonNode item, MusicKind kind) {
        if (kind == MusicKind.PLAYLIST) {
            return item.path("owner").path("display_name").asString("").strip();
        }
        List<String> names = new ArrayList<>();
        for (JsonNode artist : item.path("artists")) {
            String name = artist.path("name").asString("").strip();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return String.join(", ", names);
    }

    /** The first image, if it is an https address. It goes into an {@code <img src>}, so nothing else is passed on. */
    private static String imageUrl(JsonNode images) {
        for (JsonNode image : images) {
            String url = image.path("url").asString("");
            return url.startsWith("https://") ? url : null;
        }
        return null;
    }
}

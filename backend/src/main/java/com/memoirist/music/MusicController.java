package com.memoirist.music;

import com.memoirist.music.dto.MusicSearchRequest;
import com.memoirist.music.dto.MusicSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/music}: search for a soundtrack through our server (API_CONTRACT, Music). HTTP only. It is a
 * {@code POST} with the words in the body, because a {@code GET} would put them in the URL (D-35).
 */
@RestController
@RequestMapping("/api/v1/music")
@Tag(name = "Music")
class MusicController {

    private final MusicSearchService search;

    MusicController(MusicSearchService search) {
        this.search = search;
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search Spotify for a soundtrack",
            description = "Up to 10 tracks, albums and playlists. The words are sent to Spotify from the server with"
                    + " app-only credentials and are never logged. A POST so that they stay out of the URL.")
    @ApiResponse(responseCode = "200", description = "At most 10 results; empty when nothing matches")
    @ApiResponse(responseCode = "400", description = "validation-failed with fieldErrors (query), or malformed-request")
    @ApiResponse(responseCode = "415", description = "unsupported-media-type: the body is not application/json")
    @ApiResponse(
            responseCode = "503",
            description = "music-search-unavailable: search isn't set up, or Spotify refused the request")
    MusicSearchResponse search(@RequestBody MusicSearchRequest request) {
        return search.search(request.query());
    }
}

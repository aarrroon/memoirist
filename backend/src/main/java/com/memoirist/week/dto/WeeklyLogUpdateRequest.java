package com.memoirist.week.dto;

import com.memoirist.music.SpotifyUri;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code PUT /api/v1/weekly-logs/{logId}}: a full replace of what the user may set on a week (API_CONTRACT,
 * Weekly logs), which is its title and its soundtrack. A week's Eras follow from their dates and can't be set here
 * (D-22), and every other field the endpoint doesn't know is ignored.
 *
 * <p>Being a full replace, a {@code title} that is left out, {@code null} or only spaces clears the title, and a
 * {@code soundtrack} that is left out or {@code null} clears the soundtrack.
 *
 * @param title up to 200 characters once stripped of spaces at both ends; {@code null} for no title
 * @param soundtrack a Spotify track, album or playlist URI (D-35), or {@code null} for none; anything else is
 *     {@code 400} ({@code fieldErrors.soundtrack})
 */
public record WeeklyLogUpdateRequest(
        @Size(max = 200) String title, @SpotifyUri String soundtrack) {

    public WeeklyLogUpdateRequest {
        title = cleaned(title);
    }

    /** Stripped; {@code null} when there is nothing left, so "no title" has one spelling. */
    private static String cleaned(String title) {
        if (title == null) {
            return null;
        }
        String stripped = title.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}

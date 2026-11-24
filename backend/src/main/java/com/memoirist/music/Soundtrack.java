package com.memoirist.music;

import java.util.regex.Pattern;

/**
 * What a soundtrack may be (D-35): a Spotify URI, {@code spotify:track:}, {@code spotify:album:} or
 * {@code spotify:playlist:} followed by a 22-character id of letters and digits. Nothing else is stored: no web link,
 * no user-chosen text, so the player can be built from the value without it being able to point somewhere else.
 */
public final class Soundtrack {

    /** 22 base-62 characters. {@code matches()} needs the whole text to fit, so a trailing newline fails too. */
    private static final Pattern URI = Pattern.compile("spotify:(track|album|playlist):[A-Za-z0-9]{22}");

    private Soundtrack() {}

    public static boolean isValid(String uri) {
        return uri != null && URI.matcher(uri).matches();
    }
}

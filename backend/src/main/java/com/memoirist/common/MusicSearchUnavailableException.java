package com.memoirist.common;

/**
 * Spotify search isn't set up (no client id and secret), or Spotify refused the request (D-35). It becomes
 * {@code 503 music-search-unavailable} (API_CONTRACT, Errors).
 *
 * <p>The message goes to the client, so it says only which of the two it was. Pass the cause when there is one: the
 * advice logs it.
 */
public class MusicSearchUnavailableException extends RuntimeException {

    public MusicSearchUnavailableException(String message) {
        super(message);
    }

    public MusicSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

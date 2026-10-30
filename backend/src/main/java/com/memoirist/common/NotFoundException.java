package com.memoirist.common;

/**
 * The resource doesn't exist <em>or belongs to someone else</em>; the client can't tell which (non-negotiable 3). It
 * becomes {@code 404 not-found} (API_CONTRACT, Errors).
 *
 * <p>Throw it from a service when a user-scoped lookup comes back empty, and for a referenced id (a tag, a template,
 * a reply target) the user doesn't own. Never answer {@code 403}: that would confirm the resource exists.
 */
public class NotFoundException extends RuntimeException {

    /** @param resource what wasn't found, in words for the client, such as {@code "Entry"} or {@code "Era"} */
    public NotFoundException(String resource) {
        super(resource + " not found.");
    }
}

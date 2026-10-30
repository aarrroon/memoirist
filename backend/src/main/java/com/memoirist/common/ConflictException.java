package com.memoirist.common;

/**
 * The request clashes with data that already exists: a client-supplied id used by another user, or by an entry in
 * Recently deleted (D-12, D-30). It becomes {@code 409 conflict} (API_CONTRACT, Errors).
 *
 * <p>Don't reveal who owns the other row, or what it holds. "That id is already in use" is the whole message.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

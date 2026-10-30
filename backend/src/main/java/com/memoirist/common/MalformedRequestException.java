package com.memoirist.common;

/**
 * The request can't be understood: a header, path variable or body that isn't in the shape the API contract allows.
 * It becomes {@code 400} with the problem type {@code malformed-request} (API_CONTRACT, Errors).
 *
 * <p>The message goes to the client as the problem's {@code detail}, so write it for the caller and never put the
 * rejected value in it: a value like that is untrusted input, and echoing it back is how reflected-content bugs start.
 */
public class MalformedRequestException extends RuntimeException {

    public MalformedRequestException(String message) {
        super(message);
    }
}

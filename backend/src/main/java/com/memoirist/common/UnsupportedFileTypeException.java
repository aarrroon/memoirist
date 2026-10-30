package com.memoirist.common;

/**
 * An uploaded file's type isn't on the allow-list (D-17), or a diary-page photo isn't an image (D-20). It becomes
 * {@code 415 unsupported-media-type} (API_CONTRACT, Errors).
 *
 * <p>Named for the file, not for HTTP's "media type", so it can't be mixed up with Spring's own
 * {@code HttpMediaTypeNotSupportedException}, which is about the request's Content-Type.
 */
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}

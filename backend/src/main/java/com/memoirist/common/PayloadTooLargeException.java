package com.memoirist.common;

/**
 * Content over 200 KB (D-05), or another size limit that a service checks itself. It becomes
 * {@code 413 payload-too-large} (API_CONTRACT, Errors).
 *
 * <p>A file over 25 MB never reaches a service: the multipart limit stops it first, and the advice answers Spring's
 * {@code MaxUploadSizeExceededException} with the same response.
 */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}

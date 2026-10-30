package com.memoirist.common;

/**
 * Object storage (SeaweedFS locally, S3 later) failed a call. It becomes {@code 503 storage-unavailable}
 * (API_CONTRACT, Errors).
 *
 * <p>Always pass the cause: the advice logs it, and the client gets only a fixed sentence, because the cause can name
 * the storage host and bucket.
 */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

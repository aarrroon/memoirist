package com.memoirist.storage;

import com.memoirist.common.StorageUnavailableException;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * The only door to object storage: put, delete and presign (D-03, TECH_STACK §2 Storage). Photos, voice notes and page
 * scans go in through {@code put}, come out to the browser as presigned URLs, and go away through {@code delete}.
 *
 * <p>It knows nothing about users, entries or media rows. Callers pass object keys
 * ({@code users/{userId}/entries/{entryId}/{mediaId}.{ext}}), so the checks that matter stay with them: whether the
 * caller owns the entry, whether it is sealed (never presign a sealed entry's media, D-10), whether the type and size
 * are allowed (D-17).
 *
 * <p>Failures of the storage service itself (down, refusing the keys, a timeout) become
 * {@link StorageUnavailableException}, which the API answers with {@code 503}. The exception is thrown by {@code put}
 * and {@code delete}; {@code presignGet} calls nobody, so it can only fail on a bug.
 *
 * <p>This class is not {@code @Transactional}: a stored file can't be rolled back. The callers' flows are built around
 * that. An upload stores the file first and inserts the row second, and deletes the file again if the insert fails.
 * A delete waits for the commit ({@link #deleteAfterCommit}), so a rolled-back erase never loses a file.
 */
@Service
public class StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;
    private final ApplicationEventPublisher events;

    public StorageService(
            S3Client s3, S3Presigner presigner, StorageProperties properties, ApplicationEventPublisher events) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
        this.events = events;
    }

    /**
     * Stores {@code content} under {@code objectKey}, replacing whatever was there.
     *
     * <p>The caller closes the stream. The length must be exact: S3 needs it up front, and it is why a 25 MB upload
     * never has to sit in memory.
     *
     * <p>The SDK retries a failed attempt (four in all) by rewinding the stream, and it keeps only the first 128 KiB
     * for that. A stream that can't be rewound at all, such as a file stream, makes the first retry throw an
     * {@code IllegalStateException}, which would answer {@code 500} when storage merely hiccuped. So such a stream is
     * wrapped in a {@link BufferedInputStream}, which can. Once more than 128 KiB has been sent, a failed attempt
     * can't be retried and the upload fails as {@link StorageUnavailableException}; the user tries again.
     *
     * @param contentType the declared type, returned as {@code Content-Type} when the file is fetched; the caller has
     *     already checked it against the D-17 list
     * @throws StorageUnavailableException if storage fails
     */
    public void put(String objectKey, InputStream content, long contentLength, String contentType) {
        requireKey(objectKey);
        requireContentType(contentType);
        if (contentLength < 0) {
            throw new IllegalArgumentException("A stored object can't have a negative length");
        }
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        InputStream rewindable = content.markSupported() ? content : new BufferedInputStream(content);
        try {
            s3.putObject(request, RequestBody.fromInputStream(rewindable, contentLength));
        } catch (SdkException failure) {
            throw new StorageUnavailableException("Storing an object failed", failure);
        }
    }

    /** Stores bytes already in memory: sample content and small generated files. Same rules as the stream version. */
    public void put(String objectKey, byte[] content, String contentType) {
        requireKey(objectKey);
        requireContentType(contentType);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();
        try {
            s3.putObject(request, RequestBody.fromBytes(content));
        } catch (SdkException failure) {
            throw new StorageUnavailableException("Storing an object failed", failure);
        }
    }

    /**
     * Deletes one object now. Deleting a key that isn't there is not an error (S3 answers {@code 204} either way), so
     * a repeated call, or a cleanup that races another, is harmless.
     *
     * <p>Use {@link #deleteAfterCommit} when the delete belongs to a database change.
     *
     * @throws StorageUnavailableException if storage fails
     */
    public void delete(String objectKey) {
        requireKey(objectKey);
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();
        try {
            s3.deleteObject(request);
        } catch (SdkException failure) {
            throw new StorageUnavailableException("Deleting an object failed", failure);
        }
    }

    /**
     * Deletes these objects once the current transaction has committed, and not at all if it rolls back.
     *
     * <p>The database is the record of what exists, so it goes first: if the objects were deleted inside the
     * transaction and the commit then failed, the rows would stay and point at files that are gone. Deleted after
     * the commit, the worst case is the opposite, files nobody points at, which cost space and nothing else.
     * {@link ObjectDeletionListener} does the deleting, one key at a time, and a key that fails is logged and
     * skipped; it never fails the request that already committed.
     *
     * <p>Called with no transaction running, it deletes at once.
     */
    public void deleteAfterCommit(Collection<String> objectKeys) {
        objectKeys.forEach(StorageService::requireKey);
        if (!objectKeys.isEmpty()) {
            events.publishEvent(new ObjectDeletionRequested(List.copyOf(objectKeys)));
        }
    }

    /**
     * A URL the browser can open to fetch the object, valid for {@code storage.presign-ttl} (60 minutes at most).
     *
     * <p>The caller decides whether this user may see the object. The URL works for anyone who holds it until it
     * expires, and it is never logged or stored (non-negotiable 5, {@code security.md}). Signing happens here, with
     * no call to storage, so the key doesn't have to exist.
     */
    public String presignGet(String objectKey) {
        requireKey(objectKey);
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(properties.presignTtl())
                .getObjectRequest(get)
                .build();
        return presigner.presignGetObject(request).url().toString();
    }

    private static void requireKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("An object key can't be blank");
        }
    }

    private static void requireContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("A stored object needs a content type");
        }
    }
}

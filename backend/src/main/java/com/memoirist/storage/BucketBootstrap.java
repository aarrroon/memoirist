package com.memoirist.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Makes sure the bucket exists when the app starts on a developer's machine.
 *
 * <p>Docker Compose starts SeaweedFS with no buckets (task 1.3 left that to the backend), so the first run after
 * {@code docker compose up -d} creates {@code memoirist-media}. It runs only in the {@code local} profile. In
 * production the bucket is created once, by hand, as a private bucket in Cloudflare R2 (Phase 2): an app that created
 * buckets itself would need a key allowed to do that, and a mistyped bucket name would quietly make a new, empty one
 * instead of stopping the app. The integration tests import it too, through {@code SeaweedFsTestConfiguration}.
 *
 * <p>If storage can't be reached the app does not start, the same as when Postgres is down, and the message says what
 * to check.
 */
@Component
@Profile("local")
public class BucketBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BucketBootstrap.class);

    private final S3Client s3;
    private final StorageProperties properties;

    public BucketBootstrap(S3Client s3, StorageProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureBucket();
    }

    /**
     * Creates the bucket if it isn't there. Calling it again does nothing.
     *
     * @return {@code true} if this call created the bucket
     * @throws IllegalStateException if storage can't be reached or refuses the keys
     */
    public boolean ensureBucket() {
        String bucket = properties.bucket();
        try {
            s3.headBucket(request -> request.bucket(bucket));
            return false;
        } catch (NoSuchBucketException missing) {
            // The one expected failure: carry on and create it.
        } catch (SdkException failure) {
            throw unreachable(failure);
        }
        try {
            s3.createBucket(request -> request.bucket(bucket));
            log.info("Created storage bucket {}", bucket);
            return true;
        } catch (BucketAlreadyOwnedByYouException raced) {
            return false; // another process created it between our check and our create
        } catch (SdkException failure) {
            throw unreachable(failure);
        }
    }

    private IllegalStateException unreachable(SdkException cause) {
        return new IllegalStateException(
                ("Cannot reach object storage at %s to check the bucket '%s'. Is it running (docker compose up -d)?"
                                + " If it is, do STORAGE_ACCESS_KEY and STORAGE_SECRET_KEY in .env match the keys it"
                                + " was started with? See docs/DEVELOPMENT.md, Troubleshooting.")
                        .formatted(properties.endpoint(), properties.bucket()),
                cause);
    }
}

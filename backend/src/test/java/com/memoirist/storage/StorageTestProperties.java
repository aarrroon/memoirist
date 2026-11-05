package com.memoirist.storage;

import java.net.URI;
import java.time.Duration;

/**
 * Valid {@link StorageProperties} for tests that build the storage objects by hand. The record has eight components,
 * so each test names only the one it is about.
 */
final class StorageTestProperties {

    static final String SECRET_KEY = "test-secret-key-that-must-never-be-logged";

    private StorageTestProperties() {}

    /** Path style, a public endpoint that differs from the endpoint, and the 60-minute limit. */
    static StorageProperties properties() {
        return new StorageProperties(
                URI.create("http://storage.internal:8333"),
                URI.create("http://public.example:9000"),
                "us-east-1",
                "memoirist-media",
                "test-access-key",
                SECRET_KEY,
                true,
                Duration.ofMinutes(60));
    }

    static StorageProperties withPathStyle(StorageProperties base, boolean pathStyle) {
        return new StorageProperties(
                base.endpoint(),
                base.publicEndpoint(),
                base.region(),
                base.bucket(),
                base.accessKey(),
                base.secretKey(),
                pathStyle,
                base.presignTtl());
    }

    static StorageProperties withTtl(StorageProperties base, Duration ttl) {
        return new StorageProperties(
                base.endpoint(),
                base.publicEndpoint(),
                base.region(),
                base.bucket(),
                base.accessKey(),
                base.secretKey(),
                base.pathStyle(),
                ttl);
    }

    static StorageProperties withBucket(StorageProperties base, String bucket) {
        return new StorageProperties(
                base.endpoint(),
                base.publicEndpoint(),
                base.region(),
                bucket,
                base.accessKey(),
                base.secretKey(),
                base.pathStyle(),
                base.presignTtl());
    }

    static StorageProperties withSecretKey(StorageProperties base, String secretKey) {
        return new StorageProperties(
                base.endpoint(),
                base.publicEndpoint(),
                base.region(),
                base.bucket(),
                base.accessKey(),
                secretKey,
                base.pathStyle(),
                base.presignTtl());
    }

    static StorageProperties withEndpoint(StorageProperties base, URI endpoint) {
        return new StorageProperties(
                endpoint,
                base.publicEndpoint(),
                base.region(),
                base.bucket(),
                base.accessKey(),
                base.secretKey(),
                base.pathStyle(),
                base.presignTtl());
    }
}

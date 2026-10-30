package com.memoirist.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.memoirist.IntegrationTest;
import com.memoirist.SeaweedFsTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * {@link BucketBootstrap} against a real SeaweedFS. The {@code local} profile can't be used in a test (it reads
 * {@code ../.env}), so the bootstrap is built by hand, and {@code BucketBootstrapProfileTest} checks which profiles
 * get the bean.
 */
@IntegrationTest
@Import(SeaweedFsTestConfiguration.class)
class BucketBootstrapTest {

    @Autowired
    S3Client s3;

    @Autowired
    StorageProperties properties;

    @Test
    void theBucketExistsAfterTheContextStarts() {
        // SeaweedFsTestConfiguration declares a BucketBootstrap bean, which runs at startup like the local profile's.
        assertThatNoException().isThrownBy(() -> s3.headBucket(request -> request.bucket(properties.bucket())));
    }

    @Test
    void createsAMissingBucketOnceAndThenLeavesItAlone() {
        String bucket = "bootstrap-test-" + UUID.randomUUID();
        BucketBootstrap bootstrap = new BucketBootstrap(s3, StorageTestProperties.withBucket(properties, bucket));
        assertBucketIsMissing(bucket);

        try {
            assertThat(bootstrap.ensureBucket()).as("first call creates it").isTrue();
            assertThatNoException().isThrownBy(() -> s3.headBucket(request -> request.bucket(bucket)));
            assertThat(bootstrap.ensureBucket()).as("second call finds it").isFalse();
        } finally {
            s3.deleteBucket(request -> request.bucket(bucket));
        }
    }

    @Test
    void wrongKeysStopTheAppWithAMessageThatSaysWhatToCheck() {
        StorageProperties wrongKeys = StorageTestProperties.withSecretKey(properties, "not-the-secret-seaweedfs-has");
        try (S3Client wrongClient = new StorageConfiguration().s3Client(wrongKeys)) {
            assertThatIllegalStateException()
                    .isThrownBy(() -> new BucketBootstrap(wrongClient, wrongKeys).ensureBucket())
                    .withMessageContaining(properties.endpoint().toString())
                    .withMessageContaining(properties.bucket())
                    .withMessageContaining("docker compose up -d")
                    .withMessageContaining("STORAGE_SECRET_KEY")
                    // The message goes to the console; it names the keys' variables, never their values.
                    .withMessageNotContaining("not-the-secret-seaweedfs-has")
                    .withCauseInstanceOf(S3Exception.class);
        }
    }

    private void assertBucketIsMissing(String bucket) {
        assertThatExceptionOfType(NoSuchBucketException.class)
                .as("the test's own bucket must not exist yet")
                .isThrownBy(() -> s3.headBucket(request -> request.bucket(bucket)));
    }
}

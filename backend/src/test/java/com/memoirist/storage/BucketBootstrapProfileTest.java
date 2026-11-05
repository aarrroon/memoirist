package com.memoirist.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;

/** The parts of {@link BucketBootstrap} that need no Docker: which profiles get it, and what a dead storage says. */
class BucketBootstrapProfileTest {

    /** A context holding just the bootstrap and the two beans it needs, with the given profiles active. */
    private ApplicationContextRunner contextWithProfiles(String... profiles) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles))
                .withBean(S3Client.class, () -> mock(S3Client.class))
                .withBean(StorageProperties.class, StorageTestProperties::properties)
                .withUserConfiguration(BucketBootstrap.class);
    }

    @Test
    void theLocalProfileHasTheBootstrap() {
        contextWithProfiles("local").run(context -> assertThat(context).hasSingleBean(BucketBootstrap.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"test", "prod", "staging"})
    void everyOtherProfileDoesNot(String profile) {
        // In production the bucket is made once, by hand, as a private R2 bucket. The app must not create buckets.
        contextWithProfiles(profile).run(context -> assertThat(context).doesNotHaveBean(BucketBootstrap.class));
    }

    @Test
    void noProfileAtAllDoesNotEither() {
        contextWithProfiles().run(context -> assertThat(context).doesNotHaveBean(BucketBootstrap.class));
    }

    @Test
    void storageThatIsNotRunningStopsTheAppWithAMessageThatSaysWhatToCheck() {
        // Nothing listens on port 1, so the connection is refused at once.
        StorageProperties nothingListening = StorageTestProperties.withEndpoint(
                StorageTestProperties.properties(), URI.create("http://127.0.0.1:1"));
        try (S3Client s3 = new StorageConfiguration().s3Client(nothingListening)) {
            assertThatIllegalStateException()
                    .isThrownBy(() -> new BucketBootstrap(s3, nothingListening).ensureBucket())
                    .withMessageContaining("http://127.0.0.1:1")
                    .withMessageContaining("memoirist-media")
                    .withMessageContaining("docker compose up -d")
                    .withMessageContaining("docs/DEVELOPMENT.md")
                    .withMessageNotContaining(StorageTestProperties.SECRET_KEY)
                    .withCauseInstanceOf(SdkClientException.class);
        }
    }
}

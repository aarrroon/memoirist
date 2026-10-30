package com.memoirist.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.validation.FieldError;

/**
 * Binds {@link StorageProperties} the way the app does, in a context that holds nothing else, so each case takes
 * milliseconds instead of a full app start.
 */
class StoragePropertiesTest {

    /** A complete, valid set: each test overrides what it's about. */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StorageOnly.class)
            .withPropertyValues(
                    "storage.endpoint=http://localhost:8333",
                    "storage.public-endpoint=http://localhost:8333",
                    "storage.region=us-east-1",
                    "storage.bucket=memoirist-media",
                    "storage.access-key=memoirist-local",
                    "storage.secret-key=not-a-real-secret",
                    "storage.path-style=true",
                    "storage.presign-ttl=PT60M");

    /** Registers the record as {@code @ConfigurationPropertiesScan} does in the app. */
    @EnableConfigurationProperties(StorageProperties.class)
    static class StorageOnly {}

    @Test
    void bindsEachSettingToItsType() {
        runner.run(context -> {
            StorageProperties storage = context.getBean(StorageProperties.class);
            assertThat(storage.endpoint()).isEqualTo(URI.create("http://localhost:8333"));
            assertThat(storage.publicEndpoint()).isEqualTo(URI.create("http://localhost:8333"));
            assertThat(storage.region()).isEqualTo("us-east-1");
            assertThat(storage.bucket()).isEqualTo("memoirist-media");
            assertThat(storage.accessKey()).isEqualTo("memoirist-local");
            assertThat(storage.secretKey()).isEqualTo("not-a-real-secret");
            assertThat(storage.pathStyle()).isTrue();
            assertThat(storage.presignTtl()).isEqualTo(Duration.ofMinutes(60));
        });
    }

    @Test
    void everySettingIsRequired() {
        // What application.yml's ${STORAGE_...:} placeholders produce when no variable is set: empty values.
        runner.withPropertyValues(
                        "storage.endpoint=",
                        "storage.public-endpoint=",
                        "storage.region=",
                        "storage.bucket=",
                        "storage.access-key=",
                        "storage.secret-key=",
                        "storage.path-style=",
                        "storage.presign-ttl=")
                .run(context -> assertThat(rejectedSettings(context))
                        .containsOnly(
                                "endpoint",
                                "publicEndpoint",
                                "region",
                                "bucket",
                                "accessKey",
                                "secretKey",
                                "pathStyle",
                                "presignTtl"));
    }

    @Test
    void refusesToStartAndNamesEachSettingThatIsWrong() {
        // TODO(human) PRACTICE: Prove the app refuses to start, and names the culprits, when the secret key is
        //   missing, the bucket name breaks S3's rules and the presign TTL says hours where it meant minutes.
        //   Use the runner above and the rejectedSettings helper below.
        // Hint: the runner never throws; run() hands you the context, started or failed. What does a missing
        //   variable become after application.yml's ${STORAGE_SECRET_KEY:}? Why assert the names, not just "failed"?
        // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
        runner.withPropertyValues(
                        "storage.secret-key=", // an unset STORAGE_SECRET_KEY, through ${STORAGE_SECRET_KEY:}
                        "storage.bucket=Memoirist_Media", // S3 refuses capitals and underscores
                        "storage.presign-ttl=PT60H") // 60 hours: a typo for PT60M
                .run(context ->
                        assertThat(rejectedSettings(context)).containsOnly("secretKey", "bucket", "presignTtl"));
        // └── END ANSWER ▲ ──
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT1M", "PT60M", "60m", "3600s"})
    void presignedLinksMayLastOneToSixtyMinutes(String ttl) {
        runner.withPropertyValues("storage.presign-ttl=" + ttl)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT59S", "PT60M1S", "PT60H", "0s", "-PT5M"})
    void presignedLinksMayNotLastLessThanAMinuteOrMoreThanAnHour(String ttl) {
        runner.withPropertyValues("storage.presign-ttl=" + ttl)
                .run(context -> assertThat(rejectedSettings(context)).containsOnly("presignTtl"));
    }

    @Test
    void toStringNeverShowsTheSecretKey() {
        runner.run(
                context -> assertThat(context.getBean(StorageProperties.class).toString())
                        .doesNotContain("not-a-real-secret")
                        .contains("secretKey=******", "accessKey=memoirist-local", "bucket=memoirist-media"));
    }

    /** The settings that failed validation, by record component name, from a context that failed to start. */
    private static List<String> rejectedSettings(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure());
        assertThat(cause).isInstanceOf(BindValidationException.class);
        return ((BindValidationException) cause)
                .getValidationErrors().getAllErrors().stream()
                        .map(error -> ((FieldError) error).getField())
                        .toList();
    }
}

package com.memoirist.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where photos, voice notes and page scans are stored: SeaweedFS locally, Cloudflare R2 in production (D-03).
 *
 * <p>Bound from {@code storage.*}, which {@code application.yml} reads from the {@code STORAGE_*} environment
 * variables (locally, from {@code .env}). Every value is required and checked when the app starts, so a missing or
 * mistyped setting stops it with the property's name instead of failing at the first upload.
 *
 * @param endpoint the S3 API address the backend calls to upload and delete ({@code S3Client})
 * @param publicEndpoint the address the browser can reach, used to sign GET URLs ({@code S3Presigner}); differs from
 *     {@code endpoint} only when the backend runs inside Docker
 * @param region the region requests are signed for ({@code auto} on R2)
 * @param bucket the one bucket that holds every object ({@code memoirist-media}, D-01)
 * @param accessKey the access key ID
 * @param secretKey the secret key; {@link #toString()} never shows it
 * @param pathStyle {@code true} to put the bucket in the path ({@code /memoirist-media/key}) rather than the host name,
 *     which SeaweedFS needs
 * @param presignTtl how long a presigned GET URL works
 */
@Validated
@ConfigurationProperties("storage")
public record StorageProperties(
        // TODO(human) PRACTICE: Declare the eight settings with the types and constraints that make the app refuse to
        //   start when one is missing or wrong. Every one is required: a missing variable must stop the app and name
        //   the property, not surface later as a failed upload.
        // Hint: weigh URI against String for the endpoints, and Boolean against boolean for path style (what does a
        //   missing value become?). S3 has bucket-name rules, and presigned links last 60 minutes (TECH_STACK §2
        //   Storage; security rules: short-lived links). A typo like PT60H instead of PT60M should not get through.
        // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
        @NotNull URI endpoint,
        @NotNull URI publicEndpoint,
        @NotBlank String region,

        @NotBlank
        @Pattern(
                regexp = "[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]",
                message = "must be 3 to 63 lowercase letters, digits, dots or hyphens, starting and ending"
                        + " with a letter or digit")
        String bucket,

        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotNull Boolean pathStyle,

        @NotNull @DurationMin(minutes = 1) @DurationMax(minutes = 60)
        Duration presignTtl
        // └── END ANSWER ▲ ──
        ) {

    /**
     * Like the generated {@code toString()}, but with the secret key masked. A record prints every component, so
     * without this, logging the settings (or an exception message that includes them) would print the secret.
     */
    @Override
    public String toString() {
        return ("StorageProperties[endpoint=%s, publicEndpoint=%s, region=%s, bucket=%s, accessKey=%s, secretKey=******, "
                        + "pathStyle=%s, presignTtl=%s]")
                .formatted(endpoint, publicEndpoint, region, bucket, accessKey, pathStyle, presignTtl);
    }
}

package com.memoirist.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * The two AWS SDK objects that talk to object storage (D-03), built from {@link StorageProperties}.
 *
 * <p>They are two objects because the backend and the browser may not reach storage at the same address: the
 * {@link S3Client} (uploads, deletes) calls {@code storage.endpoint}, and the {@link S3Presigner} signs URLs for
 * {@code storage.public-endpoint}, the address the browser opens. Locally they are the same; they differ only when the
 * backend runs inside Docker. Both use the keys from the properties and never the SDK's default credential chain, so
 * a stray {@code AWS_ACCESS_KEY_ID} on a developer's machine can't change which storage the app signs for.
 *
 * <p>Spring closes both beans when the app stops: it calls the public {@code close()} method of a bean method's return
 * value.
 */
@Configuration(proxyBeanMethods = false)
class StorageConfiguration {

    @Bean
    S3Client s3Client(StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                // Local SeaweedFS is addressed by IP and port, so the bucket goes in the path
                // (/memoirist-media/key). The other form puts it in the host name (memoirist-media.localhost),
                // which would need a DNS entry per bucket.
                .forcePathStyle(properties.pathStyle())
                // Since version 2.30 the SDK adds a CRC32 checksum to every upload and checks one on every
                // download. Some S3-compatible stores didn't understand that (R2 failed uploads over it in early
                // 2025). "When required" asks for a checksum only on the few calls where S3 insists. SeaweedFS
                // works either way (a test run with the default passed), so this is for R2: task 5.6 tries it
                // against a real bucket, with and without.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Bean
    S3Presigner s3Presigner(StorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(properties.publicEndpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                // The presigner has no forcePathStyle(): path style is set on its S3Configuration.
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyle())
                        .build())
                .build();
    }

    private static AwsCredentialsProvider credentials(StorageProperties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}

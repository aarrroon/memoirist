package com.memoirist.storage;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import com.memoirist.common.StorageUnavailableException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * {@link StorageService} with the real SDK client pointed at a port where nothing listens, so the SDK's own retry
 * logic runs. A mock {@code S3Client} can't show what the SDK does between attempts.
 */
class StorageServiceUnreachableTest {

    @Test
    void aFileStreamSentToStorageThatIsDownEndsAsStorageUnavailableNotAnIllegalState(@TempDir Path folder)
            throws Exception {
        StorageProperties nothingListening = StorageTestProperties.withEndpoint(
                StorageTestProperties.properties(), URI.create("http://127.0.0.1:1"));
        // The kind of stream a disk-backed multipart upload gives: it can't be marked and reset. Without the service's
        // BufferedInputStream the SDK's first retry throws IllegalStateException, and the API would answer 500, not
        // 503.
        Path file = Files.write(folder.resolve("photo.jpg"), new byte[300_000]);

        try (S3Client s3 = new StorageConfiguration().s3Client(nothingListening);
                S3Presigner presigner = new StorageConfiguration().s3Presigner(nothingListening);
                InputStream content = Files.newInputStream(file)) {
            StorageService storage =
                    new StorageService(s3, presigner, nothingListening, mock(ApplicationEventPublisher.class));

            assertThatExceptionOfType(StorageUnavailableException.class)
                    .isThrownBy(() -> storage.put("users/u/entries/e/m.jpg", content, 300_000, "image/jpeg"))
                    .withCauseInstanceOf(SdkClientException.class);
        }
    }
}

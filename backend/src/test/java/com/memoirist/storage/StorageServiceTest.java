package com.memoirist.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.memoirist.common.StorageUnavailableException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * {@link StorageService} without Docker: signing needs no storage (it is arithmetic on the keys), and a mock
 * {@link S3Client} stands in for storage failing. {@code StorageServiceIntegrationTest} runs the same service against
 * a real SeaweedFS.
 */
class StorageServiceTest {

    private static final String KEY =
            "users/11111111-1111-1111-1111-111111111111/entries/22222222-2222-2222-2222-222222222222/"
                    + "33333333-3333-3333-3333-333333333333.jpg";

    private final S3Client s3 = mock(S3Client.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final List<S3Presigner> presigners = new ArrayList<>();

    @AfterEach
    void closePresigners() {
        presigners.forEach(S3Presigner::close);
    }

    private StorageService service(StorageProperties properties) {
        S3Presigner presigner = new StorageConfiguration().s3Presigner(properties);
        presigners.add(presigner);
        return new StorageService(s3, presigner, properties, events);
    }

    // --- presigned URLs: pure signing, no storage involved ---

    @Test
    void aPresignedUrlNamesThePublicEndpointTheBucketAndTheKeyInThePath() {
        URI url = URI.create(service(StorageTestProperties.properties()).presignGet(KEY));

        // The public endpoint, not the endpoint the backend itself calls (storage.internal).
        assertThat(url.getScheme()).isEqualTo("http");
        assertThat(url.getHost()).isEqualTo("public.example");
        assertThat(url.getPort()).isEqualTo(9000);
        // Path style: the bucket is the first path segment.
        assertThat(url.getPath()).isEqualTo("/memoirist-media/" + KEY);
    }

    @Test
    void aPresignedUrlCarriesASignatureForTheConfiguredKeysAndNeverTheSecret() {
        String url = service(StorageTestProperties.properties()).presignGet(KEY);
        Map<String, String> query = query(URI.create(url));

        assertThat(query).containsEntry("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
        // The host is signed: the URL only works when the browser sends it to the host it names.
        assertThat(query).containsEntry("X-Amz-SignedHeaders", "host");
        assertThat(query.get("X-Amz-Credential")).startsWith("test-access-key/").endsWith("/us-east-1/s3/aws4_request");
        assertThat(query.get("X-Amz-Signature")).hasSize(64).matches("[0-9a-f]+");
        assertThat(url).doesNotContain(StorageTestProperties.SECRET_KEY);
    }

    @Test
    void aPresignedUrlExpiresWhenTheConfiguredTtlSays() {
        assertThat(query(URI.create(service(StorageTestProperties.properties()).presignGet(KEY))))
                .containsEntry("X-Amz-Expires", "3600");

        StorageProperties fiveMinutes =
                StorageTestProperties.withTtl(StorageTestProperties.properties(), Duration.ofMinutes(5));
        assertThat(query(URI.create(service(fiveMinutes).presignGet(KEY)))).containsEntry("X-Amz-Expires", "300");
    }

    @Test
    void withPathStyleOffTheBucketMovesIntoTheHostName() {
        StorageProperties virtualHosted =
                StorageTestProperties.withPathStyle(StorageTestProperties.properties(), false);

        URI url = URI.create(service(virtualHosted).presignGet(KEY));

        assertThat(url.getHost()).isEqualTo("memoirist-media.public.example");
        assertThat(url.getPath()).isEqualTo("/" + KEY);
    }

    @Test
    void presigningCallsNoStorage() {
        service(StorageTestProperties.properties()).presignGet(KEY);

        verifyNoInteractions(s3);
    }

    // --- failures of the storage service become StorageUnavailableException ---

    @Test
    void aFailedPutBecomesStorageUnavailableWithTheCauseKept() {
        S3Exception storageDown = (S3Exception) S3Exception.builder()
                .message("Connect to http://storage.internal:8333/memoirist-media failed")
                .statusCode(503)
                .build();
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(storageDown);

        assertThatExceptionOfType(StorageUnavailableException.class)
                .isThrownBy(() -> service(StorageTestProperties.properties()).put(KEY, new byte[] {1}, "image/jpeg"))
                .withCause(storageDown)
                // Our message names no host and no bucket: the cause does, and the advice never sends it to a client.
                .withMessage("Storing an object failed");
    }

    @Test
    void aFailedStreamedPutBecomesStorageUnavailableToo() {
        SdkClientException unreachable =
                SdkClientException.create("Unable to execute HTTP request: Connection refused");
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(unreachable);

        assertThatExceptionOfType(StorageUnavailableException.class)
                .isThrownBy(() -> service(StorageTestProperties.properties())
                        .put(KEY, new ByteArrayInputStream(new byte[] {1, 2}), 2, "image/jpeg"))
                .withCause(unreachable);
    }

    @Test
    void aFailedDeleteBecomesStorageUnavailableWithTheCauseKept() {
        S3Exception refused =
                (S3Exception) S3Exception.builder().statusCode(403).build();
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(refused);

        assertThatExceptionOfType(StorageUnavailableException.class)
                .isThrownBy(() -> service(StorageTestProperties.properties()).delete(KEY))
                .withCause(refused)
                .withMessage("Deleting an object failed");
    }

    // --- arguments ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void aBlankKeyIsAProgrammingErrorEverywhere(String blank) {
        StorageService storage = service(StorageTestProperties.properties());

        assertThatIllegalArgumentException().isThrownBy(() -> storage.put(blank, new byte[0], "image/jpeg"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> storage.put(blank, new ByteArrayInputStream(new byte[0]), 0, "image/jpeg"));
        assertThatIllegalArgumentException().isThrownBy(() -> storage.delete(blank));
        assertThatIllegalArgumentException().isThrownBy(() -> storage.presignGet(blank));
        List<String> keys = new ArrayList<>();
        keys.add(KEY);
        keys.add(blank);
        assertThatIllegalArgumentException().isThrownBy(() -> storage.deleteAfterCommit(keys));
        verifyNoInteractions(s3, events);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void aMissingContentTypeIsRefusedBeforeAnythingIsSent(String blank) {
        StorageService storage = service(StorageTestProperties.properties());

        assertThatIllegalArgumentException().isThrownBy(() -> storage.put(KEY, new byte[0], blank));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> storage.put(KEY, new ByteArrayInputStream(new byte[0]), 0, blank));
        verifyNoInteractions(s3);
    }

    @Test
    void aNegativeLengthIsRefusedBeforeAnythingIsSent() {
        StorageService storage = service(StorageTestProperties.properties());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> storage.put(KEY, new ByteArrayInputStream(new byte[0]), -1, "image/jpeg"));
        verifyNoInteractions(s3);
    }

    // --- deleteAfterCommit only publishes; the listener does the deleting ---

    @Test
    void deleteAfterCommitPublishesOneEventAndDeletesNothingItself() {
        List<String> keys = new ArrayList<>(List.of(KEY, KEY + ".2"));

        service(StorageTestProperties.properties()).deleteAfterCommit(keys);
        keys.clear(); // the caller reuses its list: the event must not change

        ArgumentCaptor<ObjectDeletionRequested> event = ArgumentCaptor.forClass(ObjectDeletionRequested.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().objectKeys()).containsExactly(KEY, KEY + ".2");
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteAfterCommitWithNoKeysPublishesNothing() {
        service(StorageTestProperties.properties()).deleteAfterCommit(List.of());

        verifyNoInteractions(events);
    }

    /** The query string as a map, URL-decoded. Fails the test on a repeated parameter. */
    private static Map<String, String> query(URI url) {
        Map<String, String> parameters = new HashMap<>();
        for (String pair : url.getRawQuery().split("&")) {
            String[] nameAndValue = pair.split("=", 2);
            String name = URLDecoder.decode(nameAndValue[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(nameAndValue[1], StandardCharsets.UTF_8);
            assertThat(parameters.put(name, value))
                    .as("repeated parameter %s", name)
                    .isNull();
        }
        return parameters;
    }
}

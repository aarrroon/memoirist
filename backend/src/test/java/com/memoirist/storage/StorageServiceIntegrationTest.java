package com.memoirist.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.IntegrationTest;
import com.memoirist.SeaweedFsTestConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Task 3.1's "Done when", against a real SeaweedFS (Testcontainers, the image Compose pins): upload, presign (the URL
 * names the public endpoint, and fetching it returns the bytes) and delete.
 *
 * <p>The whole app starts, in the {@code test} profile, so this also proves the wiring: {@code StorageConfiguration}
 * builds the client and the presigner from the real {@code storage.*} properties, and {@link StorageService} takes them
 * from the context.
 */
@IntegrationTest
@Import(SeaweedFsTestConfiguration.class)
class StorageServiceIntegrationTest {

    @Autowired
    StorageService storage;

    @Autowired
    StorageProperties properties;

    /** The SDK client itself, to look behind {@link StorageService} at what is really stored. */
    @Autowired
    S3Client s3;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void uploadsPresignsAndDeletes() throws Exception {
        String key = newKey("jpg");
        byte[] photo = randomBytes(4096, 1);

        storage.put(key, photo, "image/jpeg");

        String url = storage.presignGet(key);
        URI uri = URI.create(url);
        // The URL names the public endpoint (127.0.0.1), not the endpoint the backend uploaded through (localhost).
        // The hosts differ on purpose, so this fails if the presigner is built from the wrong one.
        assertThat(properties.endpoint().getHost())
                .as("the test must give the endpoint and the public endpoint different hosts")
                .isNotEqualTo(properties.publicEndpoint().getHost());
        assertThat(uri.getHost()).isEqualTo(properties.publicEndpoint().getHost());
        assertThat(uri.getPort()).isEqualTo(properties.publicEndpoint().getPort());
        assertThat(uri.getPath()).isEqualTo("/memoirist-media/" + key);

        HttpResponse<byte[]> fetched = get(uri);
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).isEqualTo(photo);
        assertThat(fetched.headers().firstValue("Content-Type")).hasValue("image/jpeg");

        storage.delete(key);

        assertThat(get(uri).statusCode()).as("the same URL after the delete").isEqualTo(404);
        assertThat(exists(key)).isFalse();
    }

    @Test
    void aFileSeveralMegabytesLongStreamsInIntact(@TempDir Path folder) throws Exception {
        // A phone photo, read from disk the way an uploaded multipart file is. A file stream can't be marked and reset,
        // so the SDK can't buffer it whole, which is what makes this the realistic case.
        byte[] photo = randomBytes(5 * 1024 * 1024, 2);
        Path file = Files.write(folder.resolve("photo.jpg"), photo);
        String key = newKey("jpg");

        try (InputStream content = Files.newInputStream(file)) {
            storage.put(key, content, photo.length, "image/jpeg");
        }

        HttpResponse<byte[]> fetched = get(URI.create(storage.presignGet(key)));
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).hasSize(photo.length).isEqualTo(photo);
        assertThat(contentLength(key)).isEqualTo(photo.length);

        storage.delete(key);
    }

    @Test
    void theDeclaredContentTypeComesBackWhenTheFileIsFetched() throws Exception {
        String key = newKey("webm");
        storage.put(key, randomBytes(512, 3), "audio/webm");

        assertThat(get(URI.create(storage.presignGet(key))).headers().firstValue("Content-Type"))
                .hasValue("audio/webm");

        storage.delete(key);
    }

    @Test
    void putReplacesWhatWasThere() throws Exception {
        String key = newKey("png");
        storage.put(key, new byte[] {1, 2, 3}, "image/png");
        storage.put(key, new byte[] {9, 9}, "image/png");

        assertThat(get(URI.create(storage.presignGet(key))).body()).containsExactly(9, 9);

        storage.delete(key);
    }

    @Test
    void deletingAKeyThatIsNotThereIsNotAnError() {
        String key = newKey("jpg");

        storage.delete(key);
        storage.delete(key);

        assertThat(exists(key)).isFalse();
    }

    @Test
    void anUnsignedRequestForTheSameFileIsRefused() throws Exception {
        String key = newKey("jpg");
        storage.put(key, randomBytes(256, 4), "image/jpeg");
        URI signed = URI.create(storage.presignGet(key));
        URI unsigned =
                new URI(signed.getScheme(), null, signed.getHost(), signed.getPort(), signed.getPath(), null, null);

        // The bucket is private (D-03): the path alone gets nothing, only the signed URL does.
        assertThat(get(unsigned).statusCode()).isEqualTo(403);
        assertThat(get(signed).statusCode()).isEqualTo(200);

        storage.delete(key);
    }

    @Test
    void aSignedUrlOpensOneFileOnly() throws Exception {
        String mine = newKey("jpg");
        String someoneElses = newKey("jpg");
        storage.put(mine, randomBytes(256, 5), "image/jpeg");
        storage.put(someoneElses, randomBytes(256, 6), "image/jpeg");
        URI signedForMine = URI.create(storage.presignGet(mine));

        // The same signature on another file's path: the key is part of what was signed.
        URI swapped = URI.create(signedForMine.toString().replace(mine, someoneElses));

        assertThat(swapped).isNotEqualTo(signedForMine);
        assertThat(get(swapped).statusCode()).isEqualTo(403);

        storage.delete(mine);
        storage.delete(someoneElses);
    }

    private HttpResponse<byte[]> get(URI uri) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    /** Whether the object is stored, asked of storage directly rather than through {@link StorageService}. */
    private boolean exists(String key) {
        try {
            s3.headObject(request -> request.bucket(properties.bucket()).key(key));
            return true;
        } catch (NoSuchKeyException missing) {
            return false;
        }
    }

    private long contentLength(String key) {
        HeadObjectResponse head =
                s3.headObject(request -> request.bucket(properties.bucket()).key(key));
        return head.contentLength();
    }

    /** A key shaped like the real ones (TECH_STACK §2 Storage), unique to the call. */
    static String newKey(String extension) {
        return "users/%s/entries/%s/%s.%s"
                .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), extension);
    }

    /** Repeatable pseudo-random bytes: a failure shows the same data every run. */
    static byte[] randomBytes(int length, long seed) {
        byte[] bytes = new byte[length];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }
}

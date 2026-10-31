package com.memoirist.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.memoirist.SeaweedFsTestConfiguration;
import com.memoirist.entry.EntryApiTest;
import com.memoirist.storage.StorageService;
import com.memoirist.user.User;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * What every {@code /api/v1} media test needs, on top of {@link EntryApiTest}: a real SeaweedFS to store into, a way to
 * send a multipart upload, and a way to look at what is really stored and what is really in the table.
 *
 * <p>{@link StorageService} is a Mockito <em>spy</em>: every call goes to the real thing unless a test says otherwise,
 * which lets a test change the world in the middle of an upload (seal the entry while its file is in flight) or make
 * storage fail on one call. All the subclasses are configured alike, so they share one app, one Postgres and one
 * SeaweedFS.
 */
@Import(SeaweedFsTestConfiguration.class)
abstract class MediaApiTest extends EntryApiTest {

    @MockitoSpyBean
    protected StorageService storage;

    /** The SDK client itself, to ask the storage service what it really holds. */
    @Autowired
    protected S3Client s3;

    @LocalServerPort
    protected int port;

    // Uploads

    /** A multipart upload of one file with the given declared type, and no other fields. */
    protected RestTestClient.ResponseSpec upload(User user, Object entryId, byte[] bytes, String contentType) {
        return upload(user, entryId, bytes, contentType, Map.of());
    }

    protected RestTestClient.ResponseSpec upload(
            User user, Object entryId, byte[] bytes, String contentType, Map<String, String> fields) {
        MultipartBodyBuilder form = new MultipartBodyBuilder();
        form.part("file", file(bytes)).contentType(MediaType.parseMediaType(contentType));
        fields.forEach(form::part);
        return client.post()
                .uri(ENTRIES + "/" + entryId + "/media")
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form.build())
                .exchange();
    }

    /** A form with no {@code file} part at all, only the given fields. */
    protected RestTestClient.ResponseSpec uploadWithoutFile(User user, Object entryId, Map<String, String> fields) {
        MultipartBodyBuilder form = new MultipartBodyBuilder();
        fields.forEach(form::part);
        form.part("note", "nothing to see");
        return client.post()
                .uri(ENTRIES + "/" + entryId + "/media")
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form.build())
                .exchange();
    }

    /** Uploads a JPEG that must be accepted ({@code 201}) and returns the item's JSON. */
    protected Map<String, Object> uploadPhoto(User user, Object entryId) {
        return uploadPhoto(user, entryId, Map.of());
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> uploadPhoto(User user, Object entryId, Map<String, String> fields) {
        return upload(user, entryId, bytes(2048, new Random().nextInt()), "image/jpeg", fields)
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    /** The ids of the uploaded items, in the order they were made. */
    protected List<String> uploadPhotos(User user, Object entryId, int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(uploadPhoto(user, entryId).get("id").toString());
        }
        return ids;
    }

    /**
     * Sends a multipart upload with a known {@code Content-Length}, the way a browser's {@code FormData} does. Spring's
     * test client streams the body as chunked, and an early {@code 413} can then surface as a dropped connection; this
     * one tests the server instead of the client's habits (ErrorHandlingIntegrationTest does the same).
     */
    protected HttpResponse<String> uploadWithLength(User user, Object entryId, int fileBytes, String contentType)
            throws IOException, InterruptedException {
        String boundary = "memoirist-test-boundary";
        byte[] head = ("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"big.bin\"\r\n"
                        + "Content-Type: " + contentType + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + ENTRIES + "/" + entryId + "/media"))
                .header(HEADER, user.getId().toString())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head, new byte[fileBytes], tail)))
                .build();
        try (HttpClient http =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    // The other two calls

    protected RestTestClient.ResponseSpec reorder(User user, Object entryId, Object body) {
        return client.put()
                .uri(ENTRIES + "/" + entryId + "/media/order")
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    protected RestTestClient.ResponseSpec deleteMedia(User user, Object mediaId) {
        return client.delete()
                .uri("/api/v1/media/" + mediaId)
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    // Looking at the table

    /** The ids of an entry's media rows, in carousel order, read with SQL. */
    protected List<String> idsInOrder(Object entryId) {
        return jdbc.sql("SELECT id::text FROM media WHERE entry_id = :id ORDER BY display_order")
                .param("id", UUID.fromString(entryId.toString()))
                .query(String.class)
                .list();
    }

    /** The positions of an entry's media rows, in carousel order: {@code [0, 1, 2]} when nothing is wrong. */
    protected List<Integer> positions(Object entryId) {
        return jdbc.sql("SELECT display_order FROM media WHERE entry_id = :id ORDER BY display_order")
                .param("id", UUID.fromString(entryId.toString()))
                .query(Integer.class)
                .list();
    }

    protected String objectKeyOf(Object mediaId) {
        return jdbc.sql("SELECT object_key FROM media WHERE id = :id")
                .param("id", UUID.fromString(mediaId.toString()))
                .query(String.class)
                .single();
    }

    protected List<String> objectKeysOf(Object entryId) {
        return jdbc.sql("SELECT object_key FROM media WHERE entry_id = :id ORDER BY display_order")
                .param("id", UUID.fromString(entryId.toString()))
                .query(String.class)
                .list();
    }

    protected long mediaRowCount(Object entryId) {
        return jdbc.sql("SELECT count(*) FROM media WHERE entry_id = :id")
                .param("id", UUID.fromString(entryId.toString()))
                .query(Long.class)
                .single();
    }

    protected int versionOf(Object entryId) {
        return jdbc.sql("SELECT version FROM entries WHERE id = :id")
                .param("id", UUID.fromString(entryId.toString()))
                .query(Integer.class)
                .single();
    }

    // Looking at the store

    /** Whether the object really is in the bucket, asked of the SDK and not of our own service. */
    protected boolean stored(String objectKey) {
        try {
            s3.headObject(HeadObjectRequest.builder()
                    .bucket("memoirist-media")
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException gone) {
            return false;
        }
    }

    /** The Content-Type the store holds for the object, which is what a browser is told when it fetches the file. */
    protected String storedContentType(String objectKey) {
        return s3.headObject(HeadObjectRequest.builder()
                        .bucket("memoirist-media")
                        .key(objectKey)
                        .build())
                .contentType();
    }

    /** The upload was refused before any file went to storage: the spy saw no {@code put} (of a stream) at all. */
    protected void assertNothingWasStored() {
        verify(storage, never()).put(anyString(), any(InputStream.class), anyLong(), anyString());
    }

    // Bytes

    /** Reproducible pseudo-random bytes, so a test can tell one file from another. */
    protected static byte[] bytes(int length, long seed) {
        byte[] content = new byte[length];
        new Random(seed).nextBytes(content);
        return content;
    }

    private static ByteArrayResource file(byte[] bytes) {
        // A file name is needed for Spring to treat the part as a file; the server never uses it.
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "upload.bin";
            }
        };
    }
}

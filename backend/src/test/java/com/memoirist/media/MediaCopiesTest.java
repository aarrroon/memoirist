package com.memoirist.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.doThrow;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.common.StorageUnavailableException;
import com.memoirist.entry.RecentlyDeletedApiTest;
import com.memoirist.user.User;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Smaller copies of photos (D-43, task 4.18): the browser sends a 480 px thumbnail and a 1600 px screen copy in the
 * same upload as the photo. They are checked like the original (D-17), stored next to it on the same row, signed only
 * when the photo may be seen (never while its entry is sealed, D-10), and removed with it by every path that removes
 * files: deleting the photo, erasing the entry and the 30-day sweep (D-30).
 *
 * <p>It extends {@link RecentlyDeletedApiTest} for {@code erase} and the sweeper; the clock starts every test at
 * Saturday 2026-10-31 at 01:00 UTC.
 */
class MediaCopiesTest extends RecentlyDeletedApiTest {

    private static final String SOME_DAY_AHEAD = "2026-11-30T00:00:00Z";

    private static Part jpeg(int length, long seed) {
        return new Part(bytes(length, seed), "image/jpeg");
    }

    private byte[] fetch(Object url) throws Exception {
        HttpResponse<byte[]> fetched = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(url.toString())).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        assertThat(fetched.statusCode()).isEqualTo(200);
        return fetched.body();
    }

    private String thumbnailKeyOf(Object mediaId) {
        return jdbc.sql("SELECT thumbnail_key FROM media WHERE id = :id")
                .param("id", java.util.UUID.fromString(mediaId.toString()))
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private String screenKeyOf(Object mediaId) {
        return jdbc.sql("SELECT screen_key FROM media WHERE id = :id")
                .param("id", java.util.UUID.fromString(mediaId.toString()))
                .query(String.class)
                .optional()
                .orElse(null);
    }

    // Storing and signing

    @Test
    void aPhotoWithBothCopiesStoresThreeFilesOnOneRowAndSignsEach() throws Exception {
        User user = aUser();
        String entryId = createTitledEntry(user);
        byte[] original = bytes(4096, 1);
        byte[] thumbnail = bytes(512, 2);
        byte[] screen = bytes(1024, 3);

        RestTestClient.ResponseSpec response = uploadWithCopies(
                user,
                entryId,
                new Part(original, "image/jpeg"),
                new Part(thumbnail, "image/jpeg"),
                new Part(screen, "image/jpeg"),
                Map.of());

        response.expectStatus().isCreated();
        Map<String, Object> media = bodyMap(response);
        Object id = media.get("id");
        String base = "users/" + user.getId() + "/entries/" + entryId + "/" + id;
        assertThat(mediaRowCount(entryId))
                .as("one row for the photo and its copies")
                .isEqualTo(1);
        assertThat(objectKeyOf(id)).isEqualTo(base + ".jpg");
        assertThat(thumbnailKeyOf(id)).isEqualTo(base + "-thumbnail.jpg");
        assertThat(screenKeyOf(id)).isEqualTo(base + "-screen.jpg");
        assertThat(allStoredKeysOf(entryId))
                .allSatisfy(key -> assertThat(stored(key)).isTrue());

        // Each URL is signed, names its own file, and returns that file's bytes.
        assertThat(media.get("thumbnailUrl").toString())
                .contains(base + "-thumbnail.jpg")
                .contains("X-Amz-Signature=");
        assertThat(media.get("screenUrl").toString())
                .contains(base + "-screen.jpg")
                .contains("X-Amz-Signature=");
        assertThat(fetch(media.get("fileUrl"))).isEqualTo(original);
        assertThat(fetch(media.get("thumbnailUrl"))).isEqualTo(thumbnail);
        assertThat(fetch(media.get("screenUrl"))).isEqualTo(screen);
        // The original's size is what the row says; the copies don't change it.
        assertThat(((Number) media.get("fileSizeBytes")).longValue()).isEqualTo(4096);
    }

    @Test
    void theCopiesComeBackOnTheEntryAndInTheFeed() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhotoWithCopies(user, entryId);

        String entry = bodyText(get(user, entryId).expectStatus().isOk());
        assertThat(JsonPath.<String>read(entry, "$.media[0].thumbnailUrl")).contains("-thumbnail.jpg");
        assertThat(JsonPath.<String>read(entry, "$.media[0].screenUrl")).contains("-screen.jpg");
        assertThat(feedBody(user)).contains("-thumbnail.jpg").contains("-screen.jpg");
    }

    @Test
    void aPngPhotosJpegCopiesAreStoredUnderTheirOwnExtensionAndType() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        Map<String, Object> media = bodyMap(uploadWithCopies(
                        user,
                        entryId,
                        new Part(TestImages.png(64, 48), "image/png"),
                        new Part(TestImages.jpeg(32, 24, false), "image/jpeg"),
                        null,
                        Map.of())
                .expectStatus()
                .isCreated());

        assertThat(objectKeyOf(media.get("id"))).endsWith(".png");
        assertThat(thumbnailKeyOf(media.get("id"))).endsWith("-thumbnail.jpg");
        assertThat(storedContentType(thumbnailKeyOf(media.get("id")))).isEqualTo("image/jpeg");
        // The size on the row is the original's, read from its own header, not the copy's.
        assertThat(media).containsEntry("width", 64).containsEntry("height", 48);
    }

    @Test
    void aWebpPhotoKeepsWebpCopies() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        byte[] webp = TestImages.webp("lossy-30x20.webp");

        Map<String, Object> media = bodyMap(uploadWithCopies(
                        user,
                        entryId,
                        new Part(webp, "image/webp"),
                        new Part(webp, "image/webp"),
                        new Part(webp, "image/webp"),
                        Map.of())
                .expectStatus()
                .isCreated());

        assertThat(thumbnailKeyOf(media.get("id"))).endsWith("-thumbnail.webp");
        assertThat(screenKeyOf(media.get("id"))).endsWith("-screen.webp");
    }

    @Test
    void aDiaryPageTakesCopiesLikeAPhoto() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        Map<String, Object> media = bodyMap(uploadWithCopies(
                        user, entryId, jpeg(2048, 4), jpeg(256, 5), jpeg(512, 6), Map.of("purpose", "PAGE_SCAN"))
                .expectStatus()
                .isCreated());

        assertThat(media).containsEntry("purpose", "PAGE_SCAN");
        assertThat(media.get("thumbnailUrl")).isNotNull();
        assertThat(media.get("screenUrl")).isNotNull();
    }

    // No copies

    @Test
    void aPhotoSentWithoutCopiesStoresNoneAndAnswersNullForBoth() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        // What the browser sends for a photo no bigger than a thumbnail (D-43), and what an older client sends.
        Map<String, Object> media = uploadPhoto(user, entryId);

        assertThat(media).containsEntry("thumbnailUrl", null).containsEntry("screenUrl", null);
        assertThat(thumbnailKeyOf(media.get("id"))).isNull();
        assertThat(screenKeyOf(media.get("id"))).isNull();
        assertThat(allStoredKeysOf(entryId)).hasSize(1);
    }

    @Test
    void aPhotoBetweenTheTwoSizesHasAThumbnailAndNoScreenCopy() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        // A 1200 px photo: bigger than a thumbnail, no bigger than a screen copy, so the browser makes one copy.
        Map<String, Object> media = bodyMap(uploadWithCopies(user, entryId, jpeg(2048, 7), jpeg(256, 8), null, Map.of())
                .expectStatus()
                .isCreated());

        assertThat(media.get("thumbnailUrl")).isNotNull();
        assertThat(media).containsEntry("screenUrl", null);
        assertThat(allStoredKeysOf(entryId)).hasSize(2);
    }

    @Test
    void aVoiceNoteHasNoCopies() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        Map<String, Object> note = bodyMap(upload(user, entryId, bytes(300, 9), "audio/webm")
                .expectStatus()
                .isCreated());

        assertThat(note).containsEntry("thumbnailUrl", null).containsEntry("screenUrl", null);
    }

    // Refused copies: nothing is stored, no row is written

    @Test
    void aVoiceNoteSentWithACopyIs400AndNothingIsStored() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        String body = bodyText(
                uploadWithCopies(user, entryId, new Part(bytes(300, 10), "audio/webm"), jpeg(100, 11), null, Map.of())
                        .expectStatus()
                        .isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsEntry("thumbnail", "only a photo or a diary page has copies");
        assertNothingWasStored();
        assertThat(mediaRowCount(entryId)).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"audio/webm", "application/pdf", "image/heic", "text/html"})
    void aCopyThatIsNotAnAllowedImageIs415AndNothingIsStored(String type) {
        User user = aUser();
        String entryId = createTitledEntry(user);

        String body = bodyText(
                uploadWithCopies(user, entryId, jpeg(2048, 12), jpeg(100, 13), new Part(bytes(100, 14), type), Map.of())
                        .expectStatus()
                        .isEqualTo(415));

        assertThat(problemType(body)).isEqualTo("unsupported-media-type");
        assertThat(body).doesNotContain(type);
        assertNothingWasStored();
        assertThat(mediaRowCount(entryId)).isZero();
    }

    @Test
    void anEmptyCopyIs400AndNothingIsStored() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        String body = bodyText(
                uploadWithCopies(user, entryId, jpeg(2048, 15), null, new Part(new byte[0], "image/jpeg"), Map.of())
                        .expectStatus()
                        .isBadRequest());

        assertThat(fieldErrors(body)).containsEntry("screen", "must not be empty");
        assertNothingWasStored();
    }

    @Test
    void aCopyOver25MbIs413AndNothingIsStored() throws Exception {
        User user = aUser();
        String entryId = createTitledEntry(user);

        HttpResponse<String> response = uploadWithABigCopy(user, entryId, (int) UploadRules.MAX_FILE_BYTES + 1024);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(problemType(response.body())).isEqualTo("payload-too-large");
        assertNothingWasStored();
        assertThat(mediaRowCount(entryId)).isZero();
    }

    /** A small photo with a thumbnail of the given length, with a known Content-Length (see uploadWithLength). */
    private HttpResponse<String> uploadWithABigCopy(User user, Object entryId, int copyBytes) throws Exception {
        String boundary = "memoirist-test-boundary";
        byte[] photo = ("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\n"
                        + "Content-Type: image/jpeg\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] copyHead = ("\r\n--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"thumbnail\"; filename=\"thumb.jpg\"\r\n"
                        + "Content-Type: image/jpeg\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + ENTRIES + "/" + entryId + "/media"))
                .header(HEADER, user.getId().toString())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(
                        List.of(photo, bytes(1024, 16), copyHead, new byte[copyBytes], tail)))
                .build();
        try (HttpClient http =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void anotherUsersEntryIs404AndNoCopyIsStored() {
        User alice = aUser();
        User bob = aUser();
        String alicesEntry = createTitledEntry(alice);

        uploadWithCopies(bob, alicesEntry, jpeg(2048, 17), jpeg(100, 18), jpeg(200, 19), Map.of())
                .expectStatus()
                .isNotFound();

        assertNothingWasStored();
        assertThat(mediaRowCount(alicesEntry)).isZero();
    }

    @Test
    void aPhotoWithCopiesSentToASealedEntryIs409AndNothingIsStored() {
        // The seal check runs before any copy check (D-10, D-43): a sealed entry takes no file of any size.
        User user = aUser();
        String entryId = createTitledEntry(user);
        seal(user, entryId, SOME_DAY_AHEAD).expectStatus().isOk();

        String body = bodyText(uploadWithCopies(user, entryId, jpeg(2048, 23), jpeg(100, 24), jpeg(200, 25), Map.of())
                .expectStatus()
                .isEqualTo(409));

        assertThat(problemType(body)).isEqualTo("entry-sealed");
        assertNothingWasStored();
        assertThat(mediaRowCount(entryId)).isZero();
        String prefix = "users/" + user.getId() + "/entries/" + entryId + "/";
        assertThat(s3.listObjectsV2(r -> r.bucket("memoirist-media").prefix(prefix))
                        .contents())
                .isEmpty();
    }

    @Test
    void aCopyThatFailsToStoreTakesTheOriginalAndTheOtherCopyOutAgain() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        doThrow(new StorageUnavailableException("Storing an object failed", new RuntimeException("storage is down")))
                .when(storage)
                .put(endsWith("-screen.jpg"), any(InputStream.class), anyLong(), anyString());

        String body = bodyText(uploadWithCopies(user, entryId, jpeg(2048, 20), jpeg(100, 21), jpeg(200, 22), Map.of())
                .expectStatus()
                .isEqualTo(503));

        assertThat(problemType(body)).isEqualTo("storage-unavailable");
        assertThat(mediaRowCount(entryId)).isZero();
        // Every file the upload began is gone: listed in the bucket under the entry's prefix, nothing is left.
        String prefix = "users/" + user.getId() + "/entries/" + entryId + "/";
        assertThat(s3.listObjectsV2(r -> r.bucket("memoirist-media").prefix(prefix))
                        .contents())
                .isEmpty();
    }

    // The seal (D-10)

    @Test
    void aSealedEntrysCopiesAreNeverSignedAndComeBackWhenItOpens() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhotoWithCopies(user, entryId);
        seal(user, entryId, SOME_DAY_AHEAD).expectStatus().isOk();

        String entry = bodyText(get(user, entryId).expectStatus().isOk());
        String feed = feedBody(user);
        for (String answer : List.of(entry, feed, listBody(user))) {
            assertThat(answer)
                    .doesNotContain("-thumbnail")
                    .doesNotContain("-screen")
                    .doesNotContain("X-Amz-Signature");
        }
        assertThat(JsonPath.<List<Object>>read(entry, "$.media")).isEmpty();

        // The unlock instant itself opens it, and the copies are signed again.
        clockAt(SOME_DAY_AHEAD);
        String opened = bodyText(get(user, entryId).expectStatus().isOk());
        assertThat(JsonPath.<String>read(opened, "$.media[0].thumbnailUrl")).contains("X-Amz-Signature=");
        assertThat(JsonPath.<String>read(opened, "$.media[0].screenUrl")).contains("X-Amz-Signature=");
    }

    @Test
    void anErasCoverShowsItsCopiesButNotWhileItsEntryIsSealed() {
        User user = aUser();
        String entryId = entryOn(user, W4, "Beach");
        Object photo = uploadPhotoWithCopies(user, entryId).get("id");
        RestTestClient.ResponseSpec created = client.post()
                .uri(ERAS)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("title", "Summer", "startDate", "2026-10-01", "coverMediaId", photo.toString()))
                .exchange();
        created.expectStatus().isCreated();
        assertThat(JsonPath.<String>read(bodyText(created), "$.cover.thumbnailUrl"))
                .contains("-thumbnail.jpg");

        seal(user, entryId, SOME_DAY_AHEAD).expectStatus().isOk();

        String eras = bodyText(
                client.get().uri(ERAS).header(HEADER, user.getId().toString()).exchange());
        assertThat(JsonPath.<Object>read(eras, "$[0].cover")).isNull();
        assertThat(eras).doesNotContain("-thumbnail").doesNotContain("-screen");
    }

    // Every file goes with the photo (D-30)

    @Test
    void deletingThePhotoRemovesItsCopiesToo() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        Object id = uploadPhotoWithCopies(user, entryId).get("id");
        List<String> keys = allStoredKeysOf(entryId);
        assertThat(keys).hasSize(3).allSatisfy(key -> assertThat(stored(key)).isTrue());

        deleteMedia(user, id).expectStatus().isNoContent();

        assertThat(keys).allSatisfy(key -> assertThat(stored(key)).isFalse());
    }

    @Test
    void erasingTheEntryRemovesEveryCopyAndLeavesAnotherEntrysAlone() {
        User user = aUser();
        String doomed = createTitledEntry(user);
        String kept = createTitledEntry(user);
        uploadPhotoWithCopies(user, doomed);
        uploadPhotoWithCopies(user, doomed);
        uploadPhotoWithCopies(user, kept);
        List<String> doomedKeys = allStoredKeysOf(doomed);
        List<String> keptKeys = allStoredKeysOf(kept);
        assertThat(doomedKeys).hasSize(6);
        deleteOk(user, doomed);
        assertThat(doomedKeys)
                .as("Recently deleted keeps every file")
                .allSatisfy(key -> assertThat(stored(key)).isTrue());

        erase(user, doomed).expectStatus().isNoContent();

        assertThat(doomedKeys).allSatisfy(key -> assertThat(stored(key)).isFalse());
        assertThat(keptKeys).allSatisfy(key -> assertThat(stored(key)).isTrue());
    }

    @Test
    void theThirtyDaySweepRemovesEveryCopy() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhotoWithCopies(user, entryId);
        List<String> keys = allStoredKeysOf(entryId);
        deleteOk(user, entryId);
        clock.set(Instant.now(clock).plus(Duration.ofDays(31)));

        assertThat(sweeper.sweep()).isEqualTo(1);

        assertThat(rowExists(entryId)).isFalse();
        assertThat(keys).hasSize(3).allSatisfy(key -> assertThat(stored(key)).isFalse());
    }
}

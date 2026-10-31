package com.memoirist.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import jakarta.servlet.MultipartConfigElement;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code POST /entries/{entryId}/media}: the happy paths, then every way to be refused, each with a check that nothing
 * was stored (a refused upload must leave no file behind and no row).
 */
class MediaUploadTest extends MediaApiTest {

    private static final String SOME_DAY_AHEAD = "2026-11-30T00:00:00Z";

    @Autowired
    MultipartConfigElement multipartConfig;

    // The happy path

    @Test
    void aJpegIsStoredUnderTheEntrysKeyAndComesBackWithAPresignedUrl() throws Exception {
        User user = aUser();
        String entryId = createTitledEntry(user);
        byte[] photo = bytes(4096, 1);

        Map<String, Object> media = upload(user, entryId, photo, "image/jpeg")
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        // Exactly the fields of the contract: no object key, no storage host on its own.
        assertThat(media.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "entryId",
                        "fileUrl",
                        "mediaType",
                        "purpose",
                        "displayOrder",
                        "fileSizeBytes",
                        "uploadedAt");
        assertThat(media)
                .containsEntry("entryId", entryId)
                .containsEntry("mediaType", "image/jpeg")
                .containsEntry("purpose", "PHOTO")
                .containsEntry("displayOrder", 0);
        assertThat(((Number) media.get("fileSizeBytes")).longValue()).isEqualTo(4096);
        assertThat(media.get("uploadedAt").toString()).endsWith("Z");

        // The key is users/{userId}/entries/{entryId}/{mediaId}.jpg, and it is the only place a key is stored.
        String key = objectKeyOf(media.get("id"));
        assertThat(key).isEqualTo("users/" + user.getId() + "/entries/" + entryId + "/" + media.get("id") + ".jpg");
        assertThat(media.get("fileUrl").toString()).contains(key).contains("X-Amz-Signature=");

        // The signed URL really returns the bytes that were uploaded, as the type that was declared.
        HttpResponse<byte[]> fetched = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(media.get("fileUrl").toString()))
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).isEqualTo(photo);
        assertThat(fetched.headers().firstValue("Content-Type")).hasValue("image/jpeg");
    }

    @Test
    void theUploadShowsInTheEntryAndDoesNotChangeItsVersion() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        int versionBefore = versionOf(entryId);

        Map<String, Object> media = uploadPhoto(user, entryId);

        // A photo added while the user types a caption must not make the caption's PUT stale (D-23).
        assertThat(versionOf(entryId)).isEqualTo(versionBefore);
        List<?> shown = (List<?>) getEntry(user, entryId).get("media");
        assertThat(shown).hasSize(1);
        assertThat(((Map<?, ?>) shown.get(0)).get("id")).isEqualTo(media.get("id"));
    }

    @ParameterizedTest
    @CsvSource({
        "image/jpeg,jpg",
        "image/png,png",
        "image/webp,webp",
        "audio/webm,webm",
        "audio/mp4,m4a",
        "audio/mpeg,mp3",
    })
    void everyTypeOnTheD17ListIsAcceptedAndKeepsItsExtension(String type, String extension) {
        User user = aUser();
        String entryId = createTitledEntry(user);

        Map<String, Object> media = upload(user, entryId, bytes(512, 2), type)
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(media).containsEntry("mediaType", type);
        assertThat(objectKeyOf(media.get("id"))).endsWith("." + extension);
    }

    @Test
    void aBrowsersVoiceRecordingKeepsOnlyThePlainTypeInTheTableAndInStorage() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        // Chrome's MediaRecorder labels its recordings "audio/webm;codecs=opus".
        Map<String, Object> media = upload(user, entryId, bytes(900, 3), "audio/webm;codecs=opus")
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(media).containsEntry("mediaType", "audio/webm");
        assertThat(jdbc.sql("SELECT media_type FROM media WHERE id = :id")
                        .param("id", UUID.fromString(media.get("id").toString()))
                        .query(String.class)
                        .single())
                .isEqualTo("audio/webm");
        assertThat(storedContentType(objectKeyOf(media.get("id")))).isEqualTo("audio/webm");
    }

    @Test
    void capitalLettersInTheDeclaredTypeAreFine() {
        User user = aUser();

        upload(user, createTitledEntry(user), bytes(100, 4), "IMAGE/JPEG")
                .expectStatus()
                .isCreated();
    }

    // Where the new item goes

    @Test
    void itemsAreAppendedInTheOrderTheyArrive() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        List<String> ids = uploadPhotos(user, entryId, 3);

        assertThat(idsInOrder(entryId)).isEqualTo(ids);
        assertThat(positions(entryId)).containsExactly(0, 1, 2);
        assertThat(((List<?>) getEntry(user, entryId).get("media")))
                .extracting(item -> ((Map<?, ?>) item).get("id"))
                .isEqualTo(ids);
    }

    @Test
    void aDisplayOrderInsertsThereAndMovesTheRestDownOne() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 3); // a, b, c

        String atTheFront = uploadPhoto(user, entryId, Map.of("displayOrder", "0"))
                .get("id")
                .toString();
        String inTheMiddle = uploadPhoto(user, entryId, Map.of("displayOrder", "2"))
                .get("id")
                .toString();
        String atTheEnd = uploadPhoto(user, entryId, Map.of("displayOrder", "5"))
                .get("id")
                .toString();

        assertThat(idsInOrder(entryId))
                .containsExactly(atTheFront, ids.get(0), inTheMiddle, ids.get(1), ids.get(2), atTheEnd);
        assertThat(positions(entryId)).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    void aDisplayOrderOfTheItemCountAppends() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhotos(user, entryId, 2);

        Map<String, Object> media = uploadPhoto(user, entryId, Map.of("displayOrder", "2"));

        assertThat(media).containsEntry("displayOrder", 2);
        assertThat(positions(entryId)).containsExactly(0, 1, 2);
    }

    @Test
    void aDisplayOrderPastTheEndIs400AndSaysHowFarTheEndIs() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhotos(user, entryId, 2);

        String body = bodyText(upload(user, entryId, bytes(100, 5), "image/jpeg", Map.of("displayOrder", "3"))
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsEntry("displayOrder", "must be between 0 and 2");
        assertThat(positions(entryId)).containsExactly(0, 1);
    }

    @Test
    void aNegativeDisplayOrderIs400() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        String body = bodyText(upload(user, entryId, bytes(100, 6), "image/jpeg", Map.of("displayOrder", "-1"))
                .expectStatus()
                .isBadRequest());

        assertThat(fieldErrors(body)).containsEntry("displayOrder", "must not be negative");
        assertNothingWasStored();
    }

    @Test
    void aDisplayOrderThatIsNotAWholeNumberIs400MalformedRequest() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        String body = bodyText(upload(user, entryId, bytes(100, 7), "image/jpeg", Map.of("displayOrder", "first"))
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
        assertThat(JsonPath.<String>read(body, "$.detail")).doesNotContain("first");
        assertNothingWasStored();
    }

    // Purposes (D-20)

    @Test
    void aPageScanIsAnImageMarkedAsAPage() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        Map<String, Object> media = uploadPhoto(user, entryId, Map.of("purpose", "PAGE_SCAN"));

        assertThat(media).containsEntry("purpose", "PAGE_SCAN").containsEntry("mediaType", "image/jpeg");
        assertThat(getEntry(user, entryId).get("media").toString()).contains("PAGE_SCAN");
    }

    @Test
    void thePurposeDefaultsToPhotoAndPhotoCanBeSaidOutLoud() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        assertThat(uploadPhoto(user, entryId)).containsEntry("purpose", "PHOTO");
        assertThat(uploadPhoto(user, entryId, Map.of("purpose", "PHOTO"))).containsEntry("purpose", "PHOTO");
    }

    @Test
    void aPageScanThatIsNotAnImageIs415() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        String body = bodyText(upload(user, entryId, bytes(300, 8), "audio/webm", Map.of("purpose", "PAGE_SCAN"))
                .expectStatus()
                .isEqualTo(415));

        assertThat(problemType(body)).isEqualTo("unsupported-media-type");
        assertNothingWasStored();
        assertThat(mediaRowCount(entryId)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SCRAPBOOK", "photo", "page_scan", "", " ", "VOICE_NOTE"})
    void anUnknownPurposeIs400AndIsNotRepeated(String purpose) {
        User user = aUser();
        String entryId = createTitledEntry(user);

        String body = bodyText(upload(user, entryId, bytes(300, 9), "image/jpeg", Map.of("purpose", purpose))
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsEntry("purpose", "must be PHOTO or PAGE_SCAN");
        assertNothingWasStored();
        assertThat(mediaRowCount(entryId)).isZero();
    }

    // Types (D-17)

    @ParameterizedTest
    @ValueSource(
            strings = {
                "application/pdf",
                "image/gif",
                "image/heic",
                "image/svg+xml",
                "video/mp4",
                "text/html",
                "application/octet-stream",
            })
    void aTypeThatIsNotOnTheListIs415AndTheTypeIsNotRepeated(String type) {
        User user = aUser();
        String entryId = createTitledEntry(user);

        String body = bodyText(
                upload(user, entryId, bytes(300, 10), type).expectStatus().isEqualTo(415));

        assertThat(problemType(body)).isEqualTo("unsupported-media-type");
        assertThat(JsonPath.<String>read(body, "$.detail")).doesNotContain(type.substring(type.indexOf('/') + 1));
        assertNothingWasStored();
        assertThat(mediaRowCount(entryId)).isZero();
    }

    @Test
    void aRequestThatIsNotMultipartIs415() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        String body = bodyText(client.post()
                .uri(ENTRIES + "/" + entryId + "/media")
                .header(HEADER, user.getId().toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body("{\"file\": \"nope\"}")
                .exchange()
                .expectStatus()
                .isEqualTo(415));

        assertThat(problemType(body)).isEqualTo("unsupported-media-type");
        assertNothingWasStored();
    }

    // The file itself

    @Test
    void anEmptyFileIs400() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        String body = bodyText(
                upload(user, entryId, new byte[0], "image/jpeg").expectStatus().isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsEntry("file", "must not be empty");
        assertNothingWasStored();
        assertThat(mediaRowCount(entryId)).isZero();
    }

    @Test
    void aFormWithoutAFileIs400() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        String body = bodyText(uploadWithoutFile(user, entryId, Map.of("purpose", "PHOTO"))
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsEntry("file", "must not be null");
        assertNothingWasStored();
    }

    @Test
    void aFileOver25MbIs413AndNothingIsStored() throws Exception {
        User user = aUser();
        String entryId = createTitledEntry(user);

        // One kilobyte over, so that the server stops reading almost at the end and the answer gets through.
        HttpResponse<String> response =
                uploadWithLength(user, entryId, (int) UploadRules.MAX_FILE_BYTES + 1024, "image/jpeg");

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(problemType(response.body())).isEqualTo("payload-too-large");
        assertNothingWasStored();
        assertThat(mediaRowCount(entryId)).isZero();
    }

    @Test
    void aFileOfExactly25MbIsAccepted() throws Exception {
        User user = aUser();
        String entryId = createTitledEntry(user);

        HttpResponse<String> response = uploadWithLength(user, entryId, (int) UploadRules.MAX_FILE_BYTES, "image/jpeg");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<Number>read(response.body(), "$.fileSizeBytes").longValue())
                .isEqualTo(UploadRules.MAX_FILE_BYTES);
    }

    @Test
    void theServiceLimitAndTheMultipartSettingAreTheSameNumber() {
        // The size rule lives in two places: Spring's multipart limit stops a big file before it reaches the service,
        // and the service checks again where a unit test can reach it. A test says that they are one number.
        assertThat(multipartConfig.getMaxFileSize()).isEqualTo(UploadRules.MAX_FILE_BYTES);
    }

    // The limit of 20 items (D-17)

    @Test
    void theTwentyFirstItemIs400AndTheTwentyBeforeItAreKept() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhotos(user, entryId, 20);

        String body = bodyText(upload(user, entryId, bytes(100, 11), "image/jpeg")
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsKey("file");
        assertThat(mediaRowCount(entryId)).isEqualTo(20);
        // Twenty files went to storage and the twenty-first never did.
        verify(storage, times(20)).put(anyString(), any(InputStream.class), anyLong(), anyString());
        assertThat(positions(entryId)).hasSize(20).startsWith(0, 1, 2).endsWith(17, 18, 19);
    }

    // Ownership, Recently deleted and the seal

    @Test
    void anotherUsersEntryIs404AndNothingIsStored() {
        User alice = aUser();
        User bob = aUser();
        String aliceEntry = createTitledEntry(alice);

        String body = bodyText(upload(bob, aliceEntry, bytes(100, 12), "image/jpeg")
                .expectStatus()
                .isNotFound());

        assertThat(problemType(body)).isEqualTo("not-found");
        assertThat(JsonPath.<String>read(body, "$.detail")).doesNotContain(aliceEntry);
        assertNothingWasStored();
        assertThat(mediaRowCount(aliceEntry)).isZero();
    }

    @Test
    void anEntryThatDoesNotExistIs404() {
        User user = aUser();

        upload(user, UUID.randomUUID(), bytes(100, 13), "image/jpeg")
                .expectStatus()
                .isNotFound();

        assertNothingWasStored();
    }

    @Test
    void aUserWhoIsNotThereIs404() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        User nobody = new User(UUID.randomUUID(), "nobody@example.com", "Nobody", "Australia/Melbourne");

        upload(nobody, entryId, bytes(100, 14), "image/jpeg").expectStatus().isNotFound();

        assertNothingWasStored();
    }

    @Test
    void anIdThatIsNotAUuidIs400MalformedRequest() {
        User user = aUser();

        String body = bodyText(upload(user, "not-a-uuid", bytes(100, 15), "image/jpeg")
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
        assertThat(JsonPath.<String>read(body, "$.detail")).doesNotContain("not-a-uuid");
    }

    @Test
    void anEntryInRecentlyDeletedIs404AndNothingIsStored() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhoto(user, entryId);
        delete(user, entryId).expectStatus().isNoContent();

        upload(user, entryId, bytes(100, 16), "image/jpeg").expectStatus().isNotFound();

        verify(storage, times(1)).put(anyString(), any(InputStream.class), anyLong(), anyString());
        assertThat(mediaRowCount(entryId)).isEqualTo(1);
    }

    @Test
    void aSealedEntryIs409AndNothingIsStoredOrRevealed() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhoto(user, entryId);
        seal(user, entryId, SOME_DAY_AHEAD).expectStatus().isOk();

        String body = bodyText(upload(user, entryId, bytes(100, 17), "image/jpeg")
                .expectStatus()
                .isEqualTo(409));

        assertThat(problemType(body)).isEqualTo("entry-sealed");
        assertThat(body).doesNotContain("A title worth keeping").doesNotContain("users/");
        verify(storage, times(1)).put(anyString(), any(InputStream.class), anyLong(), anyString());
        assertThat(mediaRowCount(entryId)).isEqualTo(1);
    }

    @Test
    void aSealedEntryIs409EvenForAFileThatWouldBeRefusedAnyway() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        seal(user, entryId, SOME_DAY_AHEAD).expectStatus().isOk();

        // The entry's state answers first, so the answer says nothing about the request's own mistakes.
        upload(user, entryId, bytes(100, 18), "application/pdf").expectStatus().isEqualTo(409);
    }

    @Test
    void aLootDropThatHasOpenedTakesPhotosAgain() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        seal(user, entryId, SOME_DAY_AHEAD).expectStatus().isOk();
        upload(user, entryId, bytes(100, 19), "image/jpeg").expectStatus().isEqualTo(409);

        // The unlock instant itself opens it (D-10).
        clockAt(SOME_DAY_AHEAD);

        upload(user, entryId, bytes(100, 20), "image/jpeg").expectStatus().isCreated();
        assertThat(mediaRowCount(entryId)).isEqualTo(1);
    }
}

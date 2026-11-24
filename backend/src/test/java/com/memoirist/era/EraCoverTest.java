package com.memoirist.era;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.entry.RecentlyDeletedApiTest;
import com.memoirist.user.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Era covers (task 3.15, D-36) through the whole app: which photos a request may choose, what the Era shows while the
 * photo's entry is sealed or in Recently deleted, and what removing the photo does. The clock starts on Saturday
 * 2026-10-31 and moves with {@code clockAt}; {@code SEALED_UNTIL} (2027-01-01) is ahead of it.
 */
class EraCoverTest extends RecentlyDeletedApiTest {

    private static final String AFTER_SEALED_UNTIL = "2027-01-02T00:00:00Z";

    // Requests

    private RestTestClient.ResponseSpec postEra(User user, Map<String, Object> body) {
        return client.post()
                .uri(ERAS)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec getEra(User user, String eraId) {
        return client.get()
                .uri(ERAS + "/" + eraId)
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    private String erasBody(User user) {
        RestTestClient.ResponseSpec response =
                client.get().uri(ERAS).header(HEADER, user.getId().toString()).exchange();
        response.expectStatus().isOk();
        return bodyText(response);
    }

    private static Map<String, Object> eraBody(String title, Object coverMediaId) {
        return json("title", title, "startDate", "2026-10-01", "coverMediaId", coverMediaId);
    }

    /** Makes an Era with this cover and returns its id; the cover must be accepted. */
    private String eraWithCover(User user, String title, Object mediaId) {
        RestTestClient.ResponseSpec response = postEra(user, eraBody(title, mediaId.toString()));
        response.expectStatus().isCreated();
        return bodyMap(response).get("id").toString();
    }

    // What each place shows

    /** The detail's {@code cover} object as text, which is {@code null} when no cover is shown. */
    private Object coverInDetail(User user, String eraId) {
        RestTestClient.ResponseSpec response = getEra(user, eraId);
        response.expectStatus().isOk();
        return JsonPath.read(bodyText(response), "$.cover");
    }

    private Object coverIdInDetail(User user, String eraId) {
        RestTestClient.ResponseSpec response = getEra(user, eraId);
        response.expectStatus().isOk();
        return JsonPath.read(bodyText(response), "$.cover.id");
    }

    private Object firstCoverInList(User user) {
        return JsonPath.read(erasBody(user), "$[0].cover");
    }

    private String storedCover(String eraId) {
        UUID stored = jdbc.sql("SELECT cover_media_id FROM eras WHERE id = :id")
                .param("id", UUID.fromString(eraId))
                .query(UUID.class)
                .optional()
                .orElse(null);
        return stored == null ? null : stored.toString();
    }

    private long eraCount(User user) {
        return jdbc.sql("SELECT count(*) FROM eras WHERE user_id = :id")
                .param("id", user.getId())
                .query(Long.class)
                .single();
    }

    /** An entry of the owner's dated W4 and one photo in it. */
    private record Photo(User owner, String entryId, String mediaId) {}

    private Photo aPhotoOf(User owner, String title) {
        String entryId = entryOn(owner, W4, title);
        String mediaId =
                aPhoto(entryId, "users/test/covers/" + entryId + ".jpg", 0).toString();
        return new Photo(owner, entryId, mediaId);
    }

    // The happy path

    @Test
    void anErasCoverIsOneOfTheUsersPhotosAndComesBackPresignedEverywhere() {
        User alice = aUser();
        Photo photo = aPhotoOf(alice, "Beach");

        RestTestClient.ResponseSpec created = postEra(alice, eraBody("Summer", photo.mediaId()));

        created.expectStatus().isCreated();
        String body = bodyText(created);
        assertThat(JsonPath.<String>read(body, "$.cover.id")).isEqualTo(photo.mediaId());
        assertThat(JsonPath.<String>read(body, "$.cover.entryId")).isEqualTo(photo.entryId());
        assertThat(JsonPath.<String>read(body, "$.cover.mediaType")).isEqualTo("image/jpeg");
        assertThat(JsonPath.<String>read(body, "$.cover.fileUrl"))
                .startsWith("http")
                .contains("X-Amz-Signature");
        assertThat(body).doesNotContain("objectKey", "coverMediaId");
        String eraId = JsonPath.read(body, "$.id");
        assertThat(storedCover(eraId)).isEqualTo(photo.mediaId());

        // The same cover in the detail and in the list.
        assertThat(coverIdInDetail(alice, eraId)).isEqualTo(photo.mediaId());
        assertThat(JsonPath.<String>read(erasBody(alice), "$[0].cover.id")).isEqualTo(photo.mediaId());
    }

    @Test
    void anEraWithoutACoverShowsNullAndTwoErasMayShareOnePhoto() {
        User alice = aUser();
        Photo photo = aPhotoOf(alice, "Beach");
        String plain = createEra(alice, "Plain", "2026-01-01", "2026-01-31", false);
        String first = eraWithCover(alice, "First", photo.mediaId());
        String second = eraWithCover(alice, "Second", photo.mediaId());

        String list = erasBody(alice);

        assertThat(JsonPath.<List<Object>>read(list, "$[?(@.id == '" + plain + "')].cover"))
                .containsExactly((Object) null);
        assertThat(JsonPath.<List<String>>read(list, "$[?(@.id == '" + first + "')].cover.id"))
                .containsExactly(photo.mediaId());
        assertThat(JsonPath.<List<String>>read(list, "$[?(@.id == '" + second + "')].cover.id"))
                .containsExactly(photo.mediaId());
        assertThat(coverInDetail(alice, plain)).isNull();
    }

    @Test
    void aPhotoOfADiaryPageCanBeACover() {
        User alice = aUser();
        String entryId = entryOn(alice, W4, "Page");
        String page = aDiaryPage(entryId, "users/test/covers/page.png", 0).toString();

        postEra(alice, eraBody("Pages", page)).expectStatus().isCreated();
    }

    @Test
    void aPhotoFromAnOpenedLootDropCanBeACover() {
        User alice = aUser();
        Photo photo = aPhotoOf(alice, "Drop");
        sealUntil(alice, photo.entryId(), SEALED_UNTIL);
        clockAt(AFTER_SEALED_UNTIL);

        postEra(alice, eraBody("Opened", photo.mediaId())).expectStatus().isCreated();
    }

    // What a request may not choose

    @Test
    void aVoiceNoteIs400WithTheFieldNamedAndNothingIsSaved() {
        User alice = aUser();
        String entryId = entryOn(alice, W4, "Voice");
        String voice = aVoiceNote(entryId, "users/test/covers/voice.webm", 0).toString();

        RestTestClient.ResponseSpec response = postEra(alice, eraBody("Sound", voice));

        response.expectStatus().isBadRequest();
        String body = bodyText(response);
        assertThat(problemType(body)).endsWith("validation-failed");
        assertThat(fieldErrors(body)).containsOnlyKeys("coverMediaId");
        assertThat(body).doesNotContain(voice);
        assertThat(eraCount(alice)).isZero();
    }

    @Test
    void aPhotoFromASealedLootDropIs400AndAVoiceNoteInOneGetsTheSameWords() {
        User alice = aUser();
        Photo photo = aPhotoOf(alice, "Secret");
        String voiceEntry = entryOn(alice, W4, "Secret voice");
        String voice =
                aVoiceNote(voiceEntry, "users/test/covers/secret.webm", 0).toString();
        sealUntil(alice, photo.entryId(), SEALED_UNTIL);
        sealUntil(alice, voiceEntry, SEALED_UNTIL);

        RestTestClient.ResponseSpec photoResponse = postEra(alice, eraBody("Peek", photo.mediaId()));
        RestTestClient.ResponseSpec voiceResponse = postEra(alice, eraBody("Peek", voice));

        photoResponse.expectStatus().isBadRequest();
        voiceResponse.expectStatus().isBadRequest();
        // The type of a file inside a sealed entry is not given away.
        assertThat(fieldErrors(bodyText(photoResponse))).containsOnlyKeys("coverMediaId");
        assertThat(fieldErrors(bodyText(voiceResponse))).isEqualTo(fieldErrors(bodyText(photoResponse)));
        assertThat(eraCount(alice)).isZero();
    }

    @Test
    void anotherUsersPhotoAMissingOneAndOneInRecentlyDeletedAreTheSame404() {
        User alice = aUser();
        User bob = aUser();
        Photo bobs = aPhotoOf(bob, "Bob");
        Photo gone = aPhotoOf(alice, "Deleted");
        deleteOk(alice, gone.entryId());
        String missing = UUID.randomUUID().toString();

        RestTestClient.ResponseSpec foreign = postEra(alice, eraBody("A", bobs.mediaId()));
        RestTestClient.ResponseSpec absent = postEra(alice, eraBody("B", missing));
        RestTestClient.ResponseSpec deleted = postEra(alice, eraBody("C", gone.mediaId()));

        foreign.expectStatus().isNotFound();
        absent.expectStatus().isNotFound();
        deleted.expectStatus().isNotFound();
        String detail = JsonPath.read(bodyText(foreign), "$.detail");
        assertThat(JsonPath.<String>read(bodyText(absent), "$.detail")).isEqualTo(detail);
        assertThat(JsonPath.<String>read(bodyText(deleted), "$.detail")).isEqualTo(detail);
        assertThat(detail).doesNotContain(bobs.mediaId(), missing, gone.mediaId());
        assertThat(eraCount(alice)).isZero();
    }

    @Test
    void aCoverThatIsNotAUuidIsAMalformedRequest() {
        User alice = aUser();

        postEra(alice, eraBody("Bad", "not-a-uuid")).expectStatus().isBadRequest();

        assertThat(eraCount(alice)).isZero();
    }

    // Hidden, not lost

    @Test
    void theCoverIsHiddenWhileItsEntryIsSealedAndBackWhenItOpens() {
        User alice = aUser();
        Photo photo = aPhotoOf(alice, "Beach");
        String eraId = eraWithCover(alice, "Summer", photo.mediaId());
        assertThat(coverIdInDetail(alice, eraId)).isEqualTo(photo.mediaId());

        sealUntil(alice, photo.entryId(), SEALED_UNTIL);

        String detail = bodyText(getEra(alice, eraId));
        String list = erasBody(alice);
        assertThat(JsonPath.<Object>read(detail, "$.cover")).isNull();
        assertThat(firstCoverInList(alice)).isNull();
        assertThat(detail + list).doesNotContain("fileUrl", photo.mediaId());
        assertThat(storedCover(eraId)).as("hidden, not cleared").isEqualTo(photo.mediaId());

        clockAt(AFTER_SEALED_UNTIL);

        assertThat(coverIdInDetail(alice, eraId)).isEqualTo(photo.mediaId());
    }

    @Test
    void theCoverIsHiddenWhileItsEntryIsInRecentlyDeletedAndBackOnRestore() {
        User alice = aUser();
        Photo photo = aPhotoOf(alice, "Beach");
        String eraId = eraWithCover(alice, "Summer", photo.mediaId());

        deleteOk(alice, photo.entryId());

        assertThat(coverInDetail(alice, eraId)).isNull();
        assertThat(firstCoverInList(alice)).isNull();
        assertThat(storedCover(eraId)).as("hidden, not cleared").isEqualTo(photo.mediaId());

        restore(alice, photo.entryId()).expectStatus().isOk();

        assertThat(coverIdInDetail(alice, eraId)).isEqualTo(photo.mediaId());
    }

    @Test
    void aSealedCoverStaysHiddenWhenTheLootDropIsDeletedAndRestoredWhileStillSealed() {
        User alice = aUser();
        Photo photo = aPhotoOf(alice, "Beach");
        String eraId = eraWithCover(alice, "Summer", photo.mediaId());
        sealUntil(alice, photo.entryId(), SEALED_UNTIL);
        deleteOk(alice, photo.entryId());
        restore(alice, photo.entryId()).expectStatus().isOk();

        assertThat(coverInDetail(alice, eraId)).isNull();
    }

    // Removing the photo

    @Test
    void deletingThePhotoClearsTheCoverAndLeavesTheEra() {
        User alice = aUser();
        Photo photo = aPhotoOf(alice, "Beach");
        String eraId = eraWithCover(alice, "Summer", photo.mediaId());

        deleteMedia(alice, photo.mediaId()).expectStatus().isNoContent();

        assertThat(storedCover(eraId)).isNull();
        assertThat(eraCount(alice)).isEqualTo(1);
        assertThat(coverInDetail(alice, eraId)).isNull();
    }

    @Test
    void erasingTheEntryClearsTheCoverToo() {
        User alice = aUser();
        Photo photo = aPhotoOf(alice, "Beach");
        String eraId = eraWithCover(alice, "Summer", photo.mediaId());
        deleteOk(alice, photo.entryId());

        erase(alice, photo.entryId()).expectStatus().isNoContent();

        assertThat(storedCover(eraId)).isNull();
        assertThat(eraCount(alice)).isEqualTo(1);
    }

    // PUT, the full replace

    @Test
    void putChangesTheCoverClearsItWhenLeftOutAndRefusesABadOne() {
        User alice = aUser();
        User bob = aUser();
        Photo first = aPhotoOf(alice, "First");
        Photo second = aPhotoOf(alice, "Second");
        Photo bobs = aPhotoOf(bob, "Bob");
        String eraId = eraWithCover(alice, "Summer", first.mediaId());

        putEra(alice, eraId, eraBody("Summer", second.mediaId())).expectStatus().isOk();
        assertThat(storedCover(eraId)).isEqualTo(second.mediaId());

        putEra(alice, eraId, eraBody("Summer", bobs.mediaId())).expectStatus().isNotFound();
        assertThat(storedCover(eraId)).as("a refused PUT changes nothing").isEqualTo(second.mediaId());

        RestTestClient.ResponseSpec left = putEra(alice, eraId, json("title", "Renamed", "startDate", "2026-10-01"));
        left.expectStatus().isOk();
        assertThat(JsonPath.<Object>read(bodyText(left), "$.cover")).isNull();
        assertThat(storedCover(eraId)).isNull();

        putEra(alice, eraId, eraBody("Summer", first.mediaId())).expectStatus().isOk();
        putEra(alice, eraId, eraBody("Summer", null)).expectStatus().isOk();
        assertThat(storedCover(eraId)).isNull();
    }

    @Test
    void anEraWhoseCoverIsHiddenCanStillBeEditedWhenTheRequestNamesTheSamePhoto() {
        User alice = aUser();
        Photo photo = aPhotoOf(alice, "Beach");
        String eraId = eraWithCover(alice, "Summer", photo.mediaId());
        sealUntil(alice, photo.entryId(), SEALED_UNTIL);

        // The same photo again is not looked up, so the sealed entry cannot make the edit fail.
        RestTestClient.ResponseSpec same = putEra(alice, eraId, eraBody("Summer break", photo.mediaId()));

        same.expectStatus().isOk();
        assertThat(JsonPath.<Object>read(bodyText(same), "$.cover")).isNull();
        assertThat(storedCover(eraId)).isEqualTo(photo.mediaId());

        // A different sealed photo is a change, and is refused.
        Photo other = aPhotoOf(alice, "Other");
        sealUntil(alice, other.entryId(), SEALED_UNTIL);
        putEra(alice, eraId, eraBody("Summer break", other.mediaId()))
                .expectStatus()
                .isBadRequest();
        assertThat(storedCover(eraId)).isEqualTo(photo.mediaId());
    }

    @Test
    void replayingACreateReturnsTheStoredEraWithItsCoverAndAppliesNoNewCover() {
        User alice = aUser();
        Photo first = aPhotoOf(alice, "First");
        Photo second = aPhotoOf(alice, "Second");
        UUID id = UUID.randomUUID();
        postEra(
                        alice,
                        json(
                                "id",
                                id.toString(),
                                "title",
                                "Once",
                                "startDate",
                                "2026-10-01",
                                "coverMediaId",
                                first.mediaId()))
                .expectStatus()
                .isCreated();

        RestTestClient.ResponseSpec replay = postEra(
                alice,
                json(
                        "id",
                        id.toString(),
                        "title",
                        "Twice",
                        "startDate",
                        "2026-10-01",
                        "coverMediaId",
                        second.mediaId()));

        replay.expectStatus().isOk();
        assertThat(JsonPath.<String>read(bodyText(replay), "$.cover.id")).isEqualTo(first.mediaId());
        assertThat(storedCover(id.toString())).isEqualTo(first.mediaId());
    }

    // Other users

    @Test
    void anotherUsersListNeverShowsYourCover() {
        User alice = aUser();
        User bob = aUser();
        Photo photo = aPhotoOf(alice, "Beach");
        eraWithCover(alice, "Summer", photo.mediaId());

        assertThat(JsonPath.<List<Object>>read(erasBody(bob), "$")).isEmpty();
    }
}

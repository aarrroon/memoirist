package com.memoirist.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code PUT /entries/{entryId}/media/order}: the carousel order changes, the deferrable unique constraint lets a swap
 * through, and every way to name the wrong set of ids is {@code 400} and changes nothing.
 */
class MediaReorderTest extends MediaApiTest {

    private static final String SOME_DAY_AHEAD = "2026-11-30T00:00:00Z";

    private static Map<String, Object> order(List<String> ids) {
        return json("mediaIds", ids);
    }

    private List<String> idsOf(String responseBody) {
        return JsonPath.read(responseBody, "$[*].id");
    }

    @Test
    void twoItemsCanSwapPlaces() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 2); // a, b

        // A swap is the case a non-deferrable unique constraint can't do in one step: a moves to 1 while b still holds
        // 1.
        String body = bodyText(reorder(user, entryId, order(List.of(ids.get(1), ids.get(0))))
                .expectStatus()
                .isOk());

        assertThat(idsOf(body)).containsExactly(ids.get(1), ids.get(0));
        assertThat(JsonPath.<List<Integer>>read(body, "$[*].displayOrder")).containsExactly(0, 1);
        assertThat(idsInOrder(entryId)).containsExactly(ids.get(1), ids.get(0));
        assertThat(positions(entryId)).containsExactly(0, 1);
    }

    @Test
    void anyOrderOfSeveralItemsCanBeSet() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 5);

        List<String> reversed = new ArrayList<>(ids);
        Collections.reverse(reversed);
        reorder(user, entryId, order(reversed)).expectStatus().isOk();
        assertThat(idsInOrder(entryId)).isEqualTo(reversed);

        List<String> rotated = new ArrayList<>(ids.subList(2, 5));
        rotated.addAll(ids.subList(0, 2));
        reorder(user, entryId, order(rotated)).expectStatus().isOk();
        assertThat(idsInOrder(entryId)).isEqualTo(rotated);
        assertThat(positions(entryId)).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void theNewOrderIsWhatTheEntryShowsAndTheResponseCarriesFreshUrls() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 3);

        String body = bodyText(reorder(user, entryId, order(List.of(ids.get(2), ids.get(0), ids.get(1))))
                .expectStatus()
                .isOk());

        assertThat(JsonPath.<List<String>>read(body, "$[*].fileUrl")).allMatch(url -> url.contains("X-Amz-Signature="));
        assertThat(((List<?>) getEntry(user, entryId).get("media")))
                .extracting(item -> ((Map<?, ?>) item).get("id"))
                .isEqualTo(List.of(ids.get(2), ids.get(0), ids.get(1)));
    }

    @Test
    void theSameOrderIsAFineRequest() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 3);

        reorder(user, entryId, order(ids)).expectStatus().isOk();

        assertThat(idsInOrder(entryId)).isEqualTo(ids);
    }

    @Test
    void anEntryWithNoMediaTakesAnEmptyList() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        assertThat(bodyText(
                        reorder(user, entryId, order(List.of())).expectStatus().isOk()))
                .isEqualTo("[]");
    }

    @Test
    void reorderingDoesNotChangeTheEntrysVersion() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 2);
        int versionBefore = versionOf(entryId);

        reorder(user, entryId, order(List.of(ids.get(1), ids.get(0))))
                .expectStatus()
                .isOk();

        assertThat(versionOf(entryId)).isEqualTo(versionBefore);
    }

    // The ids must be exactly the entry's media

    @Test
    void anIdLeftOutIs400() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 3);

        String body = bodyText(reorder(user, entryId, order(List.of(ids.get(1), ids.get(0))))
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsEntry("mediaIds", "must list every photo and voice note of the entry");
        assertThat(idsInOrder(entryId)).isEqualTo(ids);
    }

    @Test
    void anIdThatIsNotTheEntrysIs400AndIsNotRepeated() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 2);
        String stranger = UUID.randomUUID().toString();

        String body = bodyText(reorder(user, entryId, order(List.of(ids.get(0), stranger)))
                .expectStatus()
                .isBadRequest());

        assertThat(fieldErrors(body)).containsEntry("mediaIds", "must only list media that belong to this entry");
        assertThat(JsonPath.<String>read(body, "$.detail")).doesNotContain(stranger);
        assertThat(idsInOrder(entryId)).isEqualTo(ids);
    }

    @Test
    void anExtraIdOnTopOfTheWholeSetIs400() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 2);
        List<String> tooMany = new ArrayList<>(ids);
        tooMany.add(UUID.randomUUID().toString());

        reorder(user, entryId, order(tooMany)).expectStatus().isBadRequest();

        assertThat(idsInOrder(entryId)).isEqualTo(ids);
    }

    @Test
    void anIdListedTwiceIs400() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 2);

        String body = bodyText(reorder(user, entryId, order(List.of(ids.get(0), ids.get(0))))
                .expectStatus()
                .isBadRequest());

        assertThat(fieldErrors(body)).containsEntry("mediaIds", "must not list the same id twice");
        assertThat(idsInOrder(entryId)).isEqualTo(ids);
    }

    @Test
    void theMediaOfAnotherEntryOfTheSameUserIs400() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        String otherEntry = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 2);
        String elsewhere = uploadPhoto(user, otherEntry).get("id").toString();

        reorder(user, entryId, order(List.of(ids.get(0), elsewhere)))
                .expectStatus()
                .isBadRequest();

        assertThat(idsInOrder(entryId)).isEqualTo(ids);
        assertThat(idsInOrder(otherEntry)).containsExactly(elsewhere);
    }

    @Test
    void anotherUsersMediaIdIs400NotAnAnswerThatConfirmsItExists() {
        User alice = aUser();
        User bob = aUser();
        String bobEntry = createTitledEntry(bob);
        String aliceEntry = createTitledEntry(alice);
        List<String> aliceIds = uploadPhotos(alice, aliceEntry, 1);
        List<String> bobIds = uploadPhotos(bob, bobEntry, 1);

        String body = bodyText(reorder(bob, bobEntry, order(List.of(bobIds.get(0), aliceIds.get(0))))
                .expectStatus()
                .isBadRequest());

        // The same sentence as for an id that exists nowhere.
        assertThat(fieldErrors(body)).containsEntry("mediaIds", "must only list media that belong to this entry");
        assertThat(idsInOrder(aliceEntry)).isEqualTo(aliceIds);
    }

    @Test
    void aMissingNullOrOversizedListIs400() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhotos(user, entryId, 1);

        assertThat(fieldErrors(
                        bodyText(reorder(user, entryId, json()).expectStatus().isBadRequest())))
                .containsEntry("mediaIds", "must not be null");

        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        assertThat(fieldErrors(bodyText(
                        reorder(user, entryId, order(withNull)).expectStatus().isBadRequest())))
                .containsEntry("mediaIds[0]", "must not be null");

        List<String> twentyOne = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            twentyOne.add(UUID.randomUUID().toString());
        }
        assertThat(fieldErrors(bodyText(
                        reorder(user, entryId, order(twentyOne)).expectStatus().isBadRequest())))
                .containsKey("mediaIds");
    }

    @Test
    void aListOfThingsThatAreNotUuidsIs400MalformedRequest() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        String body = bodyText(reorder(user, entryId, "{\"mediaIds\": [\"one\", \"two\"]}")
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
    }

    @Test
    void aBodyThatIsNotJsonIs415() {
        User user = aUser();
        String entryId = createTitledEntry(user);

        client.put()
                .uri(ENTRIES + "/" + entryId + "/media/order")
                .header(HEADER, user.getId().toString())
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .body("a,b")
                .exchange()
                .expectStatus()
                .isEqualTo(415);
    }

    // Ownership, Recently deleted and the seal

    @Test
    void anotherUsersEntryIs404() {
        User alice = aUser();
        User bob = aUser();
        String aliceEntry = createTitledEntry(alice);
        List<String> ids = uploadPhotos(alice, aliceEntry, 2);

        String body = bodyText(reorder(bob, aliceEntry, order(List.of(ids.get(1), ids.get(0))))
                .expectStatus()
                .isNotFound());

        assertThat(problemType(body)).isEqualTo("not-found");
        assertThat(idsInOrder(aliceEntry)).isEqualTo(ids);
    }

    @Test
    void anEntryThatDoesNotExistIs404() {
        User user = aUser();

        reorder(user, UUID.randomUUID(), order(List.of())).expectStatus().isNotFound();
    }

    @Test
    void anEntryInRecentlyDeletedIs404() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 2);
        delete(user, entryId).expectStatus().isNoContent();

        reorder(user, entryId, order(List.of(ids.get(1), ids.get(0))))
                .expectStatus()
                .isNotFound();

        assertThat(idsInOrder(entryId)).isEqualTo(ids);
    }

    @Test
    void aSealedEntryIs409AndTheOrderIsKept() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 2);
        seal(user, entryId, SOME_DAY_AHEAD).expectStatus().isOk();

        String body = bodyText(reorder(user, entryId, order(List.of(ids.get(1), ids.get(0))))
                .expectStatus()
                .isEqualTo(409));

        assertThat(problemType(body)).isEqualTo("entry-sealed");
        assertThat(body).doesNotContain(ids.get(0)).doesNotContain(ids.get(1));
        assertThat(idsInOrder(entryId)).isEqualTo(ids);
    }

    @Test
    void aSealedEntryIs409EvenForAnInvalidList() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhotos(user, entryId, 2);
        seal(user, entryId, SOME_DAY_AHEAD).expectStatus().isOk();

        // Not an answer that tells what the entry holds: a list that is wrong for the media is still just "sealed".
        reorder(user, entryId, order(List.of(UUID.randomUUID().toString())))
                .expectStatus()
                .isEqualTo(409);
    }

    @Test
    void aLootDropThatHasOpenedCanBeReordered() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 2);
        seal(user, entryId, SOME_DAY_AHEAD).expectStatus().isOk();
        clockAt(SOME_DAY_AHEAD);

        reorder(user, entryId, order(List.of(ids.get(1), ids.get(0))))
                .expectStatus()
                .isOk();

        assertThat(idsInOrder(entryId)).containsExactly(ids.get(1), ids.get(0));
    }
}

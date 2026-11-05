package com.memoirist.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.common.StorageUnavailableException;
import com.memoirist.user.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code DELETE /media/{mediaId}}: the row goes, the stored file goes after the commit, the items behind it move up
 * to close the gap, and a sealed, someone else's or Recently deleted entry's media is left alone.
 */
class MediaDeleteTest extends MediaApiTest {

    private static final String SOME_DAY_AHEAD = "2026-11-30T00:00:00Z";

    @Test
    void deletingTheMiddleItemRemovesItsRowAndItsFileAndClosesTheGap() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 3); // a, b, c
        String keyOfB = objectKeyOf(ids.get(1));
        assertThat(stored(keyOfB)).isTrue();

        deleteMedia(user, ids.get(1)).expectStatus().isNoContent();

        assertThat(idsInOrder(entryId)).containsExactly(ids.get(0), ids.get(2));
        assertThat(positions(entryId)).as("c moved up into b's place").containsExactly(0, 1);
        assertThat(stored(keyOfB)).as("the file").isFalse();
        assertThat(stored(objectKeyOf(ids.get(0)))).isTrue();
        assertThat(stored(objectKeyOf(ids.get(2)))).isTrue();
        assertThat(((List<?>) getEntry(user, entryId).get("media"))).hasSize(2);
    }

    @Test
    void deletingTheFirstItemMovesEveryOtherItemUp() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 4);

        deleteMedia(user, ids.get(0)).expectStatus().isNoContent();

        assertThat(idsInOrder(entryId)).containsExactly(ids.get(1), ids.get(2), ids.get(3));
        assertThat(positions(entryId)).containsExactly(0, 1, 2);
    }

    @Test
    void deletingTheLastItemChangesNoOtherPosition() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 3);

        deleteMedia(user, ids.get(2)).expectStatus().isNoContent();

        assertThat(idsInOrder(entryId)).containsExactly(ids.get(0), ids.get(1));
        assertThat(positions(entryId)).containsExactly(0, 1);
    }

    @Test
    void deletingTheOnlyItemLeavesAnEntryWithNoMediaThatTakesANewUpload() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        String only = uploadPhoto(user, entryId).get("id").toString();
        String key = objectKeyOf(only);

        deleteMedia(user, only).expectStatus().isNoContent();

        assertThat(mediaRowCount(entryId)).isZero();
        assertThat(stored(key)).isFalse();
        assertThat(uploadPhoto(user, entryId)).containsEntry("displayOrder", 0);
    }

    @Test
    void aGapIsClosedWhateverTheOrderTheItemsWereReorderedTo() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 4);
        reorder(user, entryId, json("mediaIds", List.of(ids.get(3), ids.get(1), ids.get(0), ids.get(2))))
                .expectStatus()
                .isOk();

        deleteMedia(user, ids.get(1)).expectStatus().isNoContent();

        assertThat(idsInOrder(entryId)).containsExactly(ids.get(3), ids.get(0), ids.get(2));
        assertThat(positions(entryId)).containsExactly(0, 1, 2);
    }

    @Test
    void deletingDoesNotChangeTheEntrysVersion() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 2);
        int versionBefore = versionOf(entryId);

        deleteMedia(user, ids.get(0)).expectStatus().isNoContent();

        assertThat(versionOf(entryId)).isEqualTo(versionBefore);
    }

    @Test
    void aSecondDeleteOfTheSameItemIs404() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        String id = uploadPhoto(user, entryId).get("id").toString();

        deleteMedia(user, id).expectStatus().isNoContent();
        deleteMedia(user, id).expectStatus().isNotFound();
    }

    @Test
    void aFileThatCannotBeRemovedFromStorageDoesNotFailTheDelete() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        String id = uploadPhoto(user, entryId).get("id").toString();
        String key = objectKeyOf(id);
        // The row is the record of what exists. Once it is gone the file is only wasted space, so the user is not told
        // "failed" about a change that was saved (TECH_STACK, Storage, Deletion).
        doThrow(new StorageUnavailableException("Deleting an object failed", new RuntimeException("storage is down")))
                .when(storage)
                .delete(anyString());

        deleteMedia(user, id).expectStatus().isNoContent();

        assertThat(mediaRowCount(entryId)).isZero();
        assertThat(stored(key)).as("the file stays behind").isTrue();
    }

    // Ownership, Recently deleted and the seal

    @Test
    void anotherUsersMediaIs404AndItsFileStays() {
        User alice = aUser();
        User bob = aUser();
        String aliceEntry = createTitledEntry(alice);
        String id = uploadPhoto(alice, aliceEntry).get("id").toString();
        String key = objectKeyOf(id);

        String body = bodyText(deleteMedia(bob, id).expectStatus().isNotFound());

        assertThat(problemType(body)).isEqualTo("not-found");
        assertThat(mediaRowCount(aliceEntry)).isEqualTo(1);
        assertThat(stored(key)).isTrue();
    }

    @Test
    void aMediaItemThatDoesNotExistIs404() {
        User user = aUser();

        deleteMedia(user, UUID.randomUUID()).expectStatus().isNotFound();
    }

    @Test
    void anIdThatIsNotAUuidIs400MalformedRequest() {
        User user = aUser();

        String body = bodyText(deleteMedia(user, "nope").expectStatus().isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
    }

    @Test
    void theMediaOfAnEntryInRecentlyDeletedIs404AndItsFileStays() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        String id = uploadPhoto(user, entryId).get("id").toString();
        String key = objectKeyOf(id);
        delete(user, entryId).expectStatus().isNoContent();

        deleteMedia(user, id).expectStatus().isNotFound();

        assertThat(mediaRowCount(entryId)).isEqualTo(1);
        assertThat(stored(key)).isTrue();
    }

    @Test
    void theMediaOfASealedEntryIs409AndStays() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 2);
        String key = objectKeyOf(ids.get(0));
        seal(user, entryId, SOME_DAY_AHEAD).expectStatus().isOk();

        String body = bodyText(deleteMedia(user, ids.get(0)).expectStatus().isEqualTo(409));

        assertThat(problemType(body)).isEqualTo("entry-sealed");
        assertThat(JsonPath.<String>read(body, "$.detail"))
                .doesNotContain(ids.get(0))
                .doesNotContain("users/");
        assertThat(idsInOrder(entryId)).isEqualTo(ids);
        assertThat(stored(key)).isTrue();
    }

    @Test
    void theMediaOfALootDropThatHasOpenedCanBeDeleted() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        String id = uploadPhoto(user, entryId).get("id").toString();
        seal(user, entryId, SOME_DAY_AHEAD).expectStatus().isOk();
        clockAt(SOME_DAY_AHEAD);

        deleteMedia(user, id).expectStatus().isNoContent();

        assertThat(mediaRowCount(entryId)).isZero();
    }
}

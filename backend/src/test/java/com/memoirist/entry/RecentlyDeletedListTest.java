package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * {@code GET /api/v1/entries/deleted} (task 3.11, D-30, API_CONTRACT Entries): the user's Recently deleted list, the
 * entry deleted most recently first, as the same {@code EntrySummary} rows as every other list.
 */
class RecentlyDeletedListTest extends RecentlyDeletedApiTest {

    private static final String T1 = "2026-10-31T02:00:00Z";
    private static final String T2 = "2026-10-31T03:00:00Z";
    private static final String T3 = "2026-10-31T04:00:00Z";

    // What is in the list

    @Test
    void aUserWithNothingDeletedGetsAnEmptyPage() {
        User alice = aUser();
        entryOn(alice, W4, "Still here");

        String body = deletedBody(alice, "");

        assertThat(JsonPath.<List<?>>read(body, "$.items")).isEmpty();
        assertThat(JsonPath.<Integer>read(body, "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(body, "$.size")).isEqualTo(10);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isZero();
        assertThat(JsonPath.<Integer>read(body, "$.totalPages")).isZero();
    }

    @Test
    void aDeletedEntryIsListedWithWhenItWasDeletedAndALiveOneIsNot() {
        User alice = aUser();
        String live = entryOn(alice, W4, "Still here");
        String gone = entryOn(alice, W3, "Deleted one");
        deleteAt(T1, alice, gone);

        String body = deletedBody(alice, "");

        assertThat(ids(body)).containsExactly(gone);
        Map<String, Object> row = rowIn(body, gone);
        assertThat(row)
                .containsEntry("title", "Deleted one")
                .containsEntry("entryDate", W3)
                .containsEntry("deletedAt", T1)
                .containsEntry("isLocked", false);
        assertThat(ids(body)).doesNotContain(live);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(1);
    }

    @Test
    void theEntryDeletedMostRecentlyComesFirst() {
        User alice = aUser();
        String first = entryOn(alice, W4, "Deleted first");
        String second = entryOn(alice, W3, "Deleted second");
        String third = entryOn(alice, W2, "Deleted third");
        // Deleted in an order that is neither the order they were written nor the order of their memory dates.
        deleteAt(T1, alice, second);
        deleteAt(T2, alice, third);
        deleteAt(T3, alice, first);

        assertThat(deletedIds(alice)).containsExactly(first, third, second);
        assertThat(deletedAtsIn(deletedBody(alice))).containsExactly(T3, T2, T1);
    }

    @Test
    void entriesDeletedInTheSameInstantAlwaysComeInTheSameOrderSoPagingNeverRepeatsOrSkips() {
        User alice = aUser();
        List<String> all = List.of(
                entryOn(alice, W4, "A"),
                entryOn(alice, W4, "B"),
                entryOn(alice, W4, "C"),
                entryOn(alice, W4, "D"),
                entryOn(alice, W4, "E"));
        all.forEach(id -> deleteAt(T1, alice, id));

        List<String> oneBigPage = deletedIds(alice);
        List<String> walked = new java.util.ArrayList<>();
        for (int page = 0; page < 3; page++) {
            walked.addAll(ids(deletedBody(alice, "page=" + page + "&size=2")));
        }

        assertThat(oneBigPage).containsExactlyInAnyOrderElementsOf(all);
        assertThat(walked)
                .as("three pages of two, in the order of one page of five")
                .isEqualTo(oneBigPage);
        assertThat(oneBigPage)
                .as("the id is the last sort key, newest id first")
                .isSortedAccordingTo(java.util.Comparator.<String>naturalOrder().reversed());
    }

    @Test
    void anotherUsersDeletedEntriesAreNeverListedOrCounted() {
        User alice = aUser();
        User bob = aUser();
        String mine = entryOn(alice, W4, "Mine");
        String his = entryOn(bob, W4, "His");
        deleteAt(T1, alice, mine);
        deleteOk(bob, his);

        String body = deletedBody(alice, "");

        assertThat(ids(body)).containsExactly(mine);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(1);
        assertThat(bodyText(deletedList(alice, ""))).doesNotContain("His");
    }

    @Test
    void aHeaderThatNamesAUserWhoIsNotThereGetsAnEmptyPage() {
        String body = bodyText(client.get()
                .uri(DELETED)
                .header(HEADER, UUID.randomUUID().toString())
                .exchange()
                .expectStatus()
                .isOk());

        assertThat(JsonPath.<List<?>>read(body, "$.items")).isEmpty();
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isZero();
    }

    // Empty entries never wait there

    @Test
    void anEntryWithNothingInItIsErasedAtOnceAndNeverListed() {
        User alice = aUser();
        String empty = createEntryId(alice, json());
        deleteOk(alice, empty);

        assertThat(rowExists(empty)).as("erased, not set aside").isFalse();
        assertThat(deletedIds(alice)).isEmpty();
        restore(alice, empty).expectStatus().isNotFound();
    }

    @Test
    void anEntryWithOnlyAPhotoIsNotEmptyAndSoItIsListed() {
        User alice = aUser();
        String photoOnly = createEntryId(alice, json("entryDate", W4));
        aStoredPhoto(photoOnly, 0);
        deleteOk(alice, photoOnly);

        String body = deletedBody(alice);

        assertThat(ids(body)).containsExactly(photoOnly);
        assertThat(rowIn(body, photoOnly)).containsEntry("photoCount", 1).containsEntry("firstMediaKind", "PHOTO");
    }

    // The row

    @Test
    void aRowIsAnEntrySummaryWithItsMediaCountsAndTagsAndNoCaption() {
        User alice = aUser();
        String id = entryWithCaption(alice, W4, heading(1, "A heading"), paragraph("The first line"));
        aStoredPhoto(id, 0);
        aStoredPhoto(id, 1);
        aVoiceNote(id, "users/test/voice.webm", 2);
        UUID work = newTag(alice, "Work");
        UUID family = newTag(alice, "Family");
        tag(id, work);
        tag(id, family);
        deleteAt(T1, alice, id);

        Map<String, Object> row = rowIn(deletedBody(alice), id);

        assertThat(row)
                .containsEntry("previewLine", "The first line")
                .containsEntry("photoCount", 2)
                .containsEntry("voiceNoteCount", 1)
                .containsEntry("firstMediaKind", "PHOTO")
                .containsEntry("isLocked", false)
                .containsEntry("deletedAt", T1)
                .containsEntry("weeklyLogId", weekIdOf(id))
                .doesNotContainKeys("contentJson", "contentText", "media", "fileUrl");
        assertThat(JsonPath.<List<String>>read(row, "$.tags[*].name")).containsExactly("Family", "Work");
    }

    // Loot Drops (D-10)

    @Test
    void aSealedEntryIsALockedRowWithOnlyItsDatesAndNothingPrivate() {
        User alice = aUser();
        String drop = entryOn(alice, W4, "A secret title");
        aStoredPhoto(drop, 0);
        UUID work = newTag(alice, "Secret tag");
        tag(drop, work);
        sealUntil(alice, drop, SEALED_UNTIL);
        deleteAt(T1, alice, drop);

        String text = bodyText(deletedList(alice, ""));
        Map<String, Object> row = rowIn(text, drop);

        assertThat(row)
                .containsEntry("isLocked", true)
                .containsEntry("entryDate", W4)
                .containsEntry("unlockDate", SEALED_UNTIL)
                .containsEntry("deletedAt", T1)
                .containsEntry("title", null)
                .containsEntry("previewLine", null)
                .containsEntry("photoCount", null)
                .containsEntry("voiceNoteCount", null)
                .containsEntry("firstMediaKind", null);
        assertThat(JsonPath.<List<?>>read(row, "$.tags")).isEmpty();
        assertThat(text).doesNotContain("A secret title").doesNotContain("Secret tag");
    }

    @Test
    void aLootDropThatOpensWhileItWaitsThereIsWholeAgain() {
        User alice = aUser();
        String drop = aLootDrop(alice, "Opens soon", OPENS_1);
        deleteAt(T1, alice, drop);
        assertThat(rowIn(deletedBody(alice), drop))
                .containsEntry("isLocked", true)
                .containsEntry("title", null);

        clockAt(OPENS_1);

        assertThat(rowIn(deletedBody(alice), drop))
                .as("the very instant of the unlock date opens it, in Recently deleted too")
                .containsEntry("isLocked", false)
                .containsEntry("title", "Opens soon");
    }

    // Paging

    @Test
    void thePageSizeDefaultsToTenAndPagesWalkTheWholeList() {
        User alice = aUser();
        for (int i = 0; i < 12; i++) {
            deleteAt(T1, alice, entryOn(alice, W4, "Entry " + i));
        }

        String first = deletedBody(alice, "");
        String second = deletedBody(alice, "page=1");

        assertThat(ids(first)).hasSize(10);
        assertThat(ids(second)).hasSize(2);
        assertThat(JsonPath.<Integer>read(first, "$.totalItems")).isEqualTo(12);
        assertThat(JsonPath.<Integer>read(first, "$.totalPages")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(second, "$.page")).isEqualTo(1);
        assertThat(ids(first)).doesNotContainAnyElementsOf(ids(second));
    }

    @Test
    void aPagePastTheEndIsEmptyWithTheRealTotals() {
        User alice = aUser();
        deleteAt(T1, alice, entryOn(alice, W4, "Only one"));

        String body = deletedBody(alice, "page=5&size=10");

        assertThat(JsonPath.<List<?>>read(body, "$.items")).isEmpty();
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(body, "$.totalPages")).isEqualTo(1);
    }

    @Test
    void aSizeOfFiftyIsAcceptedAndOneOverIsNot() {
        User alice = aUser();

        deletedList(alice, "size=50").expectStatus().isOk();
        String body = bodyText(deletedList(alice, "size=51").expectStatus().isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsKey("size");
    }

    @Test
    void badPagingParametersAre400ThatNameTheParameter() {
        User alice = aUser();

        assertThat(fieldErrors(
                        bodyText(deletedList(alice, "size=0").expectStatus().isBadRequest())))
                .containsOnlyKeys("size");
        assertThat(fieldErrors(
                        bodyText(deletedList(alice, "page=-1").expectStatus().isBadRequest())))
                .containsOnlyKeys("page");
        assertThat(problemType(
                        bodyText(deletedList(alice, "size=ten").expectStatus().isBadRequest())))
                .isEqualTo("malformed-request");
    }

    // The path

    @Test
    void deletedIsTheListAndNotAnEntryWithThatId() {
        User alice = aUser();

        // "deleted" must route to the list and not to GET /entries/{entryId} (which would say malformed-request).
        client.get()
                .uri(DELETED)
                .header(HEADER, alice.getId().toString())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk();
    }
}

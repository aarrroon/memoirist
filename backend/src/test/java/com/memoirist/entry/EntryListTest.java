package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.Fixtures;
import com.memoirist.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/v1/entries}, the all-entries timeline (task 3.9, D-24, D-25): which entries are listed and in what
 * order, what a row says, and what a row of a sealed Loot Drop must not say (D-10). The tag filter is in
 * {@link EntryListTagFilterTest}, and the number of SQL statements per page in {@link EntryListQueryCountTest}.
 */
class EntryListTest extends EntryListApiTest {

    // Which entries, and in what order

    @Test
    void entriesAreNewestMemoryDateFirstThenNewestWritten() {
        User alice = aUser();
        String firstOnThatMonday = entryOn(alice, W4, "Monday, written first");
        String secondOnThatMonday = entryOn(alice, W4, "Monday, written second");
        String olderDayWrittenLast = entryOn(alice, W2, "An older day, written last");
        String today = entryOn(alice, "2026-10-31", "Today, written earliest");
        writtenAt(today, "2026-10-26T00:00:00Z");
        writtenAt(firstOnThatMonday, "2026-10-26T01:00:00Z");
        writtenAt(secondOnThatMonday, "2026-10-26T02:00:00Z");
        writtenAt(olderDayWrittenLast, "2026-10-30T01:00:00Z");

        List<String> order = ids(listBody(alice));

        assertThat(order)
                .as("memory date first (today, then Monday, then the older day), then the later written first")
                .containsExactly(today, secondOnThatMonday, firstOnThatMonday, olderDayWrittenLast);
    }

    @Test
    void entriesThatTieOnDateAndWrittenTimeStillComeInOneStableOrderSoNoPageRepeatsOrSkips() {
        User alice = aUser();
        List<String> made = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String id = entryOn(alice, W4, "Photo dump " + i);
            writtenAt(id, "2026-10-27T00:00:00Z");
            made.add(id);
        }

        List<String> whole = ids(listBody(alice, "size=100"));
        List<String> walked = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            walked.addAll(ids(listBody(alice, "page=" + page + "&size=3")));
        }

        assertThat(walked)
                .as("three pages of 3, 3 and 1: every entry once, in the order one big page lists them")
                .containsExactlyElementsOf(whole)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(made);
    }

    @Test
    void thePageSaysHowManyEntriesAndPagesThereAre() {
        User alice = aUser();
        for (int i = 0; i < 5; i++) {
            entryOn(alice, W4, "Entry " + i);
        }

        String second = listBody(alice, "page=1&size=2");
        String last = listBody(alice, "page=2&size=2");
        String pastTheEnd = listBody(alice, "page=3&size=2");

        assertThat(JsonPath.<Integer>read(second, "$.items.length()")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(second, "$.page")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(second, "$.size")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(second, "$.totalItems")).isEqualTo(5);
        assertThat(JsonPath.<Integer>read(second, "$.totalPages")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(last, "$.items.length()")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(pastTheEnd, "$.items.length()")).isZero();
        assertThat(JsonPath.<Integer>read(pastTheEnd, "$.totalItems")).isEqualTo(5);
        assertThat(JsonPath.<Integer>read(pastTheEnd, "$.totalPages")).isEqualTo(3);
    }

    @Test
    void withoutPagingParametersItIsPageZeroOfFifty() {
        User alice = aUser();
        entryOn(alice, W4, "Only entry");

        String body = listBody(alice, "");

        assertThat(JsonPath.<Integer>read(body, "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(body, "$.size")).isEqualTo(50);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(1);
    }

    @Test
    void aJournalWithNothingInItIsAnEmptyPage() {
        User alice = aUser();

        String body = listBody(alice, "");

        assertThat(JsonPath.<Integer>read(body, "$.items.length()")).isZero();
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isZero();
        assertThat(JsonPath.<Integer>read(body, "$.totalPages")).isZero();
    }

    @Test
    void aHeaderThatNamesNobodyGetsAnEmptyListNotAnError() {
        User nobody = Fixtures.user();

        String body = listBody(nobody, "");

        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isZero();
    }

    @Test
    void anotherUsersEntriesNeverAppear() {
        User alice = aUser();
        User bob = aUser();
        String mine = entryOn(alice, W4, "Mine");
        String his = entryOn(bob, W4, "His");

        assertThat(ids(listBody(alice))).containsExactly(mine);
        assertThat(ids(listBody(bob))).containsExactly(his);
        assertThat(JsonPath.<Integer>read(listBody(alice), "$.totalItems")).isEqualTo(1);
    }

    @Test
    void anEntryInRecentlyDeletedIsNotListedOrCounted() {
        User alice = aUser();
        String kept = entryOn(alice, W4, "Kept");
        String deleted = entryOn(alice, W4, "Deleted");
        delete(alice, deleted).expectStatus().isNoContent();

        String body = listBody(alice);

        assertThat(ids(body)).containsExactly(kept);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(1);
    }

    // Bad paging

    @Test
    void aSizeAboveOneHundredOrBelowOneIsAValidationErrorNeverAClamp() {
        User alice = aUser();

        String tooBig = bodyText(list(alice, "size=101").expectStatus().isBadRequest());
        String tooSmall = bodyText(list(alice, "size=0").expectStatus().isBadRequest());

        assertThat(problemType(tooBig)).isEqualTo("validation-failed");
        assertThat(fieldErrors(tooBig)).containsEntry("size", "must be less than or equal to 100");
        assertThat(problemType(tooSmall)).isEqualTo("validation-failed");
        assertThat(fieldErrors(tooSmall)).containsEntry("size", "must be greater than or equal to 1");
        list(alice, "size=100").expectStatus().isOk();
    }

    @Test
    void aNegativePageIsAValidationError() {
        User alice = aUser();

        String body = bodyText(list(alice, "page=-1").expectStatus().isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsEntry("page", "must be greater than or equal to 0");
    }

    @Test
    void aPageOrSizeThatIsNotAWholeNumberIsMalformed() {
        User alice = aUser();

        for (String query : List.of("page=one", "size=ten", "page=1.5", "size=2.5")) {
            String body = bodyText(list(alice, query).expectStatus().isBadRequest());
            assertThat(problemType(body)).as(query).isEqualTo("malformed-request");
        }
    }

    @Test
    void anEmptyPageOrSizeIsTheSameAsLeavingItOutSoItTakesTheDefault() {
        User alice = aUser();
        entryOn(alice, W4, "Only entry");

        String body = listBody(alice, "page=&size=");

        // Spring treats `size=` as not sent. Pinned so that a Spring upgrade that changed it is noticed.
        assertThat(JsonPath.<Integer>read(body, "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(body, "$.size")).isEqualTo(50);
    }

    // What a row says

    @Test
    void anOpenRowHasTheContractFieldsAndNoCaptionAndNoMedia() {
        User alice = aUser();
        UUID trips = newTag(alice, "Trips");
        String id = createEntryId(
                alice,
                json(
                        "entryDate",
                        W3,
                        "title",
                        "Arrived at the Grampians",
                        "contentJson",
                        doc("Just arrived at the campsite."),
                        "dateUnknown",
                        true));
        aPhoto(id, "users/test/rows/1.jpg", 0);
        aPhoto(id, "users/test/rows/2.jpg", 1);
        aVoiceNote(id, "users/test/rows/3.webm", 2);
        tag(id, trips);

        String body = listBody(alice);
        Map<String, Object> row = rowIn(body, id);

        assertThat(row)
                .containsOnlyKeys(
                        "id",
                        "weeklyLogId",
                        "entryDate",
                        "title",
                        "previewLine",
                        "photoCount",
                        "voiceNoteCount",
                        "firstMediaKind",
                        "tags",
                        "createdAt",
                        "unlockDate",
                        "isLocked",
                        "dateUnknown",
                        "deletedAt");
        assertThat(row)
                .containsEntry("id", id)
                .containsEntry("weeklyLogId", weekIdOf(id))
                .containsEntry("entryDate", W3)
                .containsEntry("title", "Arrived at the Grampians")
                .containsEntry("previewLine", "Just arrived at the campsite.")
                .containsEntry("photoCount", 2)
                .containsEntry("voiceNoteCount", 1)
                .containsEntry("firstMediaKind", "PHOTO")
                .containsEntry("unlockDate", null)
                .containsEntry("isLocked", false)
                .containsEntry("dateUnknown", true)
                .containsEntry("deletedAt", null);
        assertThat(row.get("createdAt")).isEqualTo(instantOf(row(id).get("created_at")));
        assertThat(JsonPath.<List<Map<String, Object>>>read(body, "$.items[0].tags"))
                .containsExactly(Map.of("id", trips.toString(), "name", "Trips", "source", "USER"));
        assertThat(body)
                .as("a row carries no caption and no media: those are on GET /entries/{id}")
                .doesNotContain("contentJson")
                .doesNotContain("contentText")
                .doesNotContain("fileUrl")
                .doesNotContain("users/test/rows");
    }

    @Test
    void aRowWithNoTitleNoCaptionAndNoMediaHasNullsAndZeros() {
        User alice = aUser();
        String id = createEntryId(alice, json("entryDate", W4));

        Map<String, Object> row = rowIn(listBody(alice), id);

        assertThat(row)
                .containsEntry("title", null)
                .containsEntry("previewLine", null)
                .containsEntry("photoCount", 0)
                .containsEntry("voiceNoteCount", 0)
                .containsEntry("firstMediaKind", null)
                .containsEntry("tags", List.of());
    }

    @Test
    void anEntrysTagsAreListedByNameWhateverOrderTheyWereAdded() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Tagged");
        tag(id, newTag(alice, "Work"));
        tag(id, newTag(alice, "family"));
        tag(id, newTag(alice, "Going out"));

        List<String> names = JsonPath.read(listBody(alice), "$.items[0].tags[*].name");

        assertThat(names).containsExactly("family", "Going out", "Work");
    }

    // previewLine

    @Test
    void thePreviewLineIsTheFirstLineThatIsNotAHeading() {
        User alice = aUser();
        String id = entryWithCaption(
                alice,
                W4,
                heading(2, "What triggered the spiral?"),
                paragraph("It started on the tram."),
                paragraph("Then it got worse."));

        assertThat(previewLineOf(alice, id)).isEqualTo("It started on the tram.");
    }

    @Test
    void anEmptyParagraphBeforeTheWordsIsSkipped() {
        User alice = aUser();
        String id = entryWithCaption(alice, W4, json("type", "paragraph"), paragraph("Words after a gap."));

        assertThat(previewLineOf(alice, id)).isEqualTo("Words after a gap.");
    }

    @Test
    void aCaptionOfOnlyHeadingsFallsBackToTheFirstHeading() {
        User alice = aUser();
        String id = entryWithCaption(alice, W4, heading(1, "The first heading"), heading(2, "The second heading"));

        assertThat(previewLineOf(alice, id)).isEqualTo("The first heading");
    }

    @Test
    void thePreviewLineIsCutAtOneHundredAndTwentyCharacters() {
        User alice = aUser();
        String exactly120 = "a".repeat(120);
        String longer = "b".repeat(121);
        String idExact = entryWithCaption(alice, W3, paragraph(exactly120));
        String idLonger = entryWithCaption(alice, W4, paragraph(longer));

        assertThat(previewLineOf(alice, idExact)).isEqualTo(exactly120);
        assertThat(previewLineOf(alice, idLonger)).isEqualTo("b".repeat(120));
    }

    @Test
    void aCaptionWithNoWordsHasNoPreviewLine() {
        User alice = aUser();
        String noCaption = createEntryId(alice, json("entryDate", W3));
        String emptyParagraph = entryWithCaption(alice, W4, json("type", "paragraph"));

        assertThat(previewLineOf(alice, noCaption)).isNull();
        assertThat(previewLineOf(alice, emptyParagraph)).isNull();
    }

    @Test
    void aTitleDoesNotReplaceThePreviewLine() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "A title", "contentJson", doc("The words.")));

        Map<String, Object> row = rowIn(listBody(alice), id);

        assertThat(row).containsEntry("title", "A title").containsEntry("previewLine", "The words.");
    }

    @Test
    void theRowFollowsTheCaptionWhenItIsEdited() {
        User alice = aUser();
        String id = entryWithCaption(alice, W4, paragraph("Before"));
        Map<String, Object> saved = getEntry(alice, id);

        put(alice, id, json("version", saved.get("version"), "entryDate", W4, "contentJson", doc("After")))
                .expectStatus()
                .isOk();

        assertThat(previewLineOf(alice, id)).isEqualTo("After");
    }

    // The media a row counts

    @Test
    void firstMediaKindIsThePhotoWhenAPhotoComesFirst() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Photo then voice note");
        aPhoto(id, "users/test/first/photo.jpg", 0);
        aVoiceNote(id, "users/test/first/voice.webm", 1);

        Map<String, Object> row = rowIn(listBody(alice), id);

        assertThat(row)
                .containsEntry("firstMediaKind", "PHOTO")
                .containsEntry("photoCount", 1)
                .containsEntry("voiceNoteCount", 1);
    }

    @Test
    void firstMediaKindIsTheVoiceNoteWhenAVoiceNoteComesFirst() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Voice note then photos");
        aVoiceNote(id, "users/test/first/voice.webm", 0);
        aPhoto(id, "users/test/first/a.jpg", 1);
        aPhoto(id, "users/test/first/b.jpg", 2);

        Map<String, Object> row = rowIn(listBody(alice), id);

        assertThat(row)
                .containsEntry("firstMediaKind", "VOICE_NOTE")
                .containsEntry("photoCount", 2)
                .containsEntry("voiceNoteCount", 1);
    }

    @Test
    void aDiaryPageIsAPageScanAndCountsAsAPhoto() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Diary pages");
        aDiaryPage(id, "users/test/pages/1.png", 0);
        aDiaryPage(id, "users/test/pages/2.png", 1);
        aPhoto(id, "users/test/pages/3.jpg", 2);

        Map<String, Object> row = rowIn(listBody(alice), id);

        assertThat(row)
                .containsEntry("firstMediaKind", "PAGE_SCAN")
                .containsEntry("photoCount", 3)
                .containsEntry("voiceNoteCount", 0);
    }

    @Test
    void firstMeansTheLowestDisplayOrderNotTheOrderTheRowsWereWritten() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Reordered");
        aVoiceNote(id, "users/test/reordered/voice.webm", 1);
        aPhoto(id, "users/test/reordered/photo.jpg", 0);

        Map<String, Object> row = rowIn(listBody(alice), id);

        assertThat(row).containsEntry("firstMediaKind", "PHOTO");
    }

    @Test
    void aRowCountsOnlyItsOwnMedia() {
        User alice = aUser();
        String withPhotos = entryOn(alice, W4, "Three photos");
        String without = entryOn(alice, W3, "None");
        for (int i = 0; i < 3; i++) {
            aPhoto(withPhotos, "users/test/own/" + i + ".jpg", i);
        }

        String body = listBody(alice);

        assertThat(rowIn(body, withPhotos)).containsEntry("photoCount", 3);
        assertThat(rowIn(body, without)).containsEntry("photoCount", 0).containsEntry("firstMediaKind", null);
    }

    // Sealed Loot Drops

    @Test
    void aSealedEntryIsALockedRowWithNothingPrivateInIt() {
        User alice = aUser();
        UUID secret = newTag(alice, "secret-tag");
        String id = createEntryId(
                alice,
                json(
                        "entryDate",
                        W3,
                        "title",
                        "Secret title",
                        "contentJson",
                        doc("Secret words."),
                        "dateUnknown",
                        true));
        aPhoto(id, "users/test/sealed/secret-photo.jpg", 0);
        aVoiceNote(id, "users/test/sealed/secret-voice.webm", 1);
        tag(id, secret);
        sealUntil(alice, id, SEALED_UNTIL);

        String body = listBody(alice);
        Map<String, Object> row = rowIn(body, id);

        assertThat(row)
                .containsEntry("id", id)
                .containsEntry("weeklyLogId", weekIdOf(id))
                .containsEntry("entryDate", W3)
                .containsEntry("unlockDate", SEALED_UNTIL)
                .containsEntry("isLocked", true)
                .containsEntry("dateUnknown", true)
                .containsEntry("deletedAt", null)
                .containsEntry("title", null)
                .containsEntry("previewLine", null)
                .containsEntry("photoCount", null)
                .containsEntry("voiceNoteCount", null)
                .containsEntry("firstMediaKind", null)
                .containsEntry("tags", List.of());
        assertThat(row.get("createdAt")).isNotNull();
        assertThat(body)
                .as("nothing private is anywhere in the answer, not even by its spelling")
                .doesNotContain("Secret")
                .doesNotContain("secret")
                .doesNotContain("users/test/sealed");
    }

    @Test
    void aSealedEntryIsStillListedAndCountedWhenThereIsNoFilter() {
        User alice = aUser();
        String open = entryOn(alice, W4, "Open");
        String sealed = entryOn(alice, W3, "Sealed");
        sealUntil(alice, sealed, SEALED_UNTIL);

        String body = listBody(alice);

        assertThat(ids(body)).containsExactly(open, sealed);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(2);
    }

    @Test
    void aLootDropIsWholeAgainAtTheVeryInstantItOpens() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Opens on Monday");
        aPhoto(id, "users/test/opens/photo.jpg", 0);
        tag(id, newTag(alice, "later"));
        sealUntil(alice, id, "2026-11-02T01:00:00Z");

        clockAt("2026-11-02T00:59:59Z");
        Map<String, Object> before = rowIn(listBody(alice), id);
        clockAt("2026-11-02T01:00:00Z");
        Map<String, Object> at = rowIn(listBody(alice), id);

        assertThat(before).containsEntry("isLocked", true).containsEntry("title", null);
        assertThat(at)
                .containsEntry("isLocked", false)
                .containsEntry("title", "Opens on Monday")
                .containsEntry("photoCount", 1)
                .containsEntry("firstMediaKind", "PHOTO")
                .containsEntry("unlockDate", "2026-11-02T01:00:00Z");
        assertThat(JsonPath.<List<String>>read(listBody(alice), "$.items[0].tags[*].name"))
                .containsExactly("later");
    }

    @Test
    void sealingAnEntryHidesItsRowAtOnce() {
        User alice = aUser();
        String id = entryOn(alice, W4, "About to be sealed");
        aPhoto(id, "users/test/about/photo.jpg", 0);
        assertThat(rowIn(listBody(alice), id)).containsEntry("title", "About to be sealed");

        sealUntil(alice, id, SEALED_UNTIL);

        assertThat(rowIn(listBody(alice), id)).containsEntry("title", null).containsEntry("photoCount", null);
    }

    @Test
    void aSealedEntryInRecentlyDeletedIsNotListed() {
        User alice = aUser();
        String id = entryOn(alice, W4, "Sealed then deleted");
        sealUntil(alice, id, SEALED_UNTIL);
        delete(alice, id).expectStatus().isNoContent();

        assertThat(ids(listBody(alice))).isEmpty();
    }

    @Test
    void anAnswerNeverMentionsAnotherUsersSealedEntry() {
        User alice = aUser();
        User bob = aUser();
        String bobs = entryOn(bob, W4, "Bob's secret");
        sealUntil(bob, bobs, SEALED_UNTIL);

        String body = listBody(alice);

        assertThat(body).doesNotContain(bobs);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isZero();
    }

    // Helpers

    private String previewLineOf(User user, String id) {
        return (String) rowIn(listBody(user), id).get("previewLine");
    }
}

package com.memoirist.week;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/v1/feed} from the outside, through HTTP and the real database: which weeks are on a page, in what
 * order, with which Eras and which entries, and what a sealed Loot Drop shows (API_CONTRACT, Feed; D-08, D-10, D-22,
 * D-30). How many queries a page costs is {@link FeedQueryCountTest}; one week's own calls are
 * {@link WeeklyLogEndpointTest}.
 */
class FeedEndpointTest extends WeekApiTest {

    private static final String SEALED_UNTIL = "2027-01-01T00:00:00Z";

    // The shape of a page and of a week

    @Test
    void aJournalWithNoEntriesIsAnEmptyPage() {
        User alice = aUser();

        String body = bodyText(feed(alice, "").expectStatus().isOk());

        assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet())
                .containsExactlyInAnyOrder("items", "page", "size", "totalItems", "totalPages");
        assertThat(JsonPath.<List<Object>>read(body, "$.items")).isEmpty();
        assertThat(JsonPath.<Integer>read(body, "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(body, "$.size")).isEqualTo(10);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isZero();
        assertThat(JsonPath.<Integer>read(body, "$.totalPages")).isZero();
    }

    @Test
    void eachWeekHasExactlyTheContractFieldsAndTheClientNames() {
        User alice = aUser();
        String entryId = entryOn(alice, W3, "Lab day");

        String body = feedBody(alice);

        Map<String, Object> week = JsonPath.read(body, "$.items[0]");
        assertThat(week.keySet())
                .as("isClosed, not closed (backend.md)")
                .containsExactlyInAnyOrder(
                        "id", "title", "startDate", "endDate", "isClosed", "soundtrack", "eras", "entries");
        assertThat(week.get("id")).isEqualTo(weekIdOf(entryId));
        assertThat(week.get("title"))
                .as("a week is unnamed until the user names it")
                .isNull();
        assertThat(week.get("startDate")).as("a Monday").isEqualTo(W3);
        assertThat(week.get("endDate")).as("the Sunday after it").isEqualTo("2026-10-25");
        assertThat(week.get("isClosed")).isEqualTo(false);
        assertThat(week.get("soundtrack")).isNull();
        assertThat(week.get("eras")).isEqualTo(List.of());
        assertThat(JsonPath.<List<Object>>read(body, "$.items[0].entries")).hasSize(1);
    }

    // Which weeks, and in what order

    @Test
    void weeksComeNewestFirstWhateverOrderTheEntriesWereWritten() {
        User alice = aUser();
        entryOn(alice, W2, "Middle");
        entryOn(alice, W3, "Latest");
        entryOn(alice, W1, "Earliest");

        assertThat(weekStarts(feedBody(alice))).containsExactly(W3, W2, W1);
    }

    @Test
    void aWeekWithoutEntriesIsLeftOutOfTheFeedAndThePageCounts() {
        User alice = aUser();
        entryOn(alice, W1, "Kept");
        // An entry with no title, caption or media is erased at once (D-30), but its week stays behind.
        String empty = createEntryId(alice, json("entryDate", W3));
        weekIdOf(empty);
        delete(alice, empty).expectStatus().isNoContent();
        assertThat(countWeeks(alice)).as("the week row exists").isEqualTo(2);

        String body = feedBody(alice);

        assertThat(weekStarts(body)).containsExactly(W1);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(1);
    }

    @Test
    void aWeekWhoseEntriesAreAllInRecentlyDeletedIsLeftOutAndTheOtherEntriesStay() {
        User alice = aUser();
        String gone = entryOn(alice, W3, "Only entry of its week");
        String kept = entryOn(alice, W2, "Kept entry");
        String alsoGone = entryOn(alice, W2, "Deleted beside it");
        delete(alice, gone).expectStatus().isNoContent();
        delete(alice, alsoGone).expectStatus().isNoContent();
        assertThat(row(gone).get("deleted_at"))
                .as("moved to Recently deleted, not erased")
                .isNotNull();

        String body = feedBody(alice);

        assertThat(weekStarts(body)).as("W3 has only a deleted entry (D-30)").containsExactly(W2);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(body, "$.items[0].entries[*].id"))
                .containsExactly(kept);
        assertThat(body).doesNotContain("Only entry of its week", "Deleted beside it");
    }

    @Test
    void aWeekWithOnlySealedEntriesStaysInTheFeedAsALockedRow() {
        User alice = aUser();
        String drop = entryOn(alice, W3, "Letter to future me");
        sealUntil(alice, drop, SEALED_UNTIL);

        String body = feedBody(alice);

        assertThat(weekStarts(body)).containsExactly(W3);
        assertThat(JsonPath.<List<Boolean>>read(body, "$.items[0].entries[*].isLocked"))
                .containsExactly(true);
    }

    @Test
    void entriesWithinAWeekRunFromTheOldestMemoryDateAndThenTheOldestWritten() {
        User alice = aUser();
        String wednesdayFirst = entryOn(alice, "2026-10-21", "Wednesday, written first");
        String monday = entryOn(alice, "2026-10-19", "Monday, written second");
        String wednesdayLater = entryOn(alice, "2026-10-21", "Wednesday, written third");

        String body = feedBody(alice);

        assertThat(JsonPath.<List<String>>read(body, "$.items[0].entries[*].id"))
                .containsExactly(monday, wednesdayFirst, wednesdayLater);
    }

    @Test
    void aBackdatedEntryAppearsInItsOwnOlderWeekNotTheCurrentOne() {
        User alice = aUser();
        entryOn(alice, W4, "This week");
        String backdated = entryOn(alice, W1, "Remembered later");

        String body = feedBody(alice);

        assertThat(weekStarts(body)).containsExactly(W4, W1);
        assertThat(JsonPath.<List<String>>read(body, "$.items[1].entries[*].id"))
                .containsExactly(backdated);
    }

    // Eras

    @Test
    void aWeekListsEveryEraItsDatesOverlapPinnedFirstThenTheLatestStart() {
        User alice = aUser();
        entryOn(alice, W3, "In three Eras");
        createEra(alice, "Long job", "2025-03-03", null, false);
        createEra(alice, "Trip", "2026-10-20", "2026-10-22", false);
        createEra(alice, "Uni", "2024-02-26", "2026-12-18", true);
        createEra(alice, "Last year", "2025-01-01", "2025-12-31", false);

        String body = feedBody(alice);

        assertThat(JsonPath.<List<String>>read(body, "$.items[0].eras[*].title"))
                .as("pinned Uni first, then the latest start (Trip, Long job); Last year ended long ago")
                .containsExactly("Uni", "Trip", "Long job");
        assertThat(weekStarts(body)).as("a week in several Eras is listed once").containsExactly(W3);
    }

    @Test
    void eachEraInAWeekHasExactlyTheContractFields() {
        User alice = aUser();
        entryOn(alice, W3, "Entry");
        String eraId = createEra(alice, "Trip", "2026-10-20", "2026-10-22", true);

        Map<String, Object> era = JsonPath.read(feedBody(alice), "$.items[0].eras[0]");

        assertThat(era)
                .containsOnlyKeys("id", "title", "startDate", "endDate", "pinnedInFeed")
                .containsEntry("id", eraId)
                .containsEntry("title", "Trip")
                .containsEntry("startDate", "2026-10-20")
                .containsEntry("endDate", "2026-10-22")
                .containsEntry("pinnedInFeed", true);
    }

    @Test
    void anOngoingEraShowsANullEndDateAndHoldsLaterWeeks() {
        User alice = aUser();
        entryOn(alice, W4, "Recent");
        createEra(alice, "Ongoing", "2026-01-05", null, false);

        Map<String, Object> era = JsonPath.read(feedBody(alice), "$.items[0].eras[0]");

        assertThat(era).containsEntry("title", "Ongoing").containsEntry("endDate", null);
    }

    @Test
    void erasTheWeekIsNotInAreNotListedAndAnotherUsersErasNeverAre() {
        User alice = aUser();
        User bob = aUser();
        entryOn(alice, W3, "Alice's week");
        createEra(alice, "Starts the week after", W4, null, false);
        createEra(bob, "Bob's Era over her week", "2026-01-01", null, true);

        assertThat(JsonPath.<List<Object>>read(feedBody(alice), "$.items[0].eras"))
                .isEmpty();
    }

    // Sealed Loot Drops

    @Test
    void aSealedEntryIsALockedRowAndNothingPrivateAboutItIsInTheFeed() {
        User alice = aUser();
        String open = entryOn(alice, W3, "An open entry");
        aPhoto(open, "users/test/entries/open/open.jpg", 0);
        String drop = createEntryId(
                alice,
                json("entryDate", W3, "title", "SECRET-TITLE", "contentJson", doc("SECRET words about the drop")));
        aPhoto(drop, "users/test/entries/drop/SECRET-KEY.jpg", 0);
        sealUntil(alice, drop, SEALED_UNTIL);

        String body = feedBody(alice);

        assertThat(body)
                .as("nothing private appears anywhere in the page, not in a field and not in a URL")
                .doesNotContain("SECRET");
        List<Map<String, Object>> sealed = JsonPath.read(body, "$.items[0].entries[?(@.isLocked == true)]");
        assertThat(sealed).hasSize(1);
        Map<String, Object> locked = sealed.getFirst();
        assertThat(locked.get("id")).isEqualTo(drop);
        assertThat(locked.get("weeklyLogId")).isEqualTo(weekIdOf(drop));
        assertThat(locked.get("entryDate")).isEqualTo(W3);
        assertThat(locked.get("unlockDate")).isEqualTo(SEALED_UNTIL);
        assertThat(locked.get("isLocked")).isEqualTo(true);
        assertThat(locked.get("title")).isNull();
        assertThat(locked.get("contentJson")).isNull();
        assertThat(locked.get("contentText")).isNull();
        assertThat(locked.get("templateId")).isNull();
        assertThat(locked.get("soundtrack")).isNull();
        assertThat(locked.get("replyToEntryId")).isNull();
        assertThat(locked.get("tags")).isEqualTo(List.of());
        assertThat(locked.get("replyIds")).isEqualTo(List.of());
        assertThat(locked.get("media"))
                .as("no photo URL is signed for a sealed entry")
                .isEqualTo(List.of());
    }

    @Test
    void anOpenEntryBesideASealedOneIsCompleteWithItsPhotoUrl() {
        User alice = aUser();
        String open = entryOn(alice, W3, "An open entry");
        UUID photo = aPhoto(open, "users/test/entries/open/open.jpg", 0);
        String drop = entryOn(alice, W3, "Hidden");
        sealUntil(alice, drop, SEALED_UNTIL);

        String body = feedBody(alice);

        List<Map<String, Object>> entries = JsonPath.read(body, "$.items[0].entries");
        Map<String, Object> whole = entries.stream()
                .filter(entry -> open.equals(entry.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(whole.get("isLocked")).isEqualTo(false);
        assertThat(whole.get("title")).isEqualTo("An open entry");
        assertThat(whole.get("contentText")).isEqualTo("About An open entry");
        List<Map<String, Object>> media = JsonPath.read(whole, "$.media");
        assertThat(media).hasSize(1);
        assertThat(media.getFirst().get("id")).isEqualTo(photo.toString());
        assertThat((String) media.getFirst().get("fileUrl"))
                .contains("users/test/entries/open/open.jpg")
                .contains("X-Amz-Signature=");
    }

    @Test
    void anOpenEntryKeepsItsPhotosInCarouselOrderAndShowsItsTagsByNameInTheFeedAndInWeekDetail() {
        User alice = aUser();
        String entry = entryOn(alice, W3, "With photos and tags");
        // Written out of order, so the order in the answer can only come from displayOrder.
        aPhoto(entry, "users/test/entries/order/third.jpg", 2);
        aPhoto(entry, "users/test/entries/order/first.jpg", 0);
        aPhoto(entry, "users/test/entries/order/second.jpg", 1);
        aTag(alice, entry, "walk");
        aTag(alice, entry, "Cafe");
        aTag(alice, entry, "beach");

        for (String body : List.of(feedBody(alice), weekBody(alice, weekIdOf(entry)))) {
            String root = body.contains("\"items\"") ? "$.items[0].entries[0]" : "$.entries[0]";
            List<String> urls = JsonPath.read(body, root + ".media[*].fileUrl");
            assertThat(urls).hasSize(3);
            assertThat(urls.get(0)).contains("first.jpg");
            assertThat(urls.get(1)).contains("second.jpg");
            assertThat(urls.get(2)).contains("third.jpg");
            assertThat(JsonPath.<List<Integer>>read(body, root + ".media[*].displayOrder"))
                    .containsExactly(0, 1, 2);
            assertThat(JsonPath.<List<String>>read(body, root + ".tags[*].name"))
                    .containsExactly("beach", "Cafe", "walk");
        }
    }

    @Test
    void aSealedEntryIsWholeAgainAtTheMomentItUnlocks() {
        User alice = aUser();
        String drop = entryOn(alice, W3, "Opens on Monday");
        aPhoto(drop, "users/test/entries/drop/photo.jpg", 0);
        sealUntil(alice, drop, "2026-11-02T01:00:00Z");

        clockAt("2026-11-02T00:59:59Z");
        assertThat(feedBody(alice)).doesNotContain("Opens on Monday").doesNotContain("photo.jpg");

        clockAt("2026-11-02T01:00:00Z");
        String body = feedBody(alice);
        assertThat(JsonPath.<List<String>>read(body, "$.items[0].entries[*].title"))
                .containsExactly("Opens on Monday");
        assertThat(JsonPath.<List<Boolean>>read(body, "$.items[0].entries[*].isLocked"))
                .containsExactly(false);
        assertThat(body).contains("photo.jpg");
    }

    // Other users

    @Test
    void anotherUsersWeeksAndEntriesNeverAppear() {
        User alice = aUser();
        User bob = aUser();
        entryOn(alice, W3, "Alice only");
        String bobs = entryOn(bob, W2, "Bob only");

        String aliceFeed = feedBody(alice);
        String bobFeed = feedBody(bob);

        assertThat(weekStarts(aliceFeed)).containsExactly(W3);
        assertThat(aliceFeed).doesNotContain("Bob only").doesNotContain(bobs);
        assertThat(weekStarts(bobFeed)).containsExactly(W2);
        assertThat(bobFeed).doesNotContain("Alice only");
    }

    @Test
    void aHeaderThatNamesNobodyGetsAnEmptyFeed() {
        User nobody = new User(UUID.randomUUID(), "nobody@memoirist.test", "Nobody", "Australia/Melbourne");

        String body = bodyText(feed(nobody, "").expectStatus().isOk());

        assertThat(JsonPath.<List<Object>>read(body, "$.items")).isEmpty();
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isZero();
    }

    // Paging

    @Test
    void theFeedIsPagedAndThePageNumbersAreInTheAnswer() {
        User alice = aUser();
        List<String> mondays = List.of(W1, W2, W3, W4, "2026-09-28");
        mondays.forEach(monday -> entryOn(alice, monday, "Week of " + monday));

        String first = bodyText(feed(alice, "page=0&size=2").expectStatus().isOk());
        String second = bodyText(feed(alice, "page=1&size=2").expectStatus().isOk());
        String last = bodyText(feed(alice, "page=2&size=2").expectStatus().isOk());
        String past = bodyText(feed(alice, "page=3&size=2").expectStatus().isOk());

        assertThat(weekStarts(first)).containsExactly(W4, W3);
        assertThat(weekStarts(second)).containsExactly(W2, W1);
        assertThat(weekStarts(last)).containsExactly("2026-09-28");
        assertThat(weekStarts(past))
                .as("a page past the end is empty, not an error")
                .isEmpty();
        for (String body : List.of(first, second, last, past)) {
            assertThat(JsonPath.<Integer>read(body, "$.size")).isEqualTo(2);
            assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(5);
            assertThat(JsonPath.<Integer>read(body, "$.totalPages")).isEqualTo(3);
        }
        assertThat(JsonPath.<Integer>read(second, "$.page")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(past, "$.page")).isEqualTo(3);
    }

    @Test
    void withoutParametersTheFeedIsTheFirstPageOfTen() {
        User alice = aUser();
        for (int week = 0; week < 11; week++) {
            entryOn(alice, java.time.LocalDate.parse(W1).minusWeeks(week).toString(), "Week " + week);
        }

        String body = bodyText(feed(alice, "").expectStatus().isOk());

        assertThat(JsonPath.<List<Object>>read(body, "$.items")).hasSize(10);
        assertThat(JsonPath.<Integer>read(body, "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(body, "$.size")).isEqualTo(10);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(11);
        assertThat(JsonPath.<Integer>read(body, "$.totalPages")).isEqualTo(2);
    }

    @Test
    void aPageSizeOf50IsAcceptedAndOneOverIsNot() {
        User alice = aUser();

        feed(alice, "size=50").expectStatus().isOk();

        String body = bodyText(feed(alice, "size=51").expectStatus().isBadRequest());
        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsOnlyKeys("size").containsValue("must be less than or equal to 50");
    }

    @Test
    void badPagingParametersAreA400NamingTheParameter() {
        User alice = aUser();

        String tooSmall = bodyText(feed(alice, "size=0").expectStatus().isBadRequest());
        String negative = bodyText(feed(alice, "page=-1").expectStatus().isBadRequest());
        String notANumber = bodyText(feed(alice, "page=first").expectStatus().isBadRequest());

        assertThat(problemType(tooSmall)).isEqualTo("validation-failed");
        assertThat(fieldErrors(tooSmall)).containsOnlyKeys("size");
        assertThat(problemType(negative)).isEqualTo("validation-failed");
        assertThat(fieldErrors(negative)).containsOnlyKeys("page");
        assertThat(problemType(notANumber)).isEqualTo("malformed-request");
        assertThat(notANumber).as("the rejected value is never echoed").doesNotContain("first");
    }
}

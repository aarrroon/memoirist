package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/v1/entries?tagId=…}, the timeline filtered by a tag (task 3.9, D-10, D-25, D-26). The rule that matters
 * here is the seal: while a Loot Drop is sealed it is not in the filtered list <em>and not in its count</em>, so
 * nothing shows what it is about, or even that it carries the tag. Everything about the unfiltered list is in
 * {@link EntryListTest}.
 */
class EntryListTagFilterTest extends EntryListApiTest {

    @Test
    void onlyTheEntriesWithTheTagAreListedNewestFirst() {
        User alice = aUser();
        UUID work = newTag(alice, "Work");
        UUID family = newTag(alice, "Family");
        String older = entryOn(alice, W2, "Older work");
        String newer = entryOn(alice, W4, "Newer work");
        String familyOnly = entryOn(alice, W3, "Family dinner");
        String untagged = entryOn(alice, W3, "Nothing");
        tag(older, work);
        tag(newer, work);
        tag(familyOnly, family);

        String body = listBody(alice, "tagId=" + work);

        assertThat(ids(body)).containsExactly(newer, older).doesNotContain(familyOnly, untagged);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(body, "$.totalPages")).isEqualTo(1);
    }

    @Test
    void aRowKeepsAllTheEntrysTagsNotOnlyTheOneFilteredBy() {
        User alice = aUser();
        UUID work = newTag(alice, "Work");
        UUID late = newTag(alice, "Late night");
        String id = entryOn(alice, W4, "Two tags");
        tag(id, work);
        tag(id, late);

        List<String> names = JsonPath.read(listBody(alice, "tagId=" + work), "$.items[0].tags[*].name");

        assertThat(names).containsExactly("Late night", "Work");
    }

    @Test
    void theFilteredListIsPagedAndCountedLikeTheOtherOne() {
        User alice = aUser();
        UUID work = newTag(alice, "Work");
        for (int i = 0; i < 5; i++) {
            tag(entryOn(alice, W4, "Work " + i), work);
        }
        entryOn(alice, W4, "Not work");

        String first = listBody(alice, "tagId=" + work + "&page=0&size=2");
        String last = listBody(alice, "tagId=" + work + "&page=2&size=2");

        assertThat(JsonPath.<Integer>read(first, "$.items.length()")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(first, "$.totalItems")).isEqualTo(5);
        assertThat(JsonPath.<Integer>read(first, "$.totalPages")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(last, "$.items.length()")).isEqualTo(1);
    }

    @Test
    void aSealedEntryIsNeverListedAndNeverCountedWhileFilteringByATag() {
        User alice = aUser();
        UUID secret = newTag(alice, "Secret");
        String open = entryOn(alice, W4, "Open with the tag");
        String sealed = entryOn(alice, W3, "Sealed with the tag");
        tag(open, secret);
        tag(sealed, secret);
        sealUntil(alice, sealed, SEALED_UNTIL);

        String filtered = listBody(alice, "tagId=" + secret);

        assertThat(ids(filtered)).containsExactly(open);
        assertThat(JsonPath.<Integer>read(filtered, "$.totalItems"))
                .as("the total must not move when an entry is sealed")
                .isEqualTo(1);
        assertThat(filtered).doesNotContain(sealed);
        assertThat(ids(listBody(alice)))
                .as("without the filter the same entry is still there, as a locked row")
                .containsExactly(open, sealed);
    }

    @Test
    void aTagUsedOnlyBySealedEntriesGivesAnEmptyPageWithNoHint() {
        User alice = aUser();
        UUID secret = newTag(alice, "Secret");
        String sealed = entryOn(alice, W4, "Sealed");
        tag(sealed, secret);
        sealUntil(alice, sealed, SEALED_UNTIL);

        String filtered = listBody(alice, "tagId=" + secret);
        String unused = listBody(alice, "tagId=" + newTag(alice, "Never used"));

        assertThat(JsonPath.<Integer>read(filtered, "$.items.length()")).isZero();
        assertThat(JsonPath.<Integer>read(filtered, "$.totalItems")).isZero();
        assertThat(JsonPath.<Integer>read(filtered, "$.totalPages")).isZero();
        assertThat(filtered)
                .as("a tag of sealed entries answers exactly like a tag nobody used")
                .isEqualTo(unused);
    }

    @Test
    void anEntryJoinsTheFilteredListAtTheVeryInstantItOpens() {
        User alice = aUser();
        UUID later = newTag(alice, "Later");
        String drop = entryOn(alice, W4, "Opens on Monday");
        tag(drop, later);
        sealUntil(alice, drop, "2026-11-02T01:00:00Z");

        clockAt("2026-11-02T00:59:59Z");
        String before = listBody(alice, "tagId=" + later);
        clockAt("2026-11-02T01:00:00Z");
        String at = listBody(alice, "tagId=" + later);

        assertThat(ids(before)).isEmpty();
        assertThat(JsonPath.<Integer>read(before, "$.totalItems")).isZero();
        assertThat(ids(at)).containsExactly(drop);
        assertThat(JsonPath.<Integer>read(at, "$.totalItems")).isEqualTo(1);
    }

    @Test
    void anEntrySealedAgainLeavesTheFilteredListAtOnce() {
        User alice = aUser();
        UUID work = newTag(alice, "Work");
        String id = entryOn(alice, W4, "Work");
        tag(id, work);
        assertThat(ids(listBody(alice, "tagId=" + work))).containsExactly(id);

        sealUntil(alice, id, SEALED_UNTIL);

        assertThat(ids(listBody(alice, "tagId=" + work))).isEmpty();
    }

    @Test
    void anEntryInRecentlyDeletedIsNotInTheFilteredList() {
        User alice = aUser();
        UUID work = newTag(alice, "Work");
        String kept = entryOn(alice, W4, "Kept");
        String deleted = entryOn(alice, W3, "Deleted");
        tag(kept, work);
        tag(deleted, work);
        delete(alice, deleted).expectStatus().isNoContent();

        String body = listBody(alice, "tagId=" + work);

        assertThat(ids(body)).containsExactly(kept);
        assertThat(JsonPath.<Integer>read(body, "$.totalItems")).isEqualTo(1);
    }

    @Test
    void deletingTheTagEmptiesTheFilterAndTheTagIsThenNotFound() {
        User alice = aUser();
        UUID work = newTag(alice, "Work");
        String id = entryOn(alice, W4, "Work");
        tag(id, work);

        client.delete()
                .uri("/api/v1/tags/" + work)
                .header(HEADER, alice.getId().toString())
                .exchange()
                .expectStatus()
                .isNoContent();

        list(alice, "tagId=" + work).expectStatus().isNotFound();
        assertThat(rowIn(listBody(alice), id).get("tags")).isEqualTo(List.of());
    }

    // Whose tag

    @Test
    void anotherUsersTagIsNotFoundAndSoIsATagThatDoesNotExist() {
        User alice = aUser();
        User bob = aUser();
        UUID bobsTag = newTag(bob, "Bob's tag");
        tag(entryOn(bob, W4, "Bob's entry"), bobsTag);
        entryOn(alice, W4, "Alice's entry");
        UUID nobodys = UUID.randomUUID();

        String foreign = bodyText(list(alice, "tagId=" + bobsTag).expectStatus().isNotFound());
        String missing = bodyText(list(alice, "tagId=" + nobodys).expectStatus().isNotFound());

        assertThat(problemType(foreign)).isEqualTo("not-found");
        assertThat(problemType(missing)).isEqualTo("not-found");
        assertThat(JsonPath.<String>read(foreign, "$.detail"))
                .as("the two look the same, and neither repeats the id")
                .isEqualTo(JsonPath.<String>read(missing, "$.detail"))
                .doesNotContain(bobsTag.toString());
    }

    @Test
    void aTagThatIsNotAUuidIsMalformed() {
        User alice = aUser();

        String body = bodyText(list(alice, "tagId=work").expectStatus().isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
    }

    @Test
    void anEmptyTagIdIsTheSameAsNoTagIdSoItIsTheUnfilteredList() {
        User alice = aUser();
        UUID work = newTag(alice, "Work");
        String tagged = entryOn(alice, W4, "Tagged");
        String untagged = entryOn(alice, W3, "Untagged");
        tag(tagged, work);

        // Spring reads `tagId=` as no value at all. It is the unfiltered list, never a filter that matches nothing,
        // and nothing leaks by it: the unfiltered list shows a sealed Loot Drop only as a locked row. This pins the
        // behaviour so that a Spring upgrade that changed it is noticed.
        assertThat(ids(listBody(alice, "tagId="))).containsExactly(tagged, untagged);
    }

    @Test
    void theFilterNeverShowsAnotherUsersEntriesEvenIfTheyShareATagName() {
        User alice = aUser();
        User bob = aUser();
        UUID alicesWork = newTag(alice, "Work");
        UUID bobsWork = newTag(bob, "Work");
        String mine = entryOn(alice, W4, "Mine");
        String his = entryOn(bob, W4, "His");
        tag(mine, alicesWork);
        tag(his, bobsWork);

        assertThat(ids(listBody(alice, "tagId=" + alicesWork))).containsExactly(mine);
        assertThat(ids(listBody(bob, "tagId=" + bobsWork))).containsExactly(his);
    }
}

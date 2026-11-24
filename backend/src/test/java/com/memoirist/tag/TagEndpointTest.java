package com.memoirist.tag;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.user.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * The four {@code /api/v1/tags} operations in the whole app: real Tomcat, real Postgres, real JSON. It checks each row
 * of API_CONTRACT (Tags) for the happy path, the {@code 400} rules on the name, the unique name ignoring case, another
 * user's tag, and what a rename or a delete does to the entries that carry the tag (D-26).
 *
 * <p>What {@code entryCount} counts is in {@code TagCountTest}, the two-requests-at-once cases are in
 * {@code TagRaceTest}, and {@code tagIds} on entries is in {@code EntryTagsTest}.
 */
class TagEndpointTest extends TagApiTest {

    // POST

    @Test
    void postCreatesATagAndAnswers201WithExactlyTheContractFields() {
        User alice = aUser();

        postTag(alice, "Work")
                .expectStatus()
                .isCreated()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(Map.class)
                .value(body -> {
                    // Exactly these keys: no userId, and no timestamps.
                    assertThat(body.keySet()).containsExactlyInAnyOrder("id", "name", "entryCount");
                    assertThat(UUID.fromString(body.get("id").toString())).isNotNull();
                    assertThat(body).containsEntry("name", "Work").containsEntry("entryCount", 0);
                });

        assertThat(tagNames(alice)).containsExactly("Work");
    }

    @Test
    void theRowIsOwnedByTheUserInTheHeader() {
        User alice = aUser();

        UUID id = aTag(alice, "Work");

        assertThat(tagRow(id)).containsEntry("user_id", alice.getId()).containsEntry("name", "Work");
        assertThat(tagRow(id).get("created_at")).isNotNull();
        assertThat(tagRow(id).get("updated_at")).isNotNull();
    }

    @Test
    void theNameIsStrippedBeforeItIsStoredAndItsCapitalsAreKept() {
        User alice = aUser();

        UUID id = aTag(alice, "  Going Out \n");

        assertThat(tagRow(id)).containsEntry("name", "Going Out");
    }

    @Test
    void aNameOfFiftyCharactersIsFineAndAnEmptyBlankMissingOrLongerOneIs400() {
        User alice = aUser();
        postTag(alice, "x".repeat(50)).expectStatus().isCreated();

        for (String bad : List.of("", "   ", "\t\n", "x".repeat(51), "🙂".repeat(26))) {
            String body = bodyText(postTag(alice, bad).expectStatus().isBadRequest());
            assertThat(problemType(body)).isEqualTo("validation-failed");
            assertThat(fieldErrors(body))
                    .containsOnlyKeys("name")
                    .containsEntry("name", "size must be between 1 and 50");
            assertThat(body).as("nothing the client sent comes back").doesNotContain("xxxxxxxx");
        }
        for (Map<String, Object> bad : List.of(json(), json("name", null), json("title", "Work"))) {
            String body = bodyText(postTag(alice, bad).expectStatus().isBadRequest());
            assertThat(fieldErrors(body)).containsOnlyKeys("name").containsEntry("name", "must not be null");
        }

        assertThat(countTags(alice)).as("only the fifty-character one").isEqualTo(1);
    }

    @Test
    void aNameThatIsAnObjectInsteadOfTextIsMalformedRequest() {
        User alice = aUser();

        String body = bodyText(postTag(alice, json("name", Map.of("first", "Work")))
                .expectStatus()
                .isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
        assertThat(countTags(alice)).isZero();
    }

    @Test
    void aBodyThatIsNotJsonIs415() {
        User alice = aUser();

        client.post()
                .uri(TAGS)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.TEXT_PLAIN)
                .body("Work")
                .exchange()
                .expectStatus()
                .isEqualTo(415);

        assertThat(countTags(alice)).isZero();
    }

    @Test
    void aNameTheUserAlreadyHasIgnoringCaseAnswers200WithTheStoredTagAndWritesNothing() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        Map<String, Object> before = tagRow(work);

        for (String again : List.of("Work", "work", "WORK", "  wORK  ")) {
            Map<String, Object> answer =
                    bodyMap(postTag(alice, again).expectStatus().isOk());

            assertThat(answer.get("id")).as(again).isEqualTo(work.toString());
            assertThat(answer.get("name"))
                    .as("the stored spelling, not " + again)
                    .isEqualTo("Work");
        }

        assertThat(countTags(alice)).isEqualTo(1);
        assertThat(tagRow(work)).as("not even updated_at moved").isEqualTo(before);
    }

    @Test
    void theExistingTagComesBackWithItsEntryCount() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        createEntry(alice, json("title", "a", "tagIds", ids(work)));
        createEntry(alice, json("title", "b", "tagIds", ids(work)));

        Map<String, Object> answer =
                bodyMap(postTag(alice, "work").expectStatus().isOk());

        assertThat(answer).containsEntry("entryCount", 2);
    }

    @Test
    void twoUsersMayHaveTheSameNameAndNeitherSeesTheOther() {
        User alice = aUser();
        User bob = aUser();
        UUID alicesWork = aTag(alice, "Work");

        UUID bobsWork = aTag(bob, "work");

        assertThat(bobsWork).isNotEqualTo(alicesWork);
        assertThat(tagNames(alice)).containsExactly("Work");
        assertThat(tagNames(bob)).containsExactly("work");
    }

    @Test
    void everyFieldButNameIsIgnored() {
        User alice = aUser();
        User bob = aUser();
        String chosenId = UUID.randomUUID().toString();

        Map<String, Object> answer = bodyMap(postTag(
                        alice,
                        json(
                                "name",
                                "Work",
                                "id",
                                chosenId,
                                "userId",
                                bob.getId().toString(),
                                "entryCount",
                                99,
                                "createdAt",
                                "2001-01-01T00:00:00Z"))
                .expectStatus()
                .isCreated());

        assertThat(answer.get("id")).as("the server picks the id").isNotEqualTo(chosenId);
        assertThat(answer).containsEntry("entryCount", 0);
        assertThat(tagRow(answer.get("id"))).containsEntry("user_id", alice.getId());
        assertThat(countTags(bob)).isZero();
    }

    @Test
    void aUserWhoIsNotThereIs404OnPostAndSeesAnEmptyListOnGet() {
        User ghost = aUser();
        jdbc.sql("DELETE FROM users WHERE id = :id").param("id", ghost.getId()).update();

        String body = bodyText(postTag(ghost, "Work").expectStatus().isNotFound());

        assertThat(problemType(body)).isEqualTo("not-found");
        assertThat(tagList(ghost)).isEmpty();
    }

    // GET

    @Test
    void theListIsSortedByNameIgnoringCaseAndAccentsAndDigitsSortAsText() {
        User alice = aUser();
        for (String name : List.of("Zucchini", "banana", "Éclair", "apple", "2 days", "10 days")) {
            aTag(alice, name);
        }

        assertThat(tagNames(alice)).containsExactly("10 days", "2 days", "apple", "banana", "Éclair", "Zucchini");
    }

    @Test
    void theListHoldsOnlyTheUsersOwnTagsAndEachHasItsIdNameAndCount() {
        User alice = aUser();
        User bob = aUser();
        UUID work = aTag(alice, "Work");
        aTag(bob, "Bob's");

        List<Map<String, Object>> tags = tagList(alice);

        assertThat(tags).hasSize(1);
        assertThat(tags.get(0)).containsOnlyKeys("id", "name", "entryCount");
        assertThat(tags.get(0)).containsEntry("id", work.toString()).containsEntry("name", "Work");
        assertThat(tagList(aUser())).as("a new user has none").isEmpty();
    }

    @Test
    void thereIsNoGetForOneTag() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");

        client.get()
                .uri(TAGS + "/" + work)
                .header(HEADER, alice.getId().toString())
                .exchange()
                .expectStatus()
                .isEqualTo(405);
    }

    // PUT

    @Test
    void aRenameChangesTheNameAndAnEntryThatCarriesTheTagShowsTheNewOne() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        String entry = createEntryId(alice, json("title", "Mine", "tagIds", ids(work)));

        Map<String, Object> renamed =
                bodyMap(putTag(alice, work, "Job").expectStatus().isOk());

        assertThat(renamed).containsOnlyKeys("id", "name", "entryCount").containsEntry("name", "Job");
        assertThat(renamed).containsEntry("id", work.toString()).containsEntry("entryCount", 1);
        assertThat(tagNames(alice)).containsExactly("Job");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shown =
                (List<Map<String, Object>>) getEntry(alice, entry).get("tags");
        assertThat(shown).extracting(tag -> tag.get("name")).containsExactly("Job");
    }

    @Test
    void aRenameChangesNoEntryNotEvenItsVersionOrUpdatedAt() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        String entry = createEntryId(alice, json("title", "Mine", "tagIds", ids(work)));
        Map<String, Object> before = row(entry);

        putTag(alice, work, "Job").expectStatus().isOk();

        assertThat(row(entry)).isEqualTo(before);
    }

    @Test
    void aRenameChangesTheTagsUpdatedAt() throws InterruptedException {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        Object before = tagRow(work).get("updated_at");
        Thread.sleep(20); // updated_at is stamped by the JVM clock, so let it tick

        putTag(alice, work, "Job").expectStatus().isOk();

        assertThat(tagRow(work).get("updated_at")).isNotEqualTo(before);
    }

    @Test
    void renamingToAnotherOfTheUsersTagsIgnoringCaseIs409AndNothingChanges() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        UUID family = aTag(alice, "Family");
        Map<String, Object> before = tagRow(family);

        for (String taken : List.of("Work", "work", " WORK ")) {
            String body = bodyText(putTag(alice, family, taken).expectStatus().isEqualTo(409));

            assertThat(problemType(body)).isEqualTo("conflict");
            assertThat(body)
                    .as("nothing the client sent comes back")
                    .doesNotContain("WORK")
                    .doesNotContain("\"Work\"");
        }

        assertThat(tagRow(family)).isEqualTo(before);
        assertThat(tagNames(alice)).containsExactly("Family", "Work");
        assertThat(tagRow(work)).containsEntry("name", "Work");
    }

    @Test
    void aRenameThatOnlyChangesCapitalsIsFineAndAnUnchangedNameWritesNothing() throws InterruptedException {
        User alice = aUser();
        UUID work = aTag(alice, "work");

        putTag(alice, work, "Work").expectStatus().isOk();
        assertThat(tagRow(work)).containsEntry("name", "Work");

        Map<String, Object> before = tagRow(work);
        Thread.sleep(20);
        Map<String, Object> same =
                bodyMap(putTag(alice, work, "Work").expectStatus().isOk());

        assertThat(same).containsEntry("name", "Work");
        assertThat(tagRow(work)).as("updated_at stays").isEqualTo(before);
    }

    @Test
    void aTagMayTakeTheNameAnotherUserHas() {
        User alice = aUser();
        User bob = aUser();
        aTag(bob, "Work");
        UUID family = aTag(alice, "Family");

        putTag(alice, family, "Work").expectStatus().isOk();

        assertThat(tagNames(alice)).containsExactly("Work");
        assertThat(tagNames(bob)).containsExactly("Work");
    }

    @Test
    void aRenameIsHeldToTheSameNameRulesAsACreate() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");

        for (Map<String, Object> bad : List.of(json("name", ""), json("name", "   "), json("name", "x".repeat(51)))) {
            String body = bodyText(putTag(alice, work, bad).expectStatus().isBadRequest());
            assertThat(fieldErrors(body))
                    .containsOnlyKeys("name")
                    .containsEntry("name", "size must be between 1 and 50");
        }
        String missing = bodyText(putTag(alice, work, json()).expectStatus().isBadRequest());
        assertThat(fieldErrors(missing)).containsEntry("name", "must not be null");

        assertThat(tagRow(work)).containsEntry("name", "Work");
    }

    @Test
    void anotherUsersTagAnUnknownTagAndAWordAreNotFoundOrMalformedForPutAndDelete() {
        User alice = aUser();
        User bob = aUser();
        UUID bobs = aTag(bob, "Bob's");
        Map<String, Object> before = tagRow(bobs);

        for (Object id : List.of(bobs, UUID.randomUUID())) {
            assertThat(problemType(
                            bodyText(putTag(alice, id, "Mine").expectStatus().isNotFound())))
                    .isEqualTo("not-found");
            assertThat(problemType(bodyText(deleteTag(alice, id).expectStatus().isNotFound())))
                    .isEqualTo("not-found");
        }
        assertThat(problemType(
                        bodyText(putTag(alice, "work", "Mine").expectStatus().isBadRequest())))
                .isEqualTo("malformed-request");
        assertThat(problemType(bodyText(deleteTag(alice, "work").expectStatus().isBadRequest())))
                .isEqualTo("malformed-request");

        assertThat(tagRow(bobs)).as("Bob's tag was not touched").isEqualTo(before);
    }

    // DELETE

    @Test
    void deleteRemovesTheTagAnswers204AndASecondDeleteIs404() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        aTag(alice, "Family");

        deleteTag(alice, work).expectStatus().isNoContent();

        assertThat(tagNames(alice)).containsExactly("Family");
        deleteTag(alice, work).expectStatus().isNotFound();
    }

    @Test
    void deletingATagUntagsItsEntriesAndChangesNothingElseAboutThem() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        UUID family = aTag(alice, "Family");
        String open =
                createEntryId(alice, json("title", "Open", "contentJson", doc("words"), "tagIds", ids(work, family)));
        String sealed = createEntryId(alice, json("title", "Sealed", "tagIds", ids(work)));
        seal(alice, sealed, "2026-12-25T00:00:00Z").expectStatus().isOk();
        String binned = createEntryId(alice, json("title", "Binned", "tagIds", ids(work)));
        delete(alice, binned).expectStatus().isNoContent();
        Map<String, Object> openBefore = row(open);
        Map<String, Object> sealedBefore = row(sealed);
        Map<String, Object> binnedBefore = row(binned);
        assertThat(countLinks(work)).isEqualTo(3);

        deleteTag(alice, work).expectStatus().isNoContent();

        assertThat(countLinks(work)).as("every link is gone").isZero();
        assertThat(linkedTagIds(open)).as("the other tag stays").containsExactly(family);
        assertThat(linkedTagIds(sealed)).isEmpty();
        assertThat(linkedTagIds(binned)).isEmpty();
        // The entries themselves: same content, same version, same updated_at.
        assertThat(row(open)).isEqualTo(openBefore);
        assertThat(row(sealed)).isEqualTo(sealedBefore);
        assertThat(row(binned)).isEqualTo(binnedBefore);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shown =
                (List<Map<String, Object>>) getEntry(alice, open).get("tags");
        assertThat(shown).extracting(tag -> tag.get("name")).containsExactly("Family");
    }

    @Test
    void deletingATagDoesNotTouchAnotherUsersTagOfTheSameName() {
        User alice = aUser();
        User bob = aUser();
        UUID alicesWork = aTag(alice, "Work");
        UUID bobsWork = aTag(bob, "Work");
        String bobsEntry = createEntryId(bob, json("title", "Mine", "tagIds", ids(bobsWork)));

        deleteTag(alice, alicesWork).expectStatus().isNoContent();

        assertThat(tagNames(bob)).containsExactly("Work");
        assertThat(linkedTagIds(bobsEntry)).containsExactly(bobsWork);
    }

    @Test
    void aDeletedNameCanBeUsedAgainAsANewTag() {
        User alice = aUser();
        UUID first = aTag(alice, "Work");
        deleteTag(alice, first).expectStatus().isNoContent();

        UUID second = aTag(alice, "work");

        assertThat(second).isNotEqualTo(first);
        assertThat(tagNames(alice)).containsExactly("work");
    }
}

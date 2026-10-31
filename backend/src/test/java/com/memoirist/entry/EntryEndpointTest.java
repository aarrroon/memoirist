package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.template.Template;
import com.memoirist.user.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Creating and reading entries through the whole app (API_CONTRACT, Entries; tasks 3.5): the shape of the answer, the
 * optional title, how the week is found or made, the client-supplied id of D-12, and whose entry is whose. Dates are
 * in {@link EntryDatesTest}, captions in {@link EntryContentTest}, editing in {@link EntryUpdateTest}, the seal in
 * {@link EntrySealTest} and {@link EntryRedactionTest}, and delete in {@link EntryDeleteTest}.
 */
class EntryEndpointTest extends EntryApiTest {

    // POST: the answer

    @Test
    void postCreatesAnEntryAndAnswers201WithExactlyTheContractFields() {
        User alice = aUser();

        post(alice, json("title", "Arrived at the Grampians"))
                .expectStatus()
                .isCreated()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.keySet())
                            .containsExactlyInAnyOrder(
                                    "id",
                                    "weeklyLogId",
                                    "entryDate",
                                    "templateId",
                                    "title",
                                    "contentJson",
                                    "contentText",
                                    "tags",
                                    "soundtrack",
                                    "replyToEntryId",
                                    "replyIds",
                                    "createdAt",
                                    "updatedAt",
                                    "unlockDate",
                                    "isLocked",
                                    "dateUnknown",
                                    "version",
                                    "media");
                    assertThat(UUID.fromString(body.get("id").toString())).isNotNull();
                    assertThat(UUID.fromString(body.get("weeklyLogId").toString()))
                            .isNotNull();
                    assertThat(body)
                            .containsEntry("entryDate", "2026-10-31")
                            .containsEntry("title", "Arrived at the Grampians")
                            .containsEntry("templateId", null)
                            .containsEntry("contentJson", null)
                            .containsEntry("contentText", "")
                            .containsEntry("tags", List.of())
                            .containsEntry("soundtrack", null)
                            .containsEntry("replyToEntryId", null)
                            .containsEntry("replyIds", List.of())
                            .containsEntry("unlockDate", null)
                            .containsEntry("isLocked", false)
                            .containsEntry("dateUnknown", false)
                            .containsEntry("version", 0)
                            .containsEntry("media", List.of());
                    // ISO-8601 instants, never numbers: parsing fails on anything else.
                    assertThat(Instant.parse(body.get("createdAt").toString())).isNotNull();
                    assertThat(Instant.parse(body.get("updatedAt").toString())).isNotNull();
                });

        assertThat(countEntries(alice)).isEqualTo(1);
    }

    @Test
    void theRowBelongsToTheUserInTheHeaderAndHoldsTheValuesSent() {
        User alice = aUser();

        String id = createEntryId(alice, json("title", "Hello", "contentJson", doc("First words")));

        Map<String, Object> row = row(id);
        assertThat(row)
                .containsEntry("user_id", alice.getId())
                .containsEntry("title", "Hello")
                .containsEntry("content_text", "First words")
                .containsEntry("preview_line", "First words")
                .containsEntry("soundtrack", null)
                .containsEntry("reply_to_entry_id", null)
                .containsEntry("template_id", null)
                .containsEntry("unlock_date", null)
                .containsEntry("unlock_seen_at", null)
                .containsEntry("deleted_at", null)
                .containsEntry("date_unknown", false)
                .containsEntry("unlock_notified", false)
                .containsEntry("version", 0L);
        assertThat(row.get("entry_date")).hasToString("2026-10-31");
    }

    @Test
    void anEmptyBodyCreatesAnEntryForTodayBecauseMediaComeAfterwards() {
        User alice = aUser();

        post(alice, json())
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.entryDate")
                .isEqualTo("2026-10-31")
                .jsonPath("$.title")
                .isEmpty()
                .jsonPath("$.contentText")
                .isEqualTo("");
    }

    @Test
    void aBodyWithNoJsonAtAllIsMalformedNotAnEmptyEntry() {
        User alice = aUser();

        String body = bodyText(postRaw(alice, "").expectStatus().isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
        assertThat(countEntries(alice)).isZero();
    }

    @Test
    void explicitNullsAreTheSameAsLeavingTheFieldsOut() {
        User alice = aUser();

        post(
                        alice,
                        json(
                                "id", null,
                                "entryDate", null,
                                "templateId", null,
                                "title", null,
                                "contentJson", null,
                                "dateUnknown", null))
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.entryDate")
                .isEqualTo("2026-10-31")
                .jsonPath("$.title")
                .isEmpty()
                .jsonPath("$.contentJson")
                .isEmpty()
                .jsonPath("$.dateUnknown")
                .isEqualTo(false);
        assertThat(row(onlyEntryId(alice))).containsEntry("content_json", null);
    }

    @Test
    void theServerPicksADifferentIdForEachEntryWhenTheClientSendsNone() {
        User alice = aUser();

        String first = createEntryId(alice, json());
        String second = createEntryId(alice, json());

        assertThat(first).isNotEqualTo(second);
    }

    // The title

    @Test
    void anOptionalTitleIsSavedAndStrippedAndShownAgain() {
        User alice = aUser();

        String id = createEntryId(alice, json("title", "  Night out  "));

        assertThat(row(id)).containsEntry("title", "Night out");
        assertThat(getEntry(alice, id)).containsEntry("title", "Night out");
    }

    @Test
    void noTitleIsNull() {
        User alice = aUser();

        String id = createEntryId(alice, json("contentJson", doc("Just words")));

        assertThat(row(id)).containsEntry("title", null);
        assertThat(getEntry(alice, id)).containsEntry("title", null);
    }

    @Test
    void aBlankTitleIsRejectedAndNothingIsCreated() {
        User alice = aUser();

        for (String blank : List.of("", " ", "   ", "\t", "\n \t")) {
            String body =
                    bodyText(post(alice, json("title", blank)).expectStatus().isBadRequest());

            assertThat(problemType(body)).isEqualTo("validation-failed");
            assertThat(fieldErrors(body)).containsOnlyKeys("title");
            assertThat(fieldErrors(body).get("title")).contains("size must be between 1 and 150");
        }
        assertThat(countEntries(alice)).isZero();
        assertThat(countWeeks(alice))
                .as("a rejected request leaves no week behind")
                .isZero();
    }

    @Test
    void aTitleOf150CharactersIsSavedAndOf151IsRejected() {
        User alice = aUser();

        String id = createEntryId(alice, json("title", "t".repeat(150)));
        assertThat(row(id).get("title").toString()).hasSize(150);

        String body = bodyText(
                post(alice, json("title", "t".repeat(151))).expectStatus().isBadRequest());
        assertThat(fieldErrors(body)).containsOnlyKeys("title");
        assertThat(body).as("the rejected title is not echoed back").doesNotContain("ttttt");
    }

    @Test
    void aTitleMayHoldAnyTextIncludingQuotesEmojiAndNonLatinLetters() {
        User alice = aUser();
        String title = "She said \"hi\" <b>& left</b> 🎉 café 日本";

        String id = createEntryId(alice, json("title", title));

        assertThat(getEntry(alice, id)).containsEntry("title", title);
    }

    // The week

    @Test
    void theFirstEntryOfAWeekCreatesTheWeekAndTheSecondReusesIt() {
        User alice = aUser();
        assertThat(countWeeks(alice)).isZero();

        Map<String, Object> first = createEntry(alice, json("entryDate", "2026-10-27"));
        assertThat(countWeeks(alice)).isEqualTo(1);

        Map<String, Object> second = createEntry(alice, json("entryDate", "2026-10-30"));
        assertThat(countWeeks(alice)).as("the same Monday-to-Sunday week").isEqualTo(1);
        assertThat(second.get("weeklyLogId")).isEqualTo(first.get("weeklyLogId"));

        Map<String, Object> week = jdbc.sql("SELECT * FROM weekly_logs WHERE id = :id")
                .param("id", UUID.fromString(first.get("weeklyLogId").toString()))
                .query()
                .singleRow();
        assertThat(week.get("start_date")).hasToString("2026-10-26");
        assertThat(week.get("end_date")).hasToString("2026-11-01");
        assertThat(week)
                .containsEntry("user_id", alice.getId())
                .containsEntry("is_closed", false)
                .containsEntry("title", null);
    }

    @Test
    void anEntryInAnotherWeekMakesAnotherWeek() {
        User alice = aUser();

        Map<String, Object> thisWeek = createEntry(alice, json("entryDate", "2026-10-31"));
        Map<String, Object> lastWeek = createEntry(alice, json("entryDate", "2026-10-25"));

        assertThat(lastWeek.get("weeklyLogId")).isNotEqualTo(thisWeek.get("weeklyLogId"));
        assertThat(countWeeks(alice)).isEqualTo(2);
        assertThat(weekStartOf(thisWeek.get("id"))).isEqualTo("2026-10-26");
        assertThat(weekStartOf(lastWeek.get("id")))
                .as("Sunday 25 October is the end of the week before")
                .isEqualTo("2026-10-19");
    }

    @Test
    void theFirstAndLastDayOfAWeekShareItsWeekAndTheNextMondayStartsANewOne() {
        User alice = aUser();
        clockAt("2026-11-02T01:00:00Z"); // Monday 2 November, noon in Melbourne

        Map<String, Object> monday = createEntry(alice, json("entryDate", "2026-10-26"));
        Map<String, Object> sunday = createEntry(alice, json("entryDate", "2026-11-01"));
        Map<String, Object> nextMonday = createEntry(alice, json("entryDate", "2026-11-02"));

        assertThat(sunday.get("weeklyLogId")).isEqualTo(monday.get("weeklyLogId"));
        assertThat(nextMonday.get("weeklyLogId")).isNotEqualTo(monday.get("weeklyLogId"));
        assertThat(countWeeks(alice)).isEqualTo(2);
    }

    @Test
    void manyEntriesCreatedAtOnceInANewWeekMakeExactlyOneWeek() throws Exception {
        // The photo dump sends several creates at the same moment (D-34). "Select the week, insert it if missing" would
        // let two of them both find nothing and both insert; INSERT ... ON CONFLICT DO NOTHING (D-08) lets the
        // database settle it, so every create succeeds and they all share the one week.
        User alice = aUser();
        int creates = 8;
        ExecutorService pool = Executors.newFixedThreadPool(creates);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Map<String, Object>>> answers = new ArrayList<>();
            for (int i = 0; i < creates; i++) {
                int day = 20 + (i % 7);
                answers.add(pool.submit(() -> {
                    go.await();
                    return bodyMap(post(alice, json("entryDate", "2026-10-" + day))
                            .expectStatus()
                            .isCreated());
                }));
            }
            go.countDown();

            Set<Object> weeks = new HashSet<>();
            for (Future<Map<String, Object>> answer : answers) {
                weeks.add(answer.get(60, TimeUnit.SECONDS).get("weeklyLogId"));
            }

            // 20 to 26 October 2026 runs Tuesday to Monday, so it is two weeks: 19 to 25 and 26 to 1 November.
            assertThat(weeks).hasSize(2);
            assertThat(countWeeks(alice)).isEqualTo(2);
            assertThat(countEntries(alice)).isEqualTo(creates);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoUsersNeverShareAWeekEvenOnTheSameDates() {
        User alice = aUser();
        User bob = aUser();

        Map<String, Object> aliceEntry = createEntry(alice, json("entryDate", "2026-10-27"));
        Map<String, Object> bobEntry = createEntry(bob, json("entryDate", "2026-10-27"));

        assertThat(bobEntry.get("weeklyLogId")).isNotEqualTo(aliceEntry.get("weeklyLogId"));
        assertThat(countWeeks(alice)).isEqualTo(1);
        assertThat(countWeeks(bob)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT user_id FROM weekly_logs WHERE id = :id")
                        .param("id", UUID.fromString(bobEntry.get("weeklyLogId").toString()))
                        .query(UUID.class)
                        .single())
                .isEqualTo(bob.getId());
    }

    // Fields the client must not set

    @Test
    void ownershipTheSealTheVersionAndTheDerivedTextAreNotTheClientsToSet() {
        User alice = aUser();
        User bob = aUser();
        UUID someWeek = UUID.randomUUID();

        String id = createEntryId(
                alice,
                json(
                        "userId",
                        bob.getId().toString(),
                        "weeklyLogId",
                        someWeek.toString(),
                        "unlockDate",
                        "2099-01-01T00:00:00Z",
                        "unlockNotified",
                        true,
                        "unlockSeenAt",
                        "2026-01-01T00:00:00Z",
                        "deletedAt",
                        "2026-01-01T00:00:00Z",
                        "version",
                        7,
                        "createdAt",
                        "2001-01-01T00:00:00Z",
                        "contentText",
                        "text the client wrote itself",
                        "previewLine",
                        "a preview the client wrote",
                        "soundtrack",
                        "spotify:track:1a2B3c4D5e6F7g8H9i0J1k",
                        "replyToEntryId",
                        UUID.randomUUID().toString(),
                        "contentJson",
                        doc("The real words")));

        Map<String, Object> row = row(id);
        assertThat(row)
                .containsEntry("user_id", alice.getId())
                .containsEntry("unlock_date", null)
                .containsEntry("unlock_notified", false)
                .containsEntry("unlock_seen_at", null)
                .containsEntry("deleted_at", null)
                .containsEntry("version", 0L)
                .containsEntry("content_text", "The real words")
                .containsEntry("preview_line", "The real words")
                .containsEntry("soundtrack", null)
                .containsEntry("reply_to_entry_id", null);
        assertThat(row.get("weekly_log_id")).isNotEqualTo(someWeek);
        assertThat(row.get("created_at").toString()).doesNotStartWith("2001");
        assertThat(countEntries(bob)).isZero();
    }

    // The client-supplied id (D-12)

    @Test
    void aClientSuppliedIdIsUsedAndAnswers201() {
        User alice = aUser();
        UUID id = UUID.randomUUID();

        post(alice, json("id", id.toString(), "title", "Offline entry"))
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(id.toString());

        assertThat(row(id)).containsEntry("user_id", alice.getId());
    }

    @Test
    void replayingTheSameIdAnswers200WithTheStoredEntryAndAppliesNothing() {
        User alice = aUser();
        UUID id = UUID.randomUUID();
        Map<String, Object> first = createEntry(alice, json("id", id.toString(), "title", "Original"));

        Map<String, Object> replay =
                bodyMap(post(alice, json("id", id.toString(), "title", "A different title", "entryDate", "2020-01-01"))
                        .expectStatus()
                        .isOk());

        assertThat(replay).containsEntry("title", "Original").containsEntry("id", id.toString());
        assertThat(replay.get("entryDate")).isEqualTo(first.get("entryDate"));
        assertThat(countEntries(alice)).isEqualTo(1);
        assertThat(row(id)).containsEntry("title", "Original");
    }

    @Test
    void replayingAnIdWhoseBodyWouldNowBeInvalidStillAnswers200() {
        // A retry after a timeout must get the entry it made, whatever the retry body says: the replay check is first.
        User alice = aUser();
        UUID id = UUID.randomUUID();
        createEntry(alice, json("id", id.toString(), "title", "Original"));

        post(alice, json("id", id.toString(), "entryDate", "2999-01-01", "contentJson", json("type", "image")))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.title")
                .isEqualTo("Original");
    }

    @Test
    void anIdThatAnotherUserHasIs409ConflictAndRevealsNothing() {
        User alice = aUser();
        User bob = aUser();
        UUID id = UUID.randomUUID();
        createEntry(alice, json("id", id.toString(), "title", "Alice's private thought"));

        String body = bodyText(post(bob, json("id", id.toString(), "title", "Mine now"))
                .expectStatus()
                .isEqualTo(409));

        assertThat(problemType(body)).isEqualTo("conflict");
        assertThat(JsonPath.<String>read(body, "$.detail")).isEqualTo("That id is already in use.");
        assertThat(body)
                .doesNotContain("Alice")
                .doesNotContain("private")
                .doesNotContain(alice.getId().toString());
        assertThat(row(id)).containsEntry("user_id", alice.getId()).containsEntry("title", "Alice's private thought");
        assertThat(countEntries(bob)).isZero();
        assertThat(countWeeks(bob)).as("a refused create leaves no week behind").isZero();
    }

    @Test
    void anIdThatIsInTheUsersOwnRecentlyDeletedIs409ConflictNotARevival() {
        User alice = aUser();
        UUID id = UUID.randomUUID();
        createEntry(alice, json("id", id.toString(), "title", "Deleted soon"));
        delete(alice, id).expectStatus().isNoContent();

        String body = bodyText(post(alice, json("id", id.toString(), "title", "Again"))
                .expectStatus()
                .isEqualTo(409));

        assertThat(problemType(body)).isEqualTo("conflict");
        assertThat(row(id)).containsEntry("title", "Deleted soon");
        assertThat(row(id).get("deleted_at")).isNotNull();
    }

    @Test
    void aMalformedIdIs400AndNothingIsCreated() {
        User alice = aUser();

        String body = bodyText(
                postRaw(alice, "{\"id\":\"not-a-uuid\"}").expectStatus().isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
        assertThat(countEntries(alice)).isZero();
    }

    // The user who isn't there

    @Test
    void aUserWhoIsNotThereIs404AndNothingIsCreated() {
        User nobody = unknownUser();
        UUID id = UUID.randomUUID();

        String body = bodyText(post(nobody, json("id", id.toString(), "title", "Ghost"))
                .expectStatus()
                .isNotFound());

        assertThat(problemType(body)).isEqualTo("not-found");
        assertThat(body).doesNotContain(nobody.getId().toString());
        assertThat(rowExists(id)).isFalse();
    }

    // Templates

    @Test
    void anEntryCanStartFromTheUsersOwnTemplate() {
        User alice = aUser();
        Template template = aTemplate(alice, "Trip day");

        Map<String, Object> entry =
                createEntry(alice, json("templateId", template.getId().toString()));

        assertThat(entry).containsEntry("templateId", template.getId().toString());
        assertThat(row(entry.get("id"))).containsEntry("template_id", template.getId());
    }

    @Test
    void anotherUsersTemplateIs404AndNothingIsCreated() {
        User alice = aUser();
        User bob = aUser();
        Template bobsTemplate = aTemplate(bob, "Bob's ritual");

        String body =
                bodyText(post(alice, json("templateId", bobsTemplate.getId().toString()))
                        .expectStatus()
                        .isNotFound());

        assertThat(problemType(body)).isEqualTo("not-found");
        assertThat(body).doesNotContain("ritual");
        assertThat(countEntries(alice)).isZero();
        assertThat(countWeeks(alice)).isZero();
    }

    @Test
    void aTemplateThatDoesNotExistLooksExactlyLikeSomeoneElses() {
        User alice = aUser();
        User bob = aUser();
        Template bobsTemplate = aTemplate(bob, "Bob's ritual");

        String someonesElse =
                bodyText(post(alice, json("templateId", bobsTemplate.getId().toString()))
                        .expectStatus()
                        .isNotFound());
        String missing =
                bodyText(post(alice, json("templateId", UUID.randomUUID().toString()))
                        .expectStatus()
                        .isNotFound());

        assertThat(JsonPath.<String>read(missing, "$.detail"))
                .isEqualTo(JsonPath.<String>read(someonesElse, "$.detail"));
        assertThat(JsonPath.<String>read(missing, "$.type")).isEqualTo(JsonPath.<String>read(someonesElse, "$.type"));
    }

    // GET

    @Test
    void getReturnsTheSameEntryAsCreateDid() {
        User alice = aUser();
        Map<String, Object> created =
                createEntry(alice, json("title", "Hello", "contentJson", doc("One", "Two"), "dateUnknown", true));

        Map<String, Object> read = getEntry(alice, created.get("id"));

        assertThat(read).isEqualTo(created);
        assertThat(read).containsEntry("contentText", "One\nTwo").containsEntry("dateUnknown", true);
    }

    @Test
    void getAnEntryThatIsAnotherUsersIs404AndSoIsOneThatDoesNotExist() {
        User alice = aUser();
        User bob = aUser();
        String bobsEntry = createTitledEntry(bob);

        String someonesElse = bodyText(get(alice, bobsEntry).expectStatus().isNotFound());
        String missing = bodyText(get(alice, UUID.randomUUID()).expectStatus().isNotFound());

        assertThat(problemType(someonesElse)).isEqualTo("not-found");
        assertThat(JsonPath.<String>read(someonesElse, "$.detail"))
                .isEqualTo(JsonPath.<String>read(missing, "$.detail"));
        assertThat(someonesElse).doesNotContain("worth keeping");
    }

    @Test
    void getWithAnIdThatIsNotAUuidIs400() {
        User alice = aUser();

        String body = bodyText(get(alice, "not-a-uuid").expectStatus().isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
        assertThat(JsonPath.<String>read(body, "$.detail")).doesNotContain("not-a-uuid");
    }

    @Test
    void getFromAUserWhoIsNotThereIs404() {
        User alice = aUser();
        String id = createTitledEntry(alice);

        get(unknownUser(), id).expectStatus().isNotFound();
    }

    // Requests that aren't JSON

    @Test
    void unreadableJsonIs400MalformedRequestNotAValidationFailure() {
        User alice = aUser();

        for (String broken : List.of(
                "{",
                "[]",
                "\"text\"",
                "{\"title\": 5, }",
                "{\"entryDate\": \"2026-13-45\"}",
                "{\"entryDate\": \"yesterday\"}",
                "{\"dateUnknown\": \"maybe\"}")) {
            String body = bodyText(postRaw(alice, broken).expectStatus().isBadRequest());

            assertThat(problemType(body)).as(broken).isEqualTo("malformed-request");
        }
        assertThat(countEntries(alice)).isZero();
    }

    @Test
    void aBodyThatIsNotJsonIs415() {
        User alice = aUser();

        String body = bodyText(client.post()
                .uri(ENTRIES)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.TEXT_PLAIN)
                .body("title=hello")
                .exchange()
                .expectStatus()
                .isEqualTo(415));

        assertThat(problemType(body)).isEqualTo("unsupported-media-type");
    }

    // Helpers

    private User unknownUser() {
        return new User(UUID.randomUUID(), "nobody@memoirist.test", "Nobody", "Australia/Melbourne");
    }

    private String onlyEntryId(User user) {
        return jdbc.sql("SELECT id FROM entries WHERE user_id = :id")
                .param("id", user.getId())
                .query(UUID.class)
                .single()
                .toString();
    }
}

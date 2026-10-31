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
 * Loot Drops through the API (D-10, API_CONTRACT {@code POST /entries/{id}/seal}): sealing, what a sealed entry
 * refuses, moving the unlock date, and the moment it opens. What a sealed entry <em>hides</em> field by field is in
 * {@link EntryRedactionTest}.
 *
 * <p>The clock starts at Saturday 2026-10-31 01:00 UTC (see {@link EntryApiTest}), so "tomorrow at 09:00 in
 * Melbourne" is 2026-10-31T22:00:00Z and "a year on" is 2027-10-31T22:00:00Z.
 */
class EntrySealTest extends EntryApiTest {

    private static final String TOMORROW_9AM = "2026-10-31T22:00:00Z";
    private static final String NEXT_YEAR = "2027-10-31T22:00:00Z";

    private String aTitledEntryWithWords(User user) {
        return createEntryId(user, json("title", "Dear future me", "contentJson", doc("I hope you got the job.")));
    }

    // Sealing

    @Test
    void sealingAnswers200WithTheSealedEntryAlreadyRedacted() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);

        Map<String, Object> sealed =
                bodyMap(seal(alice, id, NEXT_YEAR).expectStatus().isOk());

        assertThat(sealed)
                .containsEntry("id", id)
                .containsEntry("isLocked", true)
                .containsEntry("unlockDate", NEXT_YEAR)
                .containsEntry("version", 1)
                .containsEntry("title", null)
                .containsEntry("contentJson", null)
                .containsEntry("contentText", null)
                .containsEntry("templateId", null)
                .containsEntry("soundtrack", null)
                .containsEntry("replyToEntryId", null)
                .containsEntry("tags", List.of())
                .containsEntry("replyIds", List.of())
                .containsEntry("media", List.of())
                .containsEntry("entryDate", "2026-10-31");
        assertThat(sealed.keySet()).as("the same shape as an open entry").hasSize(18);
        assertThat(row(id).get("unlock_date")).isNotNull();
    }

    @Test
    void aSealedEntryIsStillThereUnderneathSoItCanOpenAgain() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);

        seal(alice, id, NEXT_YEAR).expectStatus().isOk();

        assertThat(row(id))
                .containsEntry("title", "Dear future me")
                .containsEntry("content_text", "I hope you got the job.");
    }

    @Test
    void sealingClearsTheSeenMark() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);
        jdbc.sql("UPDATE entries SET unlock_seen_at = NOW() WHERE id = :id")
                .param("id", UUID.fromString(id))
                .update();

        seal(alice, id, NEXT_YEAR).expectStatus().isOk();

        assertThat(row(id)).containsEntry("unlock_seen_at", null);
    }

    @Test
    void sealingLeavesTheBackgroundJobsFlagAlone() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);
        jdbc.sql("UPDATE entries SET unlock_notified = TRUE WHERE id = :id")
                .param("id", UUID.fromString(id))
                .update();

        seal(alice, id, NEXT_YEAR).expectStatus().isOk();

        assertThat(row(id)).containsEntry("unlock_notified", true);
    }

    @Test
    void anEmptyEntryCanBeSealedToo() {
        User alice = aUser();
        String id = createEntryId(alice, json());

        seal(alice, id, TOMORROW_9AM)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.isLocked")
                .isEqualTo(true);
    }

    @Test
    void theUnlockInstantIsKeptToTheMicrosecondTheDatabaseKeeps() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);

        Map<String, Object> sealed = bodyMap(
                seal(alice, id, "2030-01-01T09:00:00.123456789Z").expectStatus().isOk());

        assertThat(sealed).containsEntry("unlockDate", "2030-01-01T09:00:00.123456Z");
        assertThat(getEntry(alice, id)).containsEntry("unlockDate", "2030-01-01T09:00:00.123456Z");
    }

    @Test
    void anOffsetInTheUnlockDateIsAcceptedAndAnsweredInUtc() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);

        Map<String, Object> sealed = bodyMap(
                seal(alice, id, "2027-10-31T09:00:00+11:00").expectStatus().isOk());

        assertThat(sealed).containsEntry("unlockDate", "2027-10-30T22:00:00Z");
    }

    // Which dates are allowed

    @Test
    void anUnlockDateThatIsNotInTheFutureIs400AndNothingChanges() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);
        Map<String, Object> before = row(id);

        for (String date : new String[] {"2026-10-31T01:00:00Z", "2026-10-31T00:59:59Z", "2020-01-01T00:00:00Z"}) {
            String body = bodyText(seal(alice, id, date).expectStatus().isBadRequest());

            assertThat(problemType(body)).as(date).isEqualTo("validation-failed");
            assertThat(fieldErrors(body)).as(date).containsExactly(Map.entry("unlockDate", "must be in the future"));
        }
        assertThat(row(id)).isEqualTo(before);
    }

    @Test
    void anUnlockDateOfExactly100YearsAheadIsAcceptedAndOneSecondMoreIs400() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);

        String body =
                bodyText(seal(alice, id, "2126-10-31T01:00:01Z").expectStatus().isBadRequest());
        assertThat(fieldErrors(body)).containsExactly(Map.entry("unlockDate", "must be at most 100 years ahead"));
        assertThat(row(id).get("unlock_date")).isNull();

        seal(alice, id, "2126-10-31T01:00:00Z").expectStatus().isOk();
    }

    @Test
    void aMissingNullOrGarbageUnlockDateIsRefused() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);

        for (String broken : List.of("{}", "{\"unlockDate\": null}")) {
            String body = bodyText(client.post()
                    .uri(ENTRIES + "/" + id + "/seal")
                    .header(HEADER, alice.getId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(broken)
                    .exchange()
                    .expectStatus()
                    .isBadRequest());
            assertThat(problemType(body)).as(broken).isEqualTo("validation-failed");
            assertThat(fieldErrors(body)).as(broken).containsEntry("unlockDate", "must not be null");
        }
        for (String broken :
                List.of("{\"unlockDate\": \"next year\"}", "{\"unlockDate\": \"2027-10-31\"}", "[]", "{")) {
            String body = bodyText(client.post()
                    .uri(ENTRIES + "/" + id + "/seal")
                    .header(HEADER, alice.getId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(broken)
                    .exchange()
                    .expectStatus()
                    .isBadRequest());
            assertThat(problemType(body)).as(broken).isEqualTo("malformed-request");
        }
        assertThat(row(id).get("unlock_date")).isNull();
    }

    // A sealed entry refuses edits

    @Test
    void editingASealedEntryIs409EntrySealedAndTheAnswerLeaksNothingFromIt() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);
        seal(alice, id, NEXT_YEAR).expectStatus().isOk();
        Map<String, Object> before = row(id);

        String body = bodyText(put(alice, id, json("version", 1, "entryDate", "2026-10-31", "title", "Peeking"))
                .expectStatus()
                .isEqualTo(409));

        assertThat(problemType(body)).isEqualTo("entry-sealed");
        assertThat(JsonPath.<Integer>read(body, "$.status")).isEqualTo(409);
        assertThat(body)
                .doesNotContain("Dear future me")
                .doesNotContain("job")
                .doesNotContain("Peeking")
                .doesNotContain(NEXT_YEAR);
        assertThat(row(id)).isEqualTo(before);
    }

    @Test
    void aSealedEntryRefusesEditsWhateverVersionTheClientSends() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);
        seal(alice, id, NEXT_YEAR).expectStatus().isOk();

        for (int version : new int[] {0, 1, 2, 99}) {
            String body = bodyText(put(alice, id, json("version", version, "entryDate", "2026-10-31"))
                    .expectStatus()
                    .isEqualTo(409));
            assertThat(problemType(body)).as("version " + version).isEqualTo("entry-sealed");
        }
    }

    @Test
    void movingTheUnlockDateEarlierOnASealedEntryIs409AndNothingChanges() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);
        seal(alice, id, NEXT_YEAR).expectStatus().isOk();
        Map<String, Object> before = row(id);

        for (String earlier : new String[] {TOMORROW_9AM, "2027-10-31T21:59:59Z", "2027-10-31T22:00:00Z"}) {
            String body = bodyText(seal(alice, id, earlier).expectStatus().isEqualTo(409));

            assertThat(problemType(body))
                    .as(earlier + " (the last one is the same date)")
                    .isEqualTo("entry-sealed");
            assertThat(body).doesNotContain("Dear future me");
        }
        assertThat(row(id)).isEqualTo(before);
    }

    @Test
    void movingTheUnlockDateLaterIsAllowedAndStaysRedacted() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);
        seal(alice, id, NEXT_YEAR).expectStatus().isOk();

        Map<String, Object> later =
                bodyMap(seal(alice, id, "2030-06-15T00:00:00Z").expectStatus().isOk());

        assertThat(later)
                .containsEntry("unlockDate", "2030-06-15T00:00:00Z")
                .containsEntry("isLocked", true)
                .containsEntry("title", null);
        assertThat(later).containsEntry("version", 2);
    }

    @Test
    void aPastUnlockDateIsA400EvenOnASealedEntry() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);
        seal(alice, id, NEXT_YEAR).expectStatus().isOk();

        // Not in the future at all: that is a bad value (400) whatever the entry is, not a move "earlier" (409).
        seal(alice, id, "2020-01-01T00:00:00Z").expectStatus().isBadRequest();
    }

    // Opening

    @Test
    void theEntryIsRedactedUntilTheUnlockInstantAndWholeFromThenOn() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);
        seal(alice, id, TOMORROW_9AM).expectStatus().isOk();

        assertThat(getEntry(alice, id)).containsEntry("isLocked", true).containsEntry("title", null);
        clockAt("2026-10-31T21:59:59Z");
        assertThat(getEntry(alice, id))
                .as("one second early")
                .containsEntry("isLocked", true)
                .containsEntry("contentText", null);

        clockAt(TOMORROW_9AM);
        Map<String, Object> opened = getEntry(alice, id);
        assertThat(opened)
                .as("at the unlock instant")
                .containsEntry("isLocked", false)
                .containsEntry("title", "Dear future me")
                .containsEntry("contentText", "I hope you got the job.")
                .containsEntry("unlockDate", TOMORROW_9AM);
        assertThat(opened.get("contentJson")).isNotNull();
    }

    @Test
    void anEntryThatHasOpenedCanBeEditedAgainAndTheAnswerShowsEverything() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);
        seal(alice, id, TOMORROW_9AM).expectStatus().isOk();
        clockAt("2026-11-01T00:00:00Z");

        Map<String, Object> edited =
                bodyMap(put(alice, id, json("version", 1, "entryDate", "2026-10-31", "title", "Read it, added a note"))
                        .expectStatus()
                        .isOk());

        assertThat(edited)
                .containsEntry("isLocked", false)
                .containsEntry("title", "Read it, added a note")
                .containsEntry("unlockDate", TOMORROW_9AM)
                .containsEntry("version", 2);
    }

    @Test
    void anOpenedLootDropCanBeSealedAgainToAnyFutureDateAndTheSeenMarkIsCleared() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);
        seal(alice, id, TOMORROW_9AM).expectStatus().isOk();
        clockAt("2026-11-05T00:00:00Z");
        jdbc.sql("UPDATE entries SET unlock_seen_at = NOW() WHERE id = :id")
                .param("id", UUID.fromString(id))
                .update();

        // 10 November is later than now but earlier than the original date? No: the original date has passed, so any
        // future date is fine. The rule "only later" is about a seal that is still in force.
        Map<String, Object> again =
                bodyMap(seal(alice, id, "2026-11-10T00:00:00Z").expectStatus().isOk());

        assertThat(again).containsEntry("isLocked", true).containsEntry("unlockDate", "2026-11-10T00:00:00Z");
        assertThat(row(id)).containsEntry("unlock_seen_at", null);
    }

    // Replays

    @Test
    void replayingTheCreateOfASealedEntryAnswers200AndRedacted() {
        User alice = aUser();
        UUID id = UUID.randomUUID();
        createEntry(alice, json("id", id.toString(), "title", "Dear future me"));
        seal(alice, id, NEXT_YEAR).expectStatus().isOk();

        Map<String, Object> replay = bodyMap(post(alice, json("id", id.toString(), "title", "Dear future me"))
                .expectStatus()
                .isOk());

        assertThat(replay).containsEntry("isLocked", true).containsEntry("title", null);
    }

    // Whose entry it is

    @Test
    void sealingAnotherUsersEntryIs404AndSoIsSealingOneThatDoesNotExist() {
        User alice = aUser();
        User bob = aUser();
        String bobsEntry = aTitledEntryWithWords(bob);
        Map<String, Object> before = row(bobsEntry);

        String someonesElse =
                bodyText(seal(alice, bobsEntry, NEXT_YEAR).expectStatus().isNotFound());
        String missing = bodyText(
                seal(alice, UUID.randomUUID(), NEXT_YEAR).expectStatus().isNotFound());

        assertThat(problemType(someonesElse)).isEqualTo("not-found");
        assertThat(JsonPath.<String>read(missing, "$.detail"))
                .isEqualTo(JsonPath.<String>read(someonesElse, "$.detail"));
        assertThat(row(bobsEntry)).isEqualTo(before);
    }

    @Test
    void sealingWithAnIdThatIsNotAUuidIs400AndFromAUserWhoIsNotThereIs404() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);

        String body =
                bodyText(seal(alice, "not-a-uuid", NEXT_YEAR).expectStatus().isBadRequest());
        assertThat(problemType(body)).isEqualTo("malformed-request");

        User nobody = new User(UUID.randomUUID(), "nobody@memoirist.test", "Nobody", "Australia/Melbourne");
        seal(nobody, id, NEXT_YEAR).expectStatus().isNotFound();
        assertThat(row(id).get("unlock_date")).isNull();
    }

    @Test
    void sealingNeedsAJsonBody() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);

        client.post()
                .uri(ENTRIES + "/" + id + "/seal")
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.TEXT_PLAIN)
                .body("2027-01-01")
                .exchange()
                .expectStatus()
                .isEqualTo(415);
    }

    @Test
    void sealOnlyAcceptsPost() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);

        client.get()
                .uri(ENTRIES + "/" + id + "/seal")
                .header(HEADER, alice.getId().toString())
                .exchange()
                .expectStatus()
                .isEqualTo(405);
    }

    // Deleting a sealed entry (D-10: allowed; D-30: it stays sealed)

    @Test
    void aSealedEntryCanAlwaysBeDeletedAndStaysSealedInRecentlyDeleted() {
        User alice = aUser();
        String id = aTitledEntryWithWords(alice);
        seal(alice, id, NEXT_YEAR).expectStatus().isOk();

        delete(alice, id).expectStatus().isNoContent();

        Map<String, Object> row = row(id);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("unlock_date")).as("still sealed").isNotNull();
        assertThat(instantOf(row.get("unlock_date"))).isEqualTo(NEXT_YEAR);
        get(alice, id).expectStatus().isNotFound();
        put(alice, id, json("version", 2, "entryDate", "2026-10-31"))
                .expectStatus()
                .isNotFound();
        seal(alice, id, "2030-01-01T00:00:00Z").expectStatus().isNotFound();
    }
}

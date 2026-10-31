package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.template.Template;
import com.memoirist.user.User;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Editing an entry through the API (D-23, API_CONTRACT {@code PUT /entries/{id}}): a full replace guarded by the
 * version the client read. The point of the version is that two devices can't silently overwrite each other, so the
 * tests are about what is <em>not</em> written as much as what is.
 */
class EntryUpdateTest extends EntryApiTest {

    private static Map<String, Object> edit(int version, Object... more) {
        Map<String, Object> body = json("version", version, "entryDate", "2026-10-31");
        for (int i = 0; i < more.length; i += 2) {
            body.put((String) more[i], more[i + 1]);
        }
        return body;
    }

    // The version

    @Test
    void aCurrentVersionSavesAndReturnsTheNextVersion() {
        User alice = aUser();
        Map<String, Object> created = createEntry(alice, json("title", "First"));
        assertThat(created).containsEntry("version", 0);

        Map<String, Object> edited = bodyMap(put(alice, created.get("id"), edit(0, "title", "Second"))
                .expectStatus()
                .isOk());

        assertThat(edited).containsEntry("title", "Second").containsEntry("version", 1);
        assertThat(row(created.get("id"))).containsEntry("title", "Second").containsEntry("version", 1L);
        assertThat(getEntry(alice, created.get("id"))).isEqualTo(edited);

        Map<String, Object> again = bodyMap(put(alice, created.get("id"), edit(1, "title", "Third"))
                .expectStatus()
                .isOk());
        assertThat(again).containsEntry("version", 2);
    }

    @Test
    void anOlderVersionIs409EntryStaleAndChangesNothingAtAll() {
        User alice = aUser();
        Map<String, Object> created = createEntry(alice, json("title", "First", "contentJson", doc("original words")));
        String id = created.get("id").toString();
        put(alice, id, edit(0, "title", "Edited on device B", "contentJson", doc("device B words")))
                .expectStatus()
                .isOk();
        Map<String, Object> before = row(id);

        String body =
                bodyText(put(alice, id, edit(0, "title", "Edited on device A", "contentJson", doc("device A words")))
                        .expectStatus()
                        .isEqualTo(409));

        assertThat(problemType(body)).isEqualTo("entry-stale");
        assertThat(JsonPath.<Integer>read(body, "$.status")).isEqualTo(409);
        assertThat(body)
                .as("nothing of either edit comes back")
                .doesNotContain("device")
                .doesNotContain("Edited");
        assertThat(row(id)).as("the stored row is exactly as device B left it").isEqualTo(before);
        assertThat(row(id)).containsEntry("title", "Edited on device B").containsEntry("version", 1L);
    }

    @Test
    void twoDevicesThatBothReadVersionZeroTheSecondSaveIsRefusedUntilItReloads() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Shared"));
        Map<String, Object> readByA = getEntry(alice, id);
        Map<String, Object> readByB = getEntry(alice, id);

        put(alice, id, edit(((Number) readByA.get("version")).intValue(), "title", "From A"))
                .expectStatus()
                .isOk();
        put(alice, id, edit(((Number) readByB.get("version")).intValue(), "title", "From B"))
                .expectStatus()
                .isEqualTo(409);
        assertThat(row(id)).containsEntry("title", "From A");

        Map<String, Object> reloadedByB = getEntry(alice, id);
        Map<String, Object> saved = bodyMap(put(
                        alice,
                        id,
                        edit(((Number) reloadedByB.get("version")).intValue(), "title", "From B, after reading A's"))
                .expectStatus()
                .isOk());
        assertThat(saved).containsEntry("version", 2);
    }

    @Test
    void aVersionAheadOfTheStoredOneIsRefusedToo() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Unchanged"));

        for (int claimed : new int[] {1, 2, 99, Integer.MAX_VALUE}) {
            String body = bodyText(put(alice, id, edit(claimed, "title", "Forged"))
                    .expectStatus()
                    .isEqualTo(409));
            assertThat(problemType(body)).as("version " + claimed).isEqualTo("entry-stale");
        }
        assertThat(row(id)).containsEntry("title", "Unchanged").containsEntry("version", 0L);
    }

    @Test
    void theVersionTheClientSendsIsNeverCopiedOntoTheEntry() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "A"));
        put(alice, id, edit(0, "title", "B")).expectStatus().isOk();
        put(alice, id, edit(1, "title", "C")).expectStatus().isOk();

        // The row is at 2. A client that claims 2 gets 3: the new version comes from the database, not from the body.
        Map<String, Object> saved =
                bodyMap(put(alice, id, edit(2, "title", "D")).expectStatus().isOk());

        assertThat(saved).containsEntry("version", 3);
        assertThat(row(id)).containsEntry("version", 3L);
        Method[] methods = Entry.class.getMethods();
        assertThat(Arrays.stream(methods).map(Method::getName))
                .as("Entry has no way to set its version")
                .doesNotContain("setVersion");
    }

    @Test
    void aStaleVersionWinsOverEveryOtherCheckExceptTheSeal() {
        User alice = aUser();
        User bob = aUser();
        Template bobsTemplate = aTemplate(bob, "Bob's");
        String id = createEntryId(alice, json("title", "Mine"));
        put(alice, id, edit(0, "title", "Newer")).expectStatus().isOk();

        // A future date, a bad caption and someone else's template would each be refused on their own. The entry has
        // moved on, though, so the client must reload before anything else is worth telling it.
        put(
                        alice,
                        id,
                        json(
                                "version",
                                0,
                                "entryDate",
                                "2999-01-01",
                                "templateId",
                                bobsTemplate.getId().toString(),
                                "contentJson",
                                json("type", "image")))
                .expectStatus()
                .isEqualTo(409);

        assertThat(row(id)).containsEntry("title", "Newer").containsEntry("version", 1L);
    }

    @Test
    void anUpdateThatChangesNothingSavesNothingAndKeepsTheVersion() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Same", "contentJson", doc("same words")));
        OffsetDateTime updatedBefore = updatedAt(id);

        Map<String, Object> answer = bodyMap(put(alice, id, edit(0, "title", "Same", "contentJson", doc("same words")))
                .expectStatus()
                .isOk());

        assertThat(answer).containsEntry("version", 0).containsEntry("title", "Same");
        assertThat(row(id)).containsEntry("version", 0L);
        assertThat(updatedAt(id)).isEqualTo(updatedBefore);
    }

    @Test
    void anUpdateThatChangesSomethingMovesUpdatedAtForward() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Before"));
        OffsetDateTime updatedBefore = updatedAt(id);

        put(alice, id, edit(0, "title", "After")).expectStatus().isOk();

        assertThat(updatedAt(id)).isAfter(updatedBefore);
    }

    @Test
    void aMissingOrNegativeVersionIs400AndAWordIsMalformed() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Keep"));

        String missing = bodyText(put(alice, id, json("entryDate", "2026-10-31", "title", "x"))
                .expectStatus()
                .isBadRequest());
        assertThat(problemType(missing)).isEqualTo("validation-failed");
        assertThat(fieldErrors(missing)).containsEntry("version", "must not be null");

        String negative = bodyText(put(alice, id, edit(-1)).expectStatus().isBadRequest());
        assertThat(fieldErrors(negative)).containsOnlyKeys("version");

        String word = bodyText(put(alice, id, json("version", "latest", "entryDate", "2026-10-31"))
                .expectStatus()
                .isBadRequest());
        assertThat(problemType(word)).isEqualTo("malformed-request");

        assertThat(row(id)).containsEntry("title", "Keep").containsEntry("version", 0L);
    }

    @Test
    void theEntryDateIsRequiredOnAnUpdateAndEveryBadFieldIsNamedAtOnce() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Keep"));

        String body =
                bodyText(put(alice, id, json("title", "   ")).expectStatus().isBadRequest());

        assertThat(problemType(body)).isEqualTo("validation-failed");
        assertThat(fieldErrors(body)).containsOnlyKeys("version", "entryDate", "title");
    }

    // Full replace

    @Test
    void aPutIsAFullReplaceSoFieldsLeftOutAreCleared() {
        User alice = aUser();
        Template template = aTemplate(alice, "Trip day");
        String id = createEntryId(
                alice,
                json(
                        "title", "Everything",
                        "templateId", template.getId().toString(),
                        "contentJson", doc("some words")));

        Map<String, Object> cleared = bodyMap(put(alice, id, json("version", 0, "entryDate", "2026-10-31"))
                .expectStatus()
                .isOk());

        assertThat(cleared)
                .containsEntry("title", null)
                .containsEntry("templateId", null)
                .containsEntry("contentJson", null)
                .containsEntry("contentText", "");
        assertThat(row(id))
                .containsEntry("title", null)
                .containsEntry("template_id", null)
                .containsEntry("content_json", null)
                .containsEntry("preview_line", null);
    }

    @Test
    void aPutStripsAndChecksTheTitleLikeACreate() {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Keep"));

        Map<String, Object> edited = bodyMap(
                put(alice, id, edit(0, "title", "  Spaced  ")).expectStatus().isOk());
        assertThat(edited).containsEntry("title", "Spaced");

        for (String bad : new String[] {"", "   ", "t".repeat(151)}) {
            String body = bodyText(
                    put(alice, id, edit(1, "title", bad)).expectStatus().isBadRequest());
            assertThat(fieldErrors(body)).as("title of length " + bad.length()).containsOnlyKeys("title");
        }
        assertThat(row(id)).containsEntry("title", "Spaced").containsEntry("version", 1L);
    }

    @Test
    void aPutCanSetChangeAndClearTheTemplate() {
        User alice = aUser();
        Template first = aTemplate(alice, "First");
        Template second = aTemplate(alice, "Second");
        String id = createEntryId(alice, json("templateId", first.getId().toString()));

        put(alice, id, edit(0, "templateId", second.getId().toString()))
                .expectStatus()
                .isOk();
        assertThat(row(id)).containsEntry("template_id", second.getId());

        put(alice, id, edit(1, "templateId", null)).expectStatus().isOk();
        assertThat(row(id)).containsEntry("template_id", null);
    }

    @Test
    void anotherUsersTemplateOnAPutIs404AndChangesNothing() {
        User alice = aUser();
        User bob = aUser();
        Template bobsTemplate = aTemplate(bob, "Bob's ritual");
        String id = createEntryId(alice, json("title", "Keep"));

        String body = bodyText(put(
                        alice,
                        id,
                        edit(
                                0,
                                "title",
                                "Changed",
                                "templateId",
                                bobsTemplate.getId().toString()))
                .expectStatus()
                .isNotFound());

        assertThat(problemType(body)).isEqualTo("not-found");
        assertThat(body).doesNotContain("ritual");
        assertThat(row(id))
                .containsEntry("title", "Keep")
                .containsEntry("template_id", null)
                .containsEntry("version", 0L);
    }

    @Test
    void aTemplateThatWasDeletedAndStillOnTheEntryDoesNotBlockEditing() {
        // Deleting a template empties entries.template_id (the foreign key does it). A client that has not noticed yet
        // may still send the old id; that is a new template reference and it is refused like any unknown one, while
        // sending null (or the entry's current value) works.
        User alice = aUser();
        Template template = aTemplate(alice, "Short-lived");
        String id = createEntryId(alice, json("templateId", template.getId().toString()));
        jdbc.sql("DELETE FROM templates WHERE id = :id")
                .param("id", template.getId())
                .update();
        assertThat(row(id)).containsEntry("template_id", null);

        put(alice, id, edit(0, "templateId", template.getId().toString()))
                .expectStatus()
                .isNotFound();
        put(alice, id, edit(0, "title", "Fine now")).expectStatus().isOk();
    }

    // Fields the client must not set

    @Test
    void aPutIgnoresEverythingThatIsNotAnEditableField() {
        User alice = aUser();
        User bob = aUser();
        String id = createEntryId(alice, json("title", "Mine"));
        jdbc.sql("UPDATE entries SET soundtrack = 'spotify:track:1a2B3c4D5e6F7g8H9i0J1k' WHERE id = :id")
                .param("id", UUID.fromString(id))
                .update();
        Map<String, Object> before = row(id);

        bodyMap(put(
                        alice,
                        id,
                        edit(
                                0,
                                "title",
                                "Mine",
                                "userId",
                                bob.getId().toString(),
                                "weeklyLogId",
                                UUID.randomUUID().toString(),
                                "unlockDate",
                                "2099-01-01T00:00:00Z",
                                "deletedAt",
                                "2026-01-01T00:00:00Z",
                                "contentText",
                                "text the client wrote itself",
                                "dateUnknown",
                                true,
                                "soundtrack",
                                null,
                                "replyToEntryId",
                                UUID.randomUUID().toString(),
                                "tagIds",
                                List.of(UUID.randomUUID().toString())))
                .expectStatus()
                .isOk());

        assertThat(row(id)).as("nothing changed, so not even the version moved").isEqualTo(before);
        assertThat(row(id)).containsEntry("soundtrack", "spotify:track:1a2B3c4D5e6F7g8H9i0J1k");
    }

    // Whose entry it is

    @Test
    void anotherUsersEntryIs404ForAPutAndStaysAsItWas() {
        User alice = aUser();
        User bob = aUser();
        String bobsEntry = createEntryId(bob, json("title", "Bob's private thought"));
        Map<String, Object> before = row(bobsEntry);

        String body = bodyText(put(alice, bobsEntry, edit(0, "title", "Hijacked"))
                .expectStatus()
                .isNotFound());

        assertThat(problemType(body)).isEqualTo("not-found");
        assertThat(body).doesNotContain("Bob").doesNotContain("private");
        assertThat(row(bobsEntry)).isEqualTo(before);
    }

    @Test
    void anEntryThatDoesNotExistLooksExactlyLikeSomeoneElses() {
        User alice = aUser();
        User bob = aUser();
        String bobsEntry = createTitledEntry(bob);

        String someonesElse =
                bodyText(put(alice, bobsEntry, edit(0)).expectStatus().isNotFound());
        String missing =
                bodyText(put(alice, UUID.randomUUID(), edit(0)).expectStatus().isNotFound());

        assertThat(JsonPath.<String>read(missing, "$.detail"))
                .isEqualTo(JsonPath.<String>read(someonesElse, "$.detail"));
        assertThat(JsonPath.<String>read(missing, "$.type")).isEqualTo(JsonPath.<String>read(someonesElse, "$.type"));
    }

    @Test
    void aPutWithAnIdThatIsNotAUuidIs400() {
        User alice = aUser();

        String body = bodyText(put(alice, "not-a-uuid", edit(0)).expectStatus().isBadRequest());

        assertThat(problemType(body)).isEqualTo("malformed-request");
    }

    @Test
    void aPutFromAUserWhoIsNotThereIs404() {
        User alice = aUser();
        String id = createTitledEntry(alice);
        User nobody = new User(UUID.randomUUID(), "nobody@memoirist.test", "Nobody", "Australia/Melbourne");

        put(nobody, id, edit(0, "title", "x")).expectStatus().isNotFound();
        assertThat(row(id)).containsEntry("version", 0L);
    }

    @Test
    void aPutNeedsAJsonBody() {
        User alice = aUser();
        String id = createTitledEntry(alice);

        String body = bodyText(client.put()
                .uri(ENTRIES + "/" + id)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.TEXT_PLAIN)
                .body("title=x")
                .exchange()
                .expectStatus()
                .isEqualTo(415));

        assertThat(problemType(body)).isEqualTo("unsupported-media-type");
    }

    // Racing

    @Test
    void whenManyDevicesSaveTheSameVersionAtOnceExactlyOneWinsAndTheRestAreStale() throws Exception {
        User alice = aUser();
        String id = createEntryId(alice, json("title", "Start"));
        int devices = 8;
        ExecutorService pool = Executors.newFixedThreadPool(devices);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int device = 0; device < devices; device++) {
                String title = "Device " + device;
                statuses.add(pool.submit(() -> {
                    go.await();
                    return put(alice, id, edit(0, "title", title))
                            .expectBody(String.class)
                            .returnResult()
                            .getStatus()
                            .value();
                }));
            }
            go.countDown();

            List<Integer> results = new ArrayList<>();
            for (Future<Integer> status : statuses) {
                results.add(status.get(60, TimeUnit.SECONDS));
            }

            assertThat(results)
                    .as("one save won, the others were told the entry changed")
                    .containsOnlyOnce(200);
            assertThat(results)
                    .filteredOn(status -> status != 200)
                    .hasSize(devices - 1)
                    .containsOnly(409);
            assertThat(row(id)).containsEntry("version", 1L);
            assertThat(row(id).get("title").toString()).startsWith("Device ");
        } finally {
            pool.shutdownNow();
        }
    }

    // Helpers

    private OffsetDateTime updatedAt(String id) {
        return jdbc.sql("SELECT updated_at FROM entries WHERE id = :id")
                .param("id", UUID.fromString(id))
                .query(OffsetDateTime.class)
                .single();
    }
}

package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.template.Template;
import com.memoirist.user.User;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The strict seal (D-10, non-negotiable 4), end to end. One entry is given something private in <em>every</em> place
 * the database can hold it (title, caption, template, tag, soundtrack, a reply target, a photo), and then each way the
 * API can answer about it is read while it is sealed: GET, the seal answer itself, a replayed create, and the
 * {@code 409} of a refused edit. None may show any of it, in any field or as text anywhere in the body. The same
 * entry is read before sealing to prove the private things really were there to leak.
 *
 * <p>The feed, week detail and lists call the same {@code EntryMapper}, and are tested where they are built (3.7, 3.9).
 */
class EntryRedactionTest extends EntryApiTest {

    private static final String SECRET_TITLE = "TOP-SECRET-TITLE";
    private static final String SECRET_WORDS = "TOP-SECRET-WORDS";
    private static final String SECRET_TAG = "TOP-SECRET-TAG";
    private static final String SECRET_SONG = "spotify:track:SECRETsongSECRETsong12";
    private static final String SECRET_KEY = "users/x/entries/y/TOP-SECRET-PHOTO.jpg";
    private static final String UNLOCK = "2027-10-31T22:00:00Z";

    /** Everything an {@code Entry} response may show while the entry is sealed (D-10, API_CONTRACT). */
    private static final Set<String> MAY_BE_SHOWN = Set.of(
            "id",
            "weeklyLogId",
            "entryDate",
            "createdAt",
            "updatedAt",
            "unlockDate",
            "isLocked",
            "dateUnknown",
            "version");

    private User alice;
    private Template template;
    private UUID replyTarget;
    private String id;

    @BeforeEach
    void anEntryWithSomethingPrivateInEveryPlace() {
        alice = aUser();
        template = aTemplate(alice, "TOP-SECRET-TEMPLATE");
        replyTarget = UUID.fromString(createEntryId(alice, json("title", "The Loot Drop being answered")));
        id = createEntryId(
                alice,
                json(
                        "entryDate",
                        "2026-10-20",
                        "dateUnknown",
                        true,
                        "title",
                        SECRET_TITLE,
                        "templateId",
                        template.getId().toString(),
                        "contentJson",
                        doc(SECRET_WORDS)));
        UUID tagId = UUID.randomUUID();
        jdbc.sql("INSERT INTO tags (id, user_id, name) VALUES (:id, :user, :name)")
                .param("id", tagId)
                .param("user", alice.getId())
                .param("name", SECRET_TAG)
                .update();
        jdbc.sql("INSERT INTO entry_tags (entry_id, tag_id) VALUES (:entry, :tag)")
                .param("entry", UUID.fromString(id))
                .param("tag", tagId)
                .update();
        jdbc.sql("UPDATE entries SET soundtrack = :song, reply_to_entry_id = :reply WHERE id = :id")
                .param("song", SECRET_SONG)
                .param("reply", replyTarget)
                .param("id", UUID.fromString(id))
                .update();
        aPhoto(id, SECRET_KEY, 0);
    }

    private List<String> theSecrets() {
        return List.of(
                SECRET_TITLE,
                SECRET_WORDS,
                SECRET_TAG,
                SECRET_SONG,
                "SECRETsong",
                "TOP-SECRET",
                template.getId().toString(),
                replyTarget.toString(),
                "PHOTO.jpg",
                "storage.invalid", // the host a presigned URL would carry in the test profile
                "X-Amz");
    }

    private void assertNothingPrivateIn(String body, String where) {
        for (String secret : theSecrets()) {
            assertThat(body).as("%s must not contain %s", where, secret).doesNotContain(secret);
        }
    }

    /** The names of the fields that carry a value: not null, not an empty list, and not {@code false}. */
    @SuppressWarnings("unchecked")
    private static Set<String> fieldsWithAValue(String body) {
        Map<String, Object> fields = JsonPath.read(body, "$");
        Set<String> shown = new HashSet<>();
        new HashMap<>(fields).forEach((name, value) -> {
            boolean empty =
                    value == null || Boolean.FALSE.equals(value) || (value instanceof List<?> list && list.isEmpty());
            if (!empty) {
                shown.add(name);
            }
        });
        return shown;
    }

    // Before: it is all really there

    @Test
    void whileOpenTheEntryShowsEverythingSoTheLaterTestsHaveSomethingToLeak() {
        String body = bodyText(get(alice, id).expectStatus().isOk());

        assertThat(JsonPath.<String>read(body, "$.title")).isEqualTo(SECRET_TITLE);
        assertThat(JsonPath.<String>read(body, "$.contentText")).isEqualTo(SECRET_WORDS);
        assertThat(JsonPath.<String>read(body, "$.templateId"))
                .isEqualTo(template.getId().toString());
        assertThat(JsonPath.<String>read(body, "$.tags[0].name")).isEqualTo(SECRET_TAG);
        assertThat(JsonPath.<String>read(body, "$.tags[0].source")).isEqualTo("USER");
        assertThat(JsonPath.<String>read(body, "$.soundtrack")).isEqualTo(SECRET_SONG);
        assertThat(JsonPath.<String>read(body, "$.replyToEntryId")).isEqualTo(replyTarget.toString());
        assertThat(JsonPath.<String>read(body, "$.media[0].fileUrl"))
                .contains("TOP-SECRET-PHOTO.jpg")
                .contains("X-Amz");
        assertThat(JsonPath.<String>read(body, "$.media[0].mediaType")).isEqualTo("image/jpeg");
        assertThat(JsonPath.<Boolean>read(body, "$.isLocked")).isFalse();
        assertThat(JsonPath.<Boolean>read(body, "$.dateUnknown")).isTrue();
    }

    // After: nothing

    @Test
    void theSealAnswerShowsNoneOfIt() {
        String body = bodyText(seal(alice, id, UNLOCK).expectStatus().isOk());

        assertNothingPrivateIn(body, "the seal answer");
        assertThat(fieldsWithAValue(body)).isSubsetOf(MAY_BE_SHOWN);
        assertThat(JsonPath.<Boolean>read(body, "$.isLocked")).isTrue();
    }

    @Test
    void getShowsNoneOfItAndNoFieldBesidesTheAllowedOnesHasAValue() {
        seal(alice, id, UNLOCK).expectStatus().isOk();

        String body = bodyText(get(alice, id).expectStatus().isOk());

        assertNothingPrivateIn(body, "GET");
        assertThat(fieldsWithAValue(body)).isSubsetOf(MAY_BE_SHOWN);
        assertThat(fieldsWithAValue(body))
                .contains(
                        "id",
                        "weeklyLogId",
                        "entryDate",
                        "createdAt",
                        "updatedAt",
                        "unlockDate",
                        "isLocked",
                        "dateUnknown");
        assertThat(JsonPath.<String>read(body, "$.entryDate")).isEqualTo("2026-10-20");
        assertThat(JsonPath.<Boolean>read(body, "$.dateUnknown"))
                .as("the memory date is not private (D-34)")
                .isTrue();
        assertThat(JsonPath.<List<?>>read(body, "$.tags")).isEmpty();
        assertThat(JsonPath.<List<?>>read(body, "$.replyIds")).isEmpty();
        assertThat(JsonPath.<List<?>>read(body, "$.media")).isEmpty();
    }

    @Test
    void aReplayedCreateShowsNoneOfIt() {
        seal(alice, id, UNLOCK).expectStatus().isOk();

        String body = bodyText(post(alice, json("id", id, "title", SECRET_TITLE))
                .expectStatus()
                .isOk());

        assertNothingPrivateIn(body, "the replayed create");
        assertThat(fieldsWithAValue(body)).isSubsetOf(MAY_BE_SHOWN);
    }

    @Test
    void aRefusedEditShowsNoneOfIt() {
        seal(alice, id, UNLOCK).expectStatus().isOk();

        String body = bodyText(put(alice, id, json("version", 1, "entryDate", "2026-10-20", "title", "x"))
                .expectStatus()
                .isEqualTo(409));

        assertNothingPrivateIn(body, "the 409");
    }

    @Test
    void aRefusedEarlierUnlockDateShowsNoneOfIt() {
        seal(alice, id, UNLOCK).expectStatus().isOk();

        String body =
                bodyText(seal(alice, id, "2026-12-01T00:00:00Z").expectStatus().isEqualTo(409));

        assertNothingPrivateIn(body, "the 409");
    }

    @Test
    void anotherUsersRequestShowsNothingAtAllNotEvenARedactedEntry() {
        seal(alice, id, UNLOCK).expectStatus().isOk();
        User bob = aUser();

        String body = bodyText(get(bob, id).expectStatus().isNotFound());

        assertThat(JsonPath.<String>read(body, "$.detail")).doesNotContain(id);
        assertThat(body).doesNotContain("isLocked").doesNotContain("unlockDate");
    }

    @Test
    void theErrorForAMissingEntryAndForASealedOneAreNotTheSameQuestion() {
        // 404 is for "not yours or not there"; a sealed entry that is yours is answered 200 redacted, never 404 and
        // never 403, so the owner can see that the Loot Drop exists and when it opens.
        seal(alice, id, UNLOCK).expectStatus().isOk();

        get(alice, id).expectStatus().isOk();
        get(alice, UUID.randomUUID()).expectStatus().isNotFound();
    }

    // Opening shows it all again, so redaction was a view, not a deletion

    @Test
    void whenTheLootDropOpensEverythingThatWasHiddenComesBack() {
        seal(alice, id, UNLOCK).expectStatus().isOk();
        clockAt(UNLOCK);

        String body = bodyText(get(alice, id).expectStatus().isOk());

        assertThat(JsonPath.<String>read(body, "$.title")).isEqualTo(SECRET_TITLE);
        assertThat(JsonPath.<String>read(body, "$.contentText")).isEqualTo(SECRET_WORDS);
        assertThat(JsonPath.<String>read(body, "$.templateId"))
                .isEqualTo(template.getId().toString());
        assertThat(JsonPath.<String>read(body, "$.tags[0].name")).isEqualTo(SECRET_TAG);
        assertThat(JsonPath.<String>read(body, "$.soundtrack")).isEqualTo(SECRET_SONG);
        assertThat(JsonPath.<String>read(body, "$.replyToEntryId")).isEqualTo(replyTarget.toString());
        assertThat(JsonPath.<String>read(body, "$.media[0].fileUrl")).contains("TOP-SECRET-PHOTO.jpg");
        assertThat(JsonPath.<Boolean>read(body, "$.isLocked")).isFalse();
    }
}

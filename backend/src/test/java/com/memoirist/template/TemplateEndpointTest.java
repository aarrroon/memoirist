package com.memoirist.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.entry.Entry;
import com.memoirist.entry.EntryRepository;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The five {@code /api/v1/templates} operations in the whole app: real Tomcat, real Postgres, real JSON. It checks each
 * row of API_CONTRACT (Templates) for the happy path, the {@code 400} rules and the limits on the subheadings, another
 * user's template, the client-supplied id of D-12, and what a change or a delete does to the entries that started from
 * the template (D-06).
 *
 * <p>Every test makes its own users and sends their id in {@code X-Dev-User-Id}, and the users (with everything that
 * hangs off them) are deleted afterwards, so the seeded dev user and the other test classes that share this Postgres
 * container are left alone.
 */
@IntegrationTest
class TemplateEndpointTest {

    private static final String TEMPLATES = "/api/v1/templates";
    private static final String HEADER = "X-Dev-User-Id";

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Autowired
    WeeklyLogRepository weeks;

    @Autowired
    EntryRepository entries;

    @Autowired
    JdbcClient jdbc;

    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void deleteTheUsersThisTestMade() {
        created.forEach(id ->
                jdbc.sql("DELETE FROM users WHERE id = :id").param("id", id).update());
    }

    // POST

    @Test
    void postCreatesATemplateAndAnswers201WithExactlyTheContractFields() {
        User alice = aUser();

        post(alice, json("name", "Trip day", "subheadings", List.of("Where did we go?", "Best moment", "Food")))
                .expectStatus()
                .isCreated()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(Map.class)
                .value(body -> {
                    // Exactly these keys: no userId, and nothing from a later phase.
                    assertThat(body.keySet())
                            .containsExactlyInAnyOrder("id", "name", "subheadings", "createdAt", "updatedAt");
                    assertThat(UUID.fromString(body.get("id").toString())).isNotNull();
                    assertThat(body).containsEntry("name", "Trip day");
                    assertThat(body.get("subheadings")).isEqualTo(List.of("Where did we go?", "Best moment", "Food"));
                    // ISO-8601 instants, never numbers: parsing fails on anything else.
                    assertThat(Instant.parse(body.get("createdAt").toString())).isNotNull();
                    assertThat(Instant.parse(body.get("updatedAt").toString())).isNotNull();
                });

        assertThat(countTemplates(alice)).isEqualTo(1);
    }

    @Test
    void theRowIsOwnedByTheUserInTheHeaderAndKeepsTheSubheadingsInOrder() {
        User alice = aUser();

        String id = createTemplate(alice, json("name", "Check-in", "subheadings", List.of("Zebra", "Apple", "Mango")));

        assertThat(row(id)).containsEntry("user_id", alice.getId()).containsEntry("name", "Check-in");
        // A JSON array in the order sent, not sorted and not a comma-separated string.
        assertThat(JsonPath.<List<String>>read(subheadingsJson(id), "$")).containsExactly("Zebra", "Apple", "Mango");
    }

    @Test
    void anEmptyListOfSubheadingsIsSavedAsAnEmptyArray() {
        User alice = aUser();

        post(alice, json("name", "Blank page", "subheadings", List.of()))
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.subheadings")
                .isArray()
                .jsonPath("$.subheadings.length()")
                .isEqualTo(0);

        String id = onlyTemplateId(alice);
        assertThat(subheadingsJson(id)).isEqualTo("[]");
        assertThat(jdbc.sql("SELECT jsonb_typeof(subheadings) FROM templates WHERE id = :id")
                        .param("id", UUID.fromString(id))
                        .query(String.class)
                        .single())
                .isEqualTo("array");
    }

    @Test
    void theNameAndEverySubheadingAreStrippedAndRepeatsAndOrderAreKept() {
        User alice = aUser();

        post(
                        alice,
                        json(
                                "name",
                                "  Spiralling check-in \n",
                                "subheadings",
                                List.of(" What happened? ", "Gratitude", "\tGratitude")))
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("Spiralling check-in")
                .jsonPath("$.subheadings[0]")
                .isEqualTo("What happened?")
                .jsonPath("$.subheadings[1]")
                .isEqualTo("Gratitude")
                .jsonPath("$.subheadings[2]")
                .isEqualTo("Gratitude");
    }

    @Test
    void thePromptsMayHoldAnyTextIncludingQuotesEmojiAndNonLatinLetters() {
        User alice = aUser();
        List<String> prompts = List.of("What's \"next\"?", "Mood 🙂", "今日は何をしましたか", "<b>not html</b>");

        String id = createTemplate(alice, json("name", "Odd", "subheadings", prompts));

        get(alice, TEMPLATES + "/" + id)
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("subheadings")).isEqualTo(prompts));
    }

    @Test
    void theServerPicksADifferentIdForEachTemplateWhenTheClientSendsNone() {
        User alice = aUser();

        String first = createTemplate(alice, json("name", "A", "subheadings", List.of()));
        String second = createTemplate(alice, json("name", "A", "subheadings", List.of()));

        assertThat(first).isNotEqualTo(second);
        assertThat(countTemplates(alice)).as("the same name twice is allowed").isEqualTo(2);
    }

    @Test
    void aUserIdOrTimestampsInTheBodyChangeNothing() {
        User alice = aUser();
        User bob = aUser();

        String id = createTemplate(
                alice,
                json(
                        "name", "Mine",
                        "subheadings", List.of(),
                        "userId", bob.getId().toString(),
                        "createdAt", "2001-01-01T00:00:00Z",
                        "updatedAt", "2001-01-01T00:00:00Z"));

        assertThat(row(id)).containsEntry("user_id", alice.getId());
        assertThat(createdAt(id)).isAfter(OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        assertThat(countTemplates(bob)).isZero();
    }

    // POST: the client's own id (D-12)

    @Test
    void aClientSuppliedIdIsUsedAndAnswers201() {
        User alice = aUser();
        UUID id = UUID.randomUUID();

        post(alice, json("id", id.toString(), "name", "Trip day", "subheadings", List.of("Where?")))
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(id.toString());

        assertThat(row(id.toString())).containsEntry("user_id", alice.getId());
    }

    @Test
    void replayingTheSameIdAnswers200WithTheStoredTemplateAndAppliesNothing() {
        User alice = aUser();
        UUID id = UUID.randomUUID();
        createTemplate(alice, json("id", id.toString(), "name", "Trip day", "subheadings", List.of("Where?")));

        // The retry carries a different body. It is not applied: the answer is what is stored.
        post(alice, json("id", id.toString(), "name", "A different name", "subheadings", List.of("Other")))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(id.toString())
                .jsonPath("$.name")
                .isEqualTo("Trip day")
                .jsonPath("$.subheadings[0]")
                .isEqualTo("Where?")
                .jsonPath("$.subheadings.length()")
                .isEqualTo(1);

        assertThat(countTemplates(alice)).isEqualTo(1);
        assertThat(row(id.toString())).containsEntry("name", "Trip day");
    }

    @Test
    void anIdThatAnotherUserHasIs409ConflictAndRevealsNothing() {
        User alice = aUser();
        User bob = aUser();
        UUID id = UUID.randomUUID();
        createTemplate(
                alice,
                json("id", id.toString(), "name", "Alice's private ritual", "subheadings", List.of("Secret prompt")));

        post(bob, json("id", id.toString(), "name", "Bob tries", "subheadings", List.of()))
                .expectStatus()
                .isEqualTo(409)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class)
                .value(body -> {
                    assertThat(JsonPath.<String>read(body, "$.type")).isEqualTo("urn:memoirist:problem:conflict");
                    assertThat(JsonPath.<Integer>read(body, "$.status")).isEqualTo(409);
                    // Nothing of Alice's, and not even the id that was sent.
                    assertThat(body)
                            .doesNotContain("Alice")
                            .doesNotContain("private")
                            .doesNotContain("Secret")
                            .doesNotContain(id.toString())
                            .doesNotContain(alice.getId().toString());
                });

        assertThat(countTemplates(bob)).isZero();
        assertThat(row(id.toString()))
                .containsEntry("user_id", alice.getId())
                .containsEntry("name", "Alice's private ritual");
    }

    @Test
    void aUserWhoIsNotThereIs404AndNothingIsCreated() {
        UUID nobody = UUID.randomUUID();
        long before =
                jdbc.sql("SELECT count(*) FROM templates").query(Long.class).single();

        client.post()
                .uri(TEMPLATES)
                .header(HEADER, nobody.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("name", "Ghost", "subheadings", List.of()))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(JsonPath.<String>read(body, "$.type")).isEqualTo("urn:memoirist:problem:not-found");
                    assertThat(body).doesNotContain(nobody.toString());
                });

        assertThat(jdbc.sql("SELECT count(*) FROM templates").query(Long.class).single())
                .isEqualTo(before);
    }

    // POST and PUT: the 400 rules and the limits

    @Test
    void postRejectsEachWrongFieldAndNamesIt() {
        User alice = aUser();

        assertThat(fieldErrorsOfPost(alice, json("name", "", "subheadings", List.of("ok", "  ", "ok"))))
                .containsOnly(
                        Map.entry("name", "size must be between 1 and 100"),
                        Map.entry("subheadings[1]", "size must be between 1 and 200"));
        assertThat(countTemplates(alice)).isZero();
    }

    @Test
    void theNameAndTheListAreRequiredAndAnEmptyBodyNamesBoth() {
        User alice = aUser();

        assertThat(fieldErrorsOfPost(alice, json()))
                .containsOnly(Map.entry("name", "must not be null"), Map.entry("subheadings", "must not be null"));
        assertThat(fieldErrorsOfPost(alice, json("name", "Only a name")))
                .containsOnly(Map.entry("subheadings", "must not be null"));
        assertThat(fieldErrorsOfPost(alice, json("subheadings", List.of())))
                .containsOnly(Map.entry("name", "must not be null"));
        // null is the same as leaving the field out.
        assertThat(fieldErrorsOfPost(alice, json("name", null, "subheadings", null)))
                .containsOnly(Map.entry("name", "must not be null"), Map.entry("subheadings", "must not be null"));
    }

    @Test
    void aNameOfOnlySpacesIsEmptyAfterStrippingAndIsRejected() {
        User alice = aUser();

        assertThat(fieldErrorsOfPost(alice, json("name", "     ", "subheadings", List.of())))
                .containsOnly(Map.entry("name", "size must be between 1 and 100"));
    }

    @Test
    void aNameOf100CharactersIsSavedAndOf101IsRejected() {
        User alice = aUser();

        post(alice, json("name", "n".repeat(100), "subheadings", List.of()))
                .expectStatus()
                .isCreated();
        assertThat(fieldErrorsOfPost(alice, json("name", "n".repeat(101), "subheadings", List.of())))
                .containsOnly(Map.entry("name", "size must be between 1 and 100"));
        assertThat(countTemplates(alice)).isEqualTo(1);
    }

    @Test
    void twentySubheadingsAreSavedAndTwentyOneAreRejected() {
        User alice = aUser();

        post(alice, json("name", "Full", "subheadings", prompts(20)))
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.subheadings.length()")
                .isEqualTo(20);
        assertThat(fieldErrorsOfPost(alice, json("name", "Too many", "subheadings", prompts(21))))
                .containsOnly(Map.entry("subheadings", "size must be between 0 and 20"));
        assertThat(countTemplates(alice)).isEqualTo(1);
    }

    @Test
    void aSubheadingOf200CharactersIsSavedAndOf201IsRejectedByItsPosition() {
        User alice = aUser();

        post(alice, json("name", "Long", "subheadings", List.of("short", "p".repeat(200))))
                .expectStatus()
                .isCreated();
        assertThat(fieldErrorsOfPost(alice, json("name", "Longer", "subheadings", List.of("short", "p".repeat(201)))))
                .containsOnly(Map.entry("subheadings[1]", "size must be between 1 and 200"));
    }

    @Test
    void aNullSubheadingIsRejectedByItsPositionNotAnInternalError() {
        User alice = aUser();

        // List.of() refuses a null, so the list is built by hand. On the wire it is ["first", null, "second"].
        List<String> withNull = new ArrayList<>(List.of("first", "second"));
        withNull.add(1, null);

        assertThat(fieldErrorsOfPost(alice, json("name", "Hole", "subheadings", withNull)))
                .containsOnly(Map.entry("subheadings[1]", "must not be null"));
    }

    @Test
    void everyWrongFieldIsNamedInOneResponseAndNothingTheClientSentComesBack() {
        User alice = aUser();
        List<String> bad = new ArrayList<>(prompts(21));
        bad.set(2, "my-secret-prompt " + "x".repeat(250));
        bad.set(5, " ");

        String body = post(alice, json("name", "", "subheadings", bad))
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(JsonPath.<Map<String, String>>read(body, "$.fieldErrors"))
                .containsOnly(
                        Map.entry("name", "size must be between 1 and 100"),
                        Map.entry("subheadings", "size must be between 0 and 20"),
                        Map.entry("subheadings[2]", "size must be between 1 and 200"),
                        Map.entry("subheadings[5]", "size must be between 1 and 200"));
        assertThat(JsonPath.<String>read(body, "$.detail")).doesNotContain("my-secret-prompt");
        assertThat(body).doesNotContain("my-secret-prompt");
    }

    @Test
    void putRejectsTheSameWrongFieldsAndLeavesTheTemplateAlone() {
        User alice = aUser();
        String id = createTemplate(alice, json("name", "Keep me", "subheadings", List.of("One", "Two")));
        OffsetDateTime updatedBefore = updatedAt(id);

        for (Map<String, Object> bad : List.of(
                json("name", "", "subheadings", List.of("One")),
                json("name", "Renamed", "subheadings", prompts(21)),
                json("name", "Renamed", "subheadings", List.of("fine", " ")),
                json("name", "Renamed"),
                json("subheadings", List.of("One")))) {
            put(alice, id, bad)
                    .expectStatus()
                    .isBadRequest()
                    .expectBody(String.class)
                    .value(body -> assertThat(JsonPath.<String>read(body, "$.type"))
                            .isEqualTo("urn:memoirist:problem:validation-failed"));
        }

        assertThat(row(id)).containsEntry("name", "Keep me");
        assertThat(JsonPath.<List<String>>read(subheadingsJson(id), "$")).containsExactly("One", "Two");
        assertThat(updatedAt(id)).isEqualTo(updatedBefore);
    }

    @Test
    void unreadableInputIs400MalformedRequestNotAValidationFailure() {
        User alice = aUser();

        for (String broken : List.of(
                "{",
                "[]",
                "{\"name\": \"x\", \"subheadings\": \"not a list\"}",
                "{\"name\": \"x\", \"subheadings\": [{\"a\": 1}]}",
                "{\"name\": \"x\", \"subheadings\": [], \"id\": \"not-a-uuid\"}")) {
            postRaw(alice, broken)
                    .expectStatus()
                    .isBadRequest()
                    .expectBody(String.class)
                    .value(body -> {
                        assertThat(JsonPath.<String>read(body, "$.type"))
                                .isEqualTo("urn:memoirist:problem:malformed-request");
                        assertThat(body).doesNotContain("not a list").doesNotContain("not-a-uuid");
                    });
        }
        assertThat(countTemplates(alice)).isZero();
    }

    @Test
    void aBodyThatIsNotJsonIs415() {
        User alice = aUser();

        client.post()
                .uri(TEMPLATES)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.TEXT_PLAIN)
                .body("name=Trip")
                .exchange()
                .expectStatus()
                .isEqualTo(415)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:unsupported-media-type");
    }

    // GET /templates

    @Test
    void theListIsTheUsersOwnTemplatesByNameIgnoringCaseAndAccents() {
        User alice = aUser();
        User bob = aUser();
        for (String name : List.of("banana", "Zucchini", "Apple", "Éclair", "cherry")) {
            createTemplate(alice, json("name", name, "subheadings", List.of("a")));
        }
        createTemplate(bob, json("name", "Bob's", "subheadings", List.of()));

        get(alice, TEMPLATES)
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .value(body -> {
                    assertThat(JsonPath.<List<String>>read(body, "$[*].name"))
                            .containsExactly("Apple", "banana", "cherry", "Éclair", "Zucchini");
                    // The same shape as one template.
                    assertThat(JsonPath.<Map<String, Object>>read(body, "$[0]").keySet())
                            .containsExactlyInAnyOrder("id", "name", "subheadings", "createdAt", "updatedAt");
                    assertThat(body).doesNotContain("Bob's");
                });
    }

    @Test
    void templatesWithTheSameNameAreListedOldestFirst() {
        User alice = aUser();
        String first = createTemplate(alice, json("name", "Trip day", "subheadings", List.of("first")));
        String second = createTemplate(alice, json("name", "Trip day", "subheadings", List.of("second")));
        String third = createTemplate(alice, json("name", "Trip day", "subheadings", List.of("third")));

        get(alice, TEMPLATES)
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body ->
                        assertThat(JsonPath.<List<String>>read(body, "$[*].id")).containsExactly(first, second, third));
    }

    @Test
    void aUserWithNoTemplatesGetsAnEmptyListAndSoDoesAUserWhoIsNotThere() {
        User alice = aUser();

        get(alice, TEMPLATES).expectStatus().isOk().expectBody().json("[]");
        client.get()
                .uri(TEMPLATES)
                .header(HEADER, UUID.randomUUID().toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("[]");
    }

    // GET /templates/{id}

    @Test
    void oneTemplateComesBackInTheSameShapeAsWhenItWasCreated() {
        User alice = aUser();
        Map<?, ?> made = post(alice, json("name", "Night out", "subheadings", List.of("Who came?", "Where next?")))
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        get(alice, TEMPLATES + "/" + made.get("id"))
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body).isEqualTo(made));
    }

    @Test
    void anotherUsersTemplateIs404OnEveryOperationAndStaysAsItWas() {
        User alice = aUser();
        User bob = aUser();
        String id = createTemplate(alice, json("name", "Alice's", "subheadings", List.of("Private prompt")));
        OffsetDateTime updatedBefore = updatedAt(id);

        for (RestTestClient.ResponseSpec response : List.of(
                get(bob, TEMPLATES + "/" + id),
                put(bob, id, json("name", "Bob's now", "subheadings", List.of())),
                delete(bob, id))) {
            response.expectStatus()
                    .isNotFound()
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody(String.class)
                    .value(body -> {
                        assertThat(JsonPath.<String>read(body, "$.type")).isEqualTo("urn:memoirist:problem:not-found");
                        // "instance" is the request path, which the client sent itself (RFC 9457); the rest says
                        // nothing.
                        assertThat(JsonPath.<String>read(body, "$.detail")).isEqualTo("Template not found.");
                        assertThat(body)
                                .doesNotContain("Alice")
                                .doesNotContain("Private prompt")
                                .doesNotContain(alice.getId().toString());
                    });
        }

        assertThat(row(id)).containsEntry("user_id", alice.getId()).containsEntry("name", "Alice's");
        assertThat(JsonPath.<List<String>>read(subheadingsJson(id), "$")).containsExactly("Private prompt");
        assertThat(updatedAt(id)).isEqualTo(updatedBefore);
        assertThat(countTemplates(bob)).isZero();
    }

    @Test
    void aTemplateThatDoesNotExistLooksExactlyLikeSomeoneElses() {
        User alice = aUser();
        String nothing = UUID.randomUUID().toString();

        get(alice, TEMPLATES + "/" + nothing).expectStatus().isNotFound();
        put(alice, nothing, json("name", "X", "subheadings", List.of()))
                .expectStatus()
                .isNotFound();
        delete(alice, nothing).expectStatus().isNotFound();
    }

    @Test
    void aUserWhoIsNotThereGets404FromEveryOperationThatNamesATemplate() {
        // Nothing here inserts a row, so no foreign key is at risk: the template just isn't found, like any other.
        String id = UUID.randomUUID().toString();
        String nobody = UUID.randomUUID().toString();

        client.get()
                .uri(TEMPLATES + "/" + id)
                .header(HEADER, nobody)
                .exchange()
                .expectStatus()
                .isNotFound();
        client.put()
                .uri(TEMPLATES + "/" + id)
                .header(HEADER, nobody)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("name", "X", "subheadings", List.of()))
                .exchange()
                .expectStatus()
                .isNotFound();
        client.delete()
                .uri(TEMPLATES + "/" + id)
                .header(HEADER, nobody)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void anIdThatIsNotAUuidIs400() {
        User alice = aUser();

        get(alice, TEMPLATES + "/not-a-uuid")
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(JsonPath.<String>read(body, "$.type"))
                            .isEqualTo("urn:memoirist:problem:malformed-request");
                    // "instance" is the request path, so only the detail is held to "nothing the client sent".
                    assertThat(JsonPath.<String>read(body, "$.detail")).doesNotContain("not-a-uuid");
                });
        delete(alice, "not-a-uuid").expectStatus().isBadRequest();
    }

    // PUT

    @Test
    void putReplacesTheNameAndTheWholeListAndKeepsTheIdAndTheCreationTime() {
        User alice = aUser();
        String id = createTemplate(alice, json("name", "Old name", "subheadings", List.of("A", "B", "C")));
        Map<String, Object> before = row(id);
        OffsetDateTime updatedBefore = updatedAt(id);

        put(alice, id, json("name", "New name", "subheadings", List.of("C", "Z")))
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body).containsEntry("id", id).containsEntry("name", "New name");
                    assertThat(body.get("subheadings")).isEqualTo(List.of("C", "Z"));
                    // The answer carries the new updatedAt, not the one from before the write.
                    assertThat(Instant.parse(body.get("updatedAt").toString())).isAfter(updatedBefore.toInstant());
                });

        Map<String, Object> after = row(id);
        assertThat(after)
                .containsEntry("name", "New name")
                .containsEntry("created_at", before.get("created_at"))
                .containsEntry("user_id", alice.getId());
        assertThat(JsonPath.<List<String>>read(subheadingsJson(id), "$")).containsExactly("C", "Z");
        assertThat(updatedAt(id)).as("updated_at moves on").isAfter(updatedBefore);
        assertThat(countTemplates(alice)).isEqualTo(1);
    }

    @Test
    void putCanEmptyTheListWithAnEmptyArray() {
        User alice = aUser();
        String id = createTemplate(alice, json("name", "Full", "subheadings", List.of("A", "B")));

        put(alice, id, json("name", "Full", "subheadings", List.of()))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.subheadings.length()")
                .isEqualTo(0);

        assertThat(subheadingsJson(id)).isEqualTo("[]");
    }

    @Test
    void putTrimsAndLimitsJustLikePost() {
        User alice = aUser();
        String id = createTemplate(alice, json("name", "Start", "subheadings", List.of()));

        put(alice, id, json("name", "  Padded  ", "subheadings", List.of(" a ", "b ")))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("Padded")
                .jsonPath("$.subheadings[0]")
                .isEqualTo("a")
                .jsonPath("$.subheadings[1]")
                .isEqualTo("b");
        put(alice, id, json("name", "n".repeat(100), "subheadings", prompts(20)))
                .expectStatus()
                .isOk();
        put(alice, id, json("name", "n".repeat(101), "subheadings", List.of()))
                .expectStatus()
                .isBadRequest();
        put(alice, id, json("name", "ok", "subheadings", List.of("p".repeat(201))))
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void anIdInAPutBodyIsIgnoredBecauseThePathNamesTheTemplate() {
        User alice = aUser();
        String id = createTemplate(alice, json("name", "Trip day", "subheadings", List.of()));
        UUID other = UUID.randomUUID();

        put(alice, id, json("id", other.toString(), "name", "Renamed", "subheadings", List.of()))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(id);

        assertThat(countTemplates(alice)).isEqualTo(1);
        assertThat(row(id)).containsEntry("name", "Renamed");
    }

    @Test
    void aPutThatChangesNothingRunsNoUpdate() {
        User alice = aUser();
        String id = createTemplate(alice, json("name", "Trip day", "subheadings", List.of("A", "B")));
        OffsetDateTime updatedBefore = updatedAt(id);

        put(alice, id, json("name", "Trip day", "subheadings", List.of("A", "B")))
                .expectStatus()
                .isOk();

        assertThat(updatedAt(id)).isEqualTo(updatedBefore);
    }

    @Test
    void entriesThatStartedFromATemplateAreUnaffectedWhenTheTemplateIsChanged() {
        User alice = aUser();
        String id = createTemplate(alice, json("name", "Trip day", "subheadings", List.of("Where?", "Who?")));
        WeeklyLog week = weeks.save(Fixtures.week(alice.getId()));
        UUID entryId = anEntryUsing(alice, week, id, "Day one", null, null);
        Map<String, Object> before = entryRow(entryId);

        put(alice, id, json("name", "Renamed", "subheadings", List.of("Completely", "Different", "Prompts")))
                .expectStatus()
                .isOk();

        // Every column of the entry is as it was, template_id included: it still names the (renamed) template.
        assertThat(entryRow(entryId)).isEqualTo(before);
        assertThat(entryRow(entryId)).containsEntry("template_id", UUID.fromString(id));
    }

    // DELETE

    @Test
    void deleteAnswers204AndTheTemplateIsGone() {
        User alice = aUser();
        String id = createTemplate(alice, json("name", "Trip day", "subheadings", List.of("A")));

        delete(alice, id).expectStatus().isNoContent().expectBody().isEmpty();

        get(alice, TEMPLATES + "/" + id).expectStatus().isNotFound();
        assertThat(countTemplates(alice)).isZero();
        // Deleting what is already gone is a 404, like any template that isn't there.
        delete(alice, id).expectStatus().isNotFound();
    }

    @Test
    void deleteTouchesOnlyTheNamedTemplate() {
        User alice = aUser();
        String keep = createTemplate(alice, json("name", "Keep", "subheadings", List.of("A")));
        String drop = createTemplate(alice, json("name", "Drop", "subheadings", List.of("B")));

        delete(alice, drop).expectStatus().isNoContent();

        get(alice, TEMPLATES)
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body ->
                        assertThat(JsonPath.<List<String>>read(body, "$[*].id")).containsExactly(keep));
    }

    @Test
    void deletingATemplateLeavesEveryEntryThatUsedItAndOnlyEmptiesItsTemplateId() {
        User alice = aUser();
        String id =
                createTemplate(alice, json("name", "Spiralling check-in", "subheadings", List.of("What happened?")));
        WeeklyLog week = weeks.save(Fixtures.week(alice.getId()));
        Instant now = Instant.now();
        UUID open = anEntryUsing(alice, week, id, "An open entry", null, null);
        UUID sealed = anEntryUsing(alice, week, id, "A sealed Loot Drop", now.plus(365, ChronoUnit.DAYS), null);
        UUID inBin = anEntryUsing(alice, week, id, "Waiting in Recently deleted", null, now.minus(2, ChronoUnit.DAYS));

        // TODO(human) PRACTICE: Prove that deleting the template changes nothing in the three entries except their
        //   template_id, which becomes NULL, and that all three entries are still there. Note which of the three the
        //   app's own queries would hide.
        // Hint: The reference is the database's ON DELETE SET NULL, not code in TemplateService (D-06, D-30).
        // Hint: Use JdbcClient to read rows, because @SQLRestriction hides the Recently deleted one from JPA.
        // Hint: Take each row before the delete and compare the whole row after, with only template_id replaced.
        // Hint: Comparing whole rows also covers a column nobody thought of.
        // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
        Map<UUID, Map<String, Object>> before = new HashMap<>();
        for (UUID entry : List.of(open, sealed, inBin)) {
            before.put(entry, entryRow(entry));
            assertThat(before.get(entry)).containsEntry("template_id", UUID.fromString(id));
        }

        delete(alice, id).expectStatus().isNoContent();

        assertThat(countTemplates(alice)).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM entries WHERE user_id = :id")
                        .param("id", alice.getId())
                        .query(Long.class)
                        .single())
                .as("the entries are all still there")
                .isEqualTo(3);
        before.forEach((entry, row) -> {
            Map<String, Object> expected = new HashMap<>(row);
            expected.put("template_id", null);
            assertThat(entryRow(entry)).as("entry %s", entry).isEqualTo(expected);
        });
        // └── END ANSWER ▲ ──
    }

    // The routes

    @Test
    void thereIsNoPatchOnATemplateAndTheAllowHeaderSaysWhatThereIs() {
        User alice = aUser();
        String id = createTemplate(alice, json("name", "Trip day", "subheadings", List.of()));

        client.patch()
                .uri(TEMPLATES + "/" + id)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("name", "X"))
                .exchange()
                .expectStatus()
                .isEqualTo(405)
                .expectHeader()
                .value(
                        "Allow",
                        allow -> assertThat(allow)
                                .contains("GET")
                                .contains("PUT")
                                .contains("DELETE"))
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:method-not-allowed");
    }

    // The API description

    @Test
    void theApiDescriptionListsAllFiveOperationsUnderTheTemplatesTagWithTheirErrors() {
        client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> {
                    for (String operation : List.of(
                            "$.paths['/api/v1/templates'].post",
                            "$.paths['/api/v1/templates'].get",
                            "$.paths['/api/v1/templates/{templateId}'].get",
                            "$.paths['/api/v1/templates/{templateId}'].put",
                            "$.paths['/api/v1/templates/{templateId}'].delete")) {
                        assertThat(JsonPath.<List<String>>read(body, operation + ".tags"))
                                .as(operation)
                                .containsExactly("Templates");
                    }
                    assertThat(responseCodes(body, "$.paths['/api/v1/templates'].post"))
                            .containsExactlyInAnyOrder("200", "201", "400", "404", "409", "415");
                    assertThat(responseCodes(body, "$.paths['/api/v1/templates'].get"))
                            .containsExactlyInAnyOrder("200", "400");
                    assertThat(responseCodes(body, "$.paths['/api/v1/templates/{templateId}'].get"))
                            .containsExactlyInAnyOrder("200", "400", "404");
                    assertThat(responseCodes(body, "$.paths['/api/v1/templates/{templateId}'].put"))
                            .containsExactlyInAnyOrder("200", "400", "404", "415");
                    assertThat(responseCodes(body, "$.paths['/api/v1/templates/{templateId}'].delete"))
                            .containsExactlyInAnyOrder("204", "400", "404");
                    // The limits written on the request records reach the schema, for the frontend's generated types.
                    assertThat(JsonPath.<Integer>read(
                                    body, "$.components.schemas.TemplateCreateRequest.properties.name.maxLength"))
                            .isEqualTo(100);
                    assertThat(JsonPath.<Integer>read(
                                    body, "$.components.schemas.TemplateUpdateRequest.properties.subheadings.maxItems"))
                            .isEqualTo(20);
                    assertThat(JsonPath.<Map<String, Object>>read(
                                            body, "$.components.schemas.TemplateResponse.properties")
                                    .keySet())
                            .containsExactlyInAnyOrder("id", "name", "subheadings", "createdAt", "updatedAt");
                });
    }

    // Helpers

    private static Iterable<String> responseCodes(String apiDocs, String operation) {
        Map<String, Object> responses = JsonPath.read(apiDocs, operation + ".responses");
        return responses.keySet();
    }

    /** Saves a new user; the test's cleanup deletes it. */
    private User aUser() {
        User user = users.save(Fixtures.user());
        created.add(user.getId());
        return user;
    }

    /** A JSON object from alternating keys and values. A map, not {@code Map.of}, so a value may be null. */
    private static Map<String, Object> json(Object... keysAndValues) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            body.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return body;
    }

    /** {@code count} distinct short prompts. */
    private static List<String> prompts(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "Prompt " + i).toList();
    }

    /** POSTs a template that must be accepted, and returns its id. */
    private String createTemplate(User user, Map<String, Object> body) {
        return post(user, body)
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("id")
                .toString();
    }

    private Map<String, String> fieldErrorsOfPost(User user, Map<String, Object> body) {
        String text = post(user, body)
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(JsonPath.<String>read(text, "$.type")).isEqualTo("urn:memoirist:problem:validation-failed");
        return JsonPath.<Map<String, String>>read(text, "$.fieldErrors");
    }

    private RestTestClient.ResponseSpec post(User user, Map<String, Object> body) {
        return client.post()
                .uri(TEMPLATES)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec postRaw(User user, String json) {
        return client.post()
                .uri(TEMPLATES)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange();
    }

    private RestTestClient.ResponseSpec get(User user, String uri) {
        return client.get().uri(uri).header(HEADER, user.getId().toString()).exchange();
    }

    private RestTestClient.ResponseSpec put(User user, String templateId, Map<String, Object> body) {
        return client.put()
                .uri(TEMPLATES + "/" + templateId)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec delete(User user, String templateId) {
        return client.delete()
                .uri(TEMPLATES + "/" + templateId)
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    /**
     * An entry that started from the template, written straight to the database through the repository: the entry
     * endpoints arrive in task 3.5. {@code unlockDate} makes it a sealed Loot Drop and {@code deletedAt} puts it in
     * Recently deleted.
     */
    private UUID anEntryUsing(
            User user, WeeklyLog week, String templateId, String title, Instant unlockDate, Instant deletedAt) {
        Entry entry = new Entry(UUID.randomUUID(), user.getId(), week.getId(), week.getStartDate());
        entry.setTemplateId(UUID.fromString(templateId));
        entry.setTitle(title);
        entry.setContentText("Written from the template: " + title);
        entry.setUnlockDate(unlockDate);
        entry.setDeletedAt(deletedAt);
        return entries.save(entry).getId();
    }

    /** The whole row, read with SQL so that an entry in Recently deleted (hidden from JPA) is seen too. */
    private Map<String, Object> entryRow(UUID entryId) {
        return jdbc.sql("SELECT * FROM entries WHERE id = :id")
                .param("id", entryId)
                .query()
                .singleRow();
    }

    private Map<String, Object> row(String templateId) {
        return jdbc.sql("SELECT * FROM templates WHERE id = :id")
                .param("id", UUID.fromString(templateId))
                .query()
                .singleRow();
    }

    /** The stored {@code subheadings} column as JSON text, to see what is really in the database. */
    private String subheadingsJson(String templateId) {
        return jdbc.sql("SELECT subheadings::text FROM templates WHERE id = :id")
                .param("id", UUID.fromString(templateId))
                .query(String.class)
                .single();
    }

    private String onlyTemplateId(User user) {
        return jdbc.sql("SELECT id FROM templates WHERE user_id = :id")
                .param("id", user.getId())
                .query(UUID.class)
                .single()
                .toString();
    }

    private OffsetDateTime updatedAt(String templateId) {
        return jdbc.sql("SELECT updated_at FROM templates WHERE id = :id")
                .param("id", UUID.fromString(templateId))
                .query(OffsetDateTime.class)
                .single();
    }

    private OffsetDateTime createdAt(String templateId) {
        return jdbc.sql("SELECT created_at FROM templates WHERE id = :id")
                .param("id", UUID.fromString(templateId))
                .query(OffsetDateTime.class)
                .single();
    }

    private long countTemplates(User user) {
        return jdbc.sql("SELECT count(*) FROM templates WHERE user_id = :id")
                .param("id", user.getId())
                .query(Long.class)
                .single();
    }
}

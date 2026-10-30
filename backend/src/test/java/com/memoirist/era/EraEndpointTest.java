package com.memoirist.era;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The five {@code /api/v1/eras} operations in the whole app: real Tomcat, real Postgres, real JSON. It checks each row
 * of API_CONTRACT (Eras) for the happy path, the {@code 400} rules, another user's Era, and the client-supplied id of
 * D-12. What an Era holds (weeks, counts, what a change of dates does) is in {@link EraWeeksTest}.
 *
 * <p>Every test makes its own users and sends their id in {@code X-Dev-User-Id}, and the users (with everything that
 * hangs off them) are deleted afterwards, so the seeded dev user and the other test classes that share this Postgres
 * container are left alone.
 */
@IntegrationTest
class EraEndpointTest {

    private static final String ERAS = "/api/v1/eras";
    private static final String HEADER = "X-Dev-User-Id";

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

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
    void postCreatesAnEraAndAnswers201WithExactlyTheContractFields() {
        User alice = aUser();

        post(
                        alice,
                        json(
                                "title", "Monash Double Degree Era",
                                "description", "Uni, mostly.",
                                "startDate", "2025-02-24",
                                "endDate", "2028-11-30",
                                "pinnedInFeed", true))
                .expectStatus()
                .isCreated()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(Map.class)
                .value(body -> {
                    // Exactly these keys: no soundtrack or cover until tasks 3.14 and 3.15, and no userId.
                    assertThat(body.keySet())
                            .containsExactlyInAnyOrder(
                                    "id",
                                    "title",
                                    "description",
                                    "startDate",
                                    "endDate",
                                    "pinnedInFeed",
                                    "createdAt",
                                    "updatedAt");
                    assertThat(UUID.fromString(body.get("id").toString())).isNotNull();
                    assertThat(body)
                            .containsEntry("title", "Monash Double Degree Era")
                            .containsEntry("description", "Uni, mostly.")
                            .containsEntry("startDate", "2025-02-24")
                            .containsEntry("endDate", "2028-11-30")
                            .containsEntry("pinnedInFeed", true);
                    // ISO-8601 instants, never numbers: parsing fails on anything else.
                    assertThat(Instant.parse(body.get("createdAt").toString())).isNotNull();
                    assertThat(Instant.parse(body.get("updatedAt").toString())).isNotNull();
                });

        assertThat(countEras(alice)).isEqualTo(1);
    }

    @Test
    void theRowIsOwnedByTheUserInTheHeaderAndHoldsTheValuesSent() {
        User alice = aUser();

        String id = createEra(alice, json("title", "Uni", "startDate", "2025-02-24", "endDate", "2028-11-30"));

        Map<String, Object> row = row(id);
        assertThat(row)
                .containsEntry("user_id", alice.getId())
                .containsEntry("title", "Uni")
                .containsEntry("pinned_in_feed", false)
                .containsEntry("description", null)
                .containsEntry("soundtrack", null)
                .containsEntry("cover_media_id", null);
        assertThat(row.get("start_date")).hasToString("2025-02-24");
        assertThat(row.get("end_date")).hasToString("2028-11-30");
    }

    @Test
    void aMinimalBodyGetsTheDefaults() {
        User alice = aUser();

        post(alice, json("title", "Uni", "startDate", "2025-02-24"))
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.description")
                .isEmpty()
                .jsonPath("$.endDate")
                .isEmpty()
                .jsonPath("$.pinnedInFeed")
                .isEqualTo(false);
    }

    @Test
    void explicitNullsAreTheSameAsLeavingTheFieldsOut() {
        User alice = aUser();

        postRaw(
                        alice,
                        "{\"title\":\"Uni\",\"startDate\":\"2025-02-24\",\"description\":null,\"endDate\":null,"
                                + "\"pinnedInFeed\":null,\"id\":null}")
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.pinnedInFeed")
                .isEqualTo(false)
                .jsonPath("$.endDate")
                .isEmpty();
    }

    @Test
    void theTitleAndDescriptionAreStrippedAndAnEmptyDescriptionIsSavedAsNothing() {
        User alice = aUser();

        post(alice, json("title", "  Uni Days  ", "description", "  Good times.  ", "startDate", "2025-02-24"))
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.title")
                .isEqualTo("Uni Days")
                .jsonPath("$.description")
                .isEqualTo("Good times.");

        post(alice, json("title", "Gap year", "description", "   ", "startDate", "2029-01-01"))
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.description")
                .isEmpty();
    }

    @Test
    void theServerPicksADifferentIdForEachEraWhenTheClientSendsNone() {
        User alice = aUser();

        String first = createEra(alice, json("title", "A", "startDate", "2026-01-01"));
        String second = createEra(alice, json("title", "A", "startDate", "2026-01-01"));

        assertThat(first).isNotEqualTo(second);
        assertThat(countEras(alice)).isEqualTo(2);
    }

    @Test
    void aSoundtrackAndACoverInTheBodyAreIgnoredUntilTheirTasks() {
        User alice = aUser();

        post(
                        alice,
                        json(
                                "title", "Uni",
                                "startDate", "2025-02-24",
                                "soundtrack", "spotify:track:1a2B3c4D5e6F7g8H9i0J1k",
                                "coverMediaId", UUID.randomUUID().toString()))
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .value(body -> assertThat(body.keySet()).doesNotContain("soundtrack", "cover", "coverMediaId"));

        Map<String, Object> row = jdbc.sql("SELECT soundtrack, cover_media_id FROM eras WHERE user_id = :id")
                .param("id", alice.getId())
                .query()
                .singleRow();
        assertThat(row).containsEntry("soundtrack", null).containsEntry("cover_media_id", null);
    }

    @Test
    void aUserIdOrTimestampsInTheBodyChangeNothing() {
        User alice = aUser();
        User bob = aUser();

        String id = createEra(
                alice,
                json(
                        "title", "Uni",
                        "startDate", "2025-02-24",
                        "userId", bob.getId().toString(),
                        "createdAt", "1999-01-01T00:00:00Z"));

        Map<String, Object> row = row(id);
        assertThat(row.get("user_id")).isEqualTo(alice.getId());
        assertThat(row.get("created_at").toString()).doesNotContain("1999");
        assertThat(countEras(bob)).isZero();
    }

    // POST: the client's own id (D-12)

    @Test
    void aClientSuppliedIdIsUsedAndAnswers201() {
        User alice = aUser();
        UUID id = UUID.randomUUID();

        post(alice, json("id", id.toString(), "title", "Uni", "startDate", "2025-02-24"))
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(id.toString());

        assertThat(row(id.toString())).containsEntry("user_id", alice.getId());
    }

    @Test
    void replayingTheSameIdAnswers200WithTheStoredEraAndAppliesNothing() {
        User alice = aUser();
        UUID id = UUID.randomUUID();
        createEra(alice, json("id", id.toString(), "title", "Uni", "startDate", "2025-02-24"));

        // The retry carries a different body. It is not applied: the answer is what is stored.
        post(
                        alice,
                        json(
                                "id",
                                id.toString(),
                                "title",
                                "A different title",
                                "startDate",
                                "2030-01-01",
                                "pinnedInFeed",
                                true))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(id.toString())
                .jsonPath("$.title")
                .isEqualTo("Uni")
                .jsonPath("$.startDate")
                .isEqualTo("2025-02-24")
                .jsonPath("$.pinnedInFeed")
                .isEqualTo(false);

        assertThat(countEras(alice)).isEqualTo(1);
        assertThat(row(id.toString())).containsEntry("title", "Uni");
    }

    @Test
    void anIdThatAnotherUserHasIs409ConflictAndRevealsNothing() {
        User alice = aUser();
        User bob = aUser();
        UUID id = UUID.randomUUID();
        createEra(alice, json("id", id.toString(), "title", "Alice's private chapter", "startDate", "2025-02-24"));

        post(bob, json("id", id.toString(), "title", "Bob tries", "startDate", "2026-01-01"))
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
                            .doesNotContain(id.toString())
                            .doesNotContain(alice.getId().toString());
                });

        assertThat(countEras(bob)).isZero();
        assertThat(row(id.toString()))
                .containsEntry("user_id", alice.getId())
                .containsEntry("title", "Alice's private chapter");
    }

    @Test
    void aUserWhoIsNotThereIs404AndNothingIsCreated() {
        UUID nobody = UUID.randomUUID();
        long before = jdbc.sql("SELECT count(*) FROM eras").query(Long.class).single();

        client.post()
                .uri(ERAS)
                .header(HEADER, nobody.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("title", "Ghost era", "startDate", "2025-02-24"))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(JsonPath.<String>read(body, "$.type")).isEqualTo("urn:memoirist:problem:not-found");
                    assertThat(body).doesNotContain(nobody.toString());
                });

        assertThat(jdbc.sql("SELECT count(*) FROM eras").query(Long.class).single())
                .isEqualTo(before);
    }

    // POST and PUT: the 400 rules

    @Test
    void postRejectsEachWrongFieldAndNamesIt() {
        User alice = aUser();

        Map<String, String> fieldErrors = fieldErrorsOfPost(
                alice, json("title", "   ", "description", "d".repeat(5001), "startDate", "1800-01-01"));

        assertThat(fieldErrors)
                .containsOnlyKeys("title", "description", "startDate")
                .containsEntry("title", "size must be between 1 and 150")
                .containsEntry("description", "size must be between 0 and 5000")
                .containsEntry("startDate", "must be between 1900-01-01 and 9999-12-31");
        assertThat(countEras(alice)).as("a rejected body saves nothing").isZero();
    }

    @Test
    void anEndBeforeTheStartIs400OnEndDate() {
        User alice = aUser();

        post(alice, json("title", "Backwards", "startDate", "2026-03-02", "endDate", "2026-03-01"))
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:validation-failed")
                .jsonPath("$.fieldErrors.endDate")
                .isEqualTo("must not be before startDate");

        assertThat(countEras(alice)).isZero();
    }

    @Test
    void aOneDayEraIsFine() {
        User alice = aUser();

        post(alice, json("title", "Gig", "startDate", "2026-03-01", "endDate", "2026-03-01"))
                .expectStatus()
                .isCreated();
    }

    @Test
    void theTitleAndTheStartDateAreRequired() {
        User alice = aUser();

        assertThat(fieldErrorsOfPost(alice, json("description", "No title or start")))
                .containsOnlyKeys("title", "startDate")
                .containsEntry("title", "must not be null")
                .containsEntry("startDate", "must not be null");
    }

    @Test
    void aTitleOf150CharactersIsSavedAndOf151IsRejected() {
        User alice = aUser();

        post(alice, json("title", "t".repeat(150), "startDate", "2026-01-01"))
                .expectStatus()
                .isCreated();
        assertThat(fieldErrorsOfPost(alice, json("title", "t".repeat(151), "startDate", "2026-01-01")))
                .containsOnlyKeys("title");
    }

    @Test
    void putRejectsTheSameWrongFieldsAndLeavesTheEraAlone() {
        User alice = aUser();
        String id = createEra(alice, json("title", "Uni", "startDate", "2025-02-24"));

        client.put()
                .uri(ERAS + "/" + id)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("title", "", "startDate", "2026-03-02", "endDate", "2026-03-01"))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.fieldErrors.title")
                .isEqualTo("size must be between 1 and 150")
                .jsonPath("$.fieldErrors.endDate")
                .isEqualTo("must not be before startDate");

        assertThat(row(id)).containsEntry("title", "Uni");
    }

    @Test
    void unreadableInputIs400MalformedRequestNotAValidationFailure() {
        User alice = aUser();

        for (String body : List.of(
                "{not json",
                "{\"title\":\"Uni\",\"startDate\":\"not-a-date\"}",
                "{\"title\":\"Uni\",\"startDate\":\"2026-13-45\"}",
                "{\"title\":\"Uni\",\"startDate\":\"2026-01-01\",\"id\":\"not-a-uuid\"}",
                "{\"title\":{\"nested\":1},\"startDate\":\"2026-01-01\"}")) {
            postRaw(alice, body)
                    .expectStatus()
                    .isBadRequest()
                    .expectBody(String.class)
                    .value(text -> {
                        assertThat(JsonPath.<String>read(text, "$.type"))
                                .isEqualTo("urn:memoirist:problem:malformed-request");
                        assertThat(text).doesNotContain("not-a-date").doesNotContain("not-a-uuid");
                    });
        }
        assertThat(countEras(alice)).isZero();
    }

    @Test
    void aBodyThatIsNotJsonIs415() {
        User alice = aUser();

        client.post()
                .uri(ERAS)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.TEXT_PLAIN)
                .body("title=Uni")
                .exchange()
                .expectStatus()
                .isEqualTo(415)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:unsupported-media-type");
    }

    // GET /eras

    @Test
    void theListIsTheUsersOwnErasWithTheLatestStartFirst() {
        User alice = aUser();
        User bob = aUser();
        String uni = createEra(alice, json("title", "Uni", "startDate", "2025-02-24"));
        String trip = createEra(alice, json("title", "Trip", "startDate", "2026-10-28", "endDate", "2026-10-30"));
        String school = createEra(alice, json("title", "School", "startDate", "2010-01-27", "endDate", "2022-12-16"));
        createEra(bob, json("title", "Bob's era", "startDate", "2026-01-01"));

        get(alice, ERAS)
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .value(body -> {
                    assertThat(JsonPath.<List<String>>read(body, "$[*].id")).containsExactly(trip, uni, school);
                    assertThat(JsonPath.<List<String>>read(body, "$[*].title")).doesNotContain("Bob's era");
                    // The list uses the same shape as one Era: no weeklyLogs.
                    assertThat(JsonPath.<Map<String, Object>>read(body, "$[0]").keySet())
                            .containsExactlyInAnyOrder(
                                    "id",
                                    "title",
                                    "description",
                                    "startDate",
                                    "endDate",
                                    "pinnedInFeed",
                                    "createdAt",
                                    "updatedAt");
                });
    }

    @Test
    void erasThatStartOnTheSameDayAreListedNewestCreatedFirst() {
        User alice = aUser();
        String older = createEra(alice, json("title", "First made", "startDate", "2026-01-01"));
        String newer = createEra(alice, json("title", "Second made", "startDate", "2026-01-01"));

        get(alice, ERAS)
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body ->
                        assertThat(JsonPath.<List<String>>read(body, "$[*].id")).containsExactly(newer, older));
    }

    @Test
    void aUserWithNoErasGetsAnEmptyListAndSoDoesAUserWhoIsNotThere() {
        User alice = aUser();

        get(alice, ERAS).expectStatus().isOk().expectBody().json("[]");
        client.get()
                .uri(ERAS)
                .header(HEADER, UUID.randomUUID().toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("[]");
    }

    // GET /eras/{id}

    @Test
    void oneEraComesWithAnEmptyWeekListWhenNoWeekOverlapsIt() {
        User alice = aUser();
        String id = createEra(alice, json("title", "Uni", "description", "Hi", "startDate", "2025-02-24"));

        get(alice, ERAS + "/" + id).expectStatus().isOk().expectBody(Map.class).value(body -> {
            assertThat(body.keySet())
                    .containsExactlyInAnyOrder(
                            "id",
                            "title",
                            "description",
                            "startDate",
                            "endDate",
                            "pinnedInFeed",
                            "createdAt",
                            "updatedAt",
                            "weeklyLogs");
            assertThat(body).containsEntry("id", id).containsEntry("weeklyLogs", List.of());
        });
    }

    @Test
    void anotherUsersEraIs404OnEveryOperationAndStaysAsItWas() {
        User alice = aUser();
        User bob = aUser();
        String id = createEra(alice, json("title", "Alice's", "startDate", "2025-02-24"));

        for (RestTestClient.ResponseSpec response : List.of(
                get(bob, ERAS + "/" + id),
                put(bob, id, json("title", "Bob's now", "startDate", "2026-01-01")),
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
                        assertThat(JsonPath.<String>read(body, "$.detail")).isEqualTo("Era not found.");
                        assertThat(body)
                                .doesNotContain("Alice")
                                .doesNotContain(alice.getId().toString());
                    });
        }

        assertThat(row(id)).containsEntry("user_id", alice.getId()).containsEntry("title", "Alice's");
        assertThat(countEras(bob)).isZero();
    }

    @Test
    void anEraThatDoesNotExistLooksExactlyLikeSomeoneElses() {
        User alice = aUser();
        String nothing = UUID.randomUUID().toString();

        get(alice, ERAS + "/" + nothing).expectStatus().isNotFound();
        put(alice, nothing, json("title", "X", "startDate", "2026-01-01"))
                .expectStatus()
                .isNotFound();
        delete(alice, nothing).expectStatus().isNotFound();
    }

    @Test
    void aUserWhoIsNotThereGets404FromEveryOperationThatNamesAnEra() {
        // Nothing here inserts a row, so no foreign key is at risk: the Era just isn't found, like any other.
        String id = UUID.randomUUID().toString();
        String nobody = UUID.randomUUID().toString();

        client.get()
                .uri(ERAS + "/" + id)
                .header(HEADER, nobody)
                .exchange()
                .expectStatus()
                .isNotFound();
        client.put()
                .uri(ERAS + "/" + id)
                .header(HEADER, nobody)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("title", "X", "startDate", "2026-01-01"))
                .exchange()
                .expectStatus()
                .isNotFound();
        client.delete()
                .uri(ERAS + "/" + id)
                .header(HEADER, nobody)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void anIdThatIsNotAUuidIs400() {
        User alice = aUser();

        get(alice, ERAS + "/not-a-uuid")
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
    void putReplacesEveryFieldAndKeepsTheIdAndTheCreationTime() {
        User alice = aUser();
        String id = createEra(
                alice, json("title", "Uni", "description", "Old", "startDate", "2025-02-24", "endDate", "2028-11-30"));
        Map<String, Object> before = row(id);
        OffsetDateTime updatedBefore = updatedAt(id);

        put(alice, id, json("title", "Monash", "description", "New", "startDate", "2025-03-01", "pinnedInFeed", true))
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(Map.class)
                .value(body -> assertThat(body)
                        .containsEntry("id", id)
                        .containsEntry("title", "Monash")
                        .containsEntry("description", "New")
                        .containsEntry("startDate", "2025-03-01")
                        .containsEntry("endDate", null)
                        .containsEntry("pinnedInFeed", true));

        Map<String, Object> after = row(id);
        assertThat(after)
                .containsEntry("title", "Monash")
                .containsEntry("description", "New")
                .containsEntry("end_date", null)
                .containsEntry("pinned_in_feed", true)
                .containsEntry("created_at", before.get("created_at"))
                .containsEntry("user_id", alice.getId());
        assertThat(after.get("start_date")).hasToString("2025-03-01");
        assertThat(updatedAt(id)).as("updated_at moves on").isAfter(updatedBefore);
        assertThat(countEras(alice)).isEqualTo(1);
    }

    @Test
    void putResetsTheOptionalFieldsThatAreLeftOut() {
        User alice = aUser();
        String id = createEra(
                alice,
                json(
                        "title", "Uni",
                        "description", "Old",
                        "startDate", "2025-02-24",
                        "endDate", "2028-11-30",
                        "pinnedInFeed", true));

        put(alice, id, json("title", "Uni", "startDate", "2025-02-24"))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.description")
                .isEmpty()
                .jsonPath("$.endDate")
                .isEmpty()
                .jsonPath("$.pinnedInFeed")
                .isEqualTo(false);
    }

    @Test
    void anIdInAPutBodyIsIgnoredBecauseThePathNamesTheEra() {
        User alice = aUser();
        String id = createEra(alice, json("title", "Uni", "startDate", "2025-02-24"));
        UUID other = UUID.randomUUID();

        put(alice, id, json("id", other.toString(), "title", "Renamed", "startDate", "2025-02-24"))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(id);

        assertThat(countEras(alice)).isEqualTo(1);
        assertThat(row(id)).containsEntry("title", "Renamed");
    }

    @Test
    void aPutThatChangesNothingRunsNoUpdate() {
        User alice = aUser();
        String id = createEra(alice, json("title", "Uni", "startDate", "2025-02-24"));
        OffsetDateTime updatedBefore = updatedAt(id);

        put(alice, id, json("title", "Uni", "startDate", "2025-02-24"))
                .expectStatus()
                .isOk();

        assertThat(updatedAt(id)).isEqualTo(updatedBefore);
    }

    // DELETE

    @Test
    void deleteAnswers204AndTheEraIsGone() {
        User alice = aUser();
        String id = createEra(alice, json("title", "Uni", "startDate", "2025-02-24"));

        delete(alice, id).expectStatus().isNoContent().expectBody().isEmpty();

        get(alice, ERAS + "/" + id).expectStatus().isNotFound();
        assertThat(countEras(alice)).isZero();
        // Deleting what is already gone is a 404, like any Era that isn't there.
        delete(alice, id).expectStatus().isNotFound();
    }

    @Test
    void deleteTouchesOnlyTheNamedEra() {
        User alice = aUser();
        String keep = createEra(alice, json("title", "Keep", "startDate", "2025-02-24"));
        String drop = createEra(alice, json("title", "Drop", "startDate", "2026-01-01"));

        delete(alice, drop).expectStatus().isNoContent();

        get(alice, ERAS)
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body ->
                        assertThat(JsonPath.<List<String>>read(body, "$[*].id")).containsExactly(keep));
    }

    // The routes

    @Test
    void thereIsNoPatchOnAnEraAndTheAllowHeaderSaysWhatThereIs() {
        User alice = aUser();
        String id = createEra(alice, json("title", "Uni", "startDate", "2025-02-24"));

        client.patch()
                .uri(ERAS + "/" + id)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("title", "X"))
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
    void theApiDescriptionListsAllFiveOperationsUnderTheErasTagWithTheirErrors() {
        client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> {
                    for (String operation : List.of(
                            "$.paths['/api/v1/eras'].post",
                            "$.paths['/api/v1/eras'].get",
                            "$.paths['/api/v1/eras/{eraId}'].get",
                            "$.paths['/api/v1/eras/{eraId}'].put",
                            "$.paths['/api/v1/eras/{eraId}'].delete")) {
                        assertThat(JsonPath.<List<String>>read(body, operation + ".tags"))
                                .as(operation)
                                .containsExactly("Eras");
                    }
                    assertThat(responseCodes(body, "$.paths['/api/v1/eras'].post"))
                            .containsExactlyInAnyOrder("200", "201", "400", "404", "409", "415");
                    assertThat(responseCodes(body, "$.paths['/api/v1/eras'].get"))
                            .containsExactlyInAnyOrder("200", "400");
                    assertThat(responseCodes(body, "$.paths['/api/v1/eras/{eraId}'].get"))
                            .containsExactlyInAnyOrder("200", "400", "404");
                    assertThat(responseCodes(body, "$.paths['/api/v1/eras/{eraId}'].put"))
                            .containsExactlyInAnyOrder("200", "400", "404", "415");
                    assertThat(responseCodes(body, "$.paths['/api/v1/eras/{eraId}'].delete"))
                            .containsExactlyInAnyOrder("204", "400", "404");
                    // The limits written on the request record reach the schema, for the frontend's generated types.
                    assertThat(JsonPath.<Integer>read(
                                    body, "$.components.schemas.EraCreateRequest.properties.title.maxLength"))
                            .isEqualTo(150);
                    assertThat(JsonPath.<Integer>read(
                                    body, "$.components.schemas.EraUpdateRequest.properties.description.maxLength"))
                            .isEqualTo(5000);
                    // The weekly log's flag is called isClosed in JSON, as the contract says.
                    assertThat(JsonPath.<Map<String, Object>>read(
                                            body, "$.components.schemas.EraWeekResponse.properties")
                                    .keySet())
                            .contains("isClosed")
                            .doesNotContain("closed");
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

    /** POSTs an Era that must be accepted, and returns its id. */
    private String createEra(User user, Map<String, Object> body) {
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
                .uri(ERAS)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec postRaw(User user, String json) {
        return client.post()
                .uri(ERAS)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange();
    }

    private RestTestClient.ResponseSpec get(User user, String uri) {
        return client.get().uri(uri).header(HEADER, user.getId().toString()).exchange();
    }

    private RestTestClient.ResponseSpec put(User user, String eraId, Map<String, Object> body) {
        return client.put()
                .uri(ERAS + "/" + eraId)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec delete(User user, String eraId) {
        return client.delete()
                .uri(ERAS + "/" + eraId)
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    private Map<String, Object> row(String eraId) {
        return jdbc.sql("SELECT * FROM eras WHERE id = :id")
                .param("id", UUID.fromString(eraId))
                .query()
                .singleRow();
    }

    private OffsetDateTime updatedAt(String eraId) {
        return jdbc.sql("SELECT updated_at FROM eras WHERE id = :id")
                .param("id", UUID.fromString(eraId))
                .query(OffsetDateTime.class)
                .single();
    }

    private long countEras(User user) {
        return jdbc.sql("SELECT count(*) FROM eras WHERE user_id = :id")
                .param("id", user.getId())
                .query(Long.class)
                .single();
    }
}

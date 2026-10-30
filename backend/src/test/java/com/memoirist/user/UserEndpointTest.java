package com.memoirist.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.SqlLog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * {@code GET} and {@code PATCH /api/v1/users/me} in the whole app: real Tomcat, real Postgres, real JSON. The unit
 * tests next door ({@link UserServiceTest}, {@link UserUpdateRequestTest}) check the decisions; this class checks that
 * they are wired together the way the contract (API_CONTRACT, Users) says.
 *
 * <p>Every test that changes a user makes its own, with a header naming it, so the seeded dev user stays as the seed
 * made it for the other test classes that share this Postgres container. The users are deleted afterwards.
 */
@IntegrationTest
class UserEndpointTest {

    private static final String ME = "/api/v1/users/me";
    private static final String HEADER = "X-Dev-User-Id";
    private static final String BAD_ZONE_MESSAGE = "must be an IANA time zone, such as Australia/Melbourne";

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

    // GET

    @Test
    void getAnswersWithExactlyTheContractFields() {
        User alice = aUser("Alice");

        client.get()
                .uri(ME)
                .header(HEADER, alice.getId().toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(Map.class)
                .value(body -> {
                    // Exactly these keys: no createdAt/updatedAt, and no birthday until task 3.13.
                    assertThat(body.keySet()).containsExactlyInAnyOrder("id", "email", "displayName", "timezone");
                    assertThat(body)
                            .containsEntry("id", alice.getId().toString())
                            .containsEntry("email", alice.getEmail())
                            .containsEntry("displayName", "Alice")
                            .containsEntry("timezone", "Australia/Melbourne");
                });
    }

    @Test
    void withoutAHeaderTheSeededDevUserAnswers() {
        client.get()
                .uri(ME)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(Fixtures.DEV_USER_ID.toString())
                .jsonPath("$.email")
                .isEqualTo("dev@memoirist.local");
    }

    @Test
    void eachUserSeesOnlyThemselves() {
        // There is no id in the URL to tamper with, so ownership is "whose header". Two users, two answers.
        User alice = aUser("Alice");
        User bob = aUser("Bob");

        assertThat(displayNameOf(alice.getId())).isEqualTo("Alice");
        assertThat(displayNameOf(bob.getId())).isEqualTo("Bob");
    }

    // The decision of task 3.2: a well-formed id that names nobody

    @Test
    void aUserWhoIsNotThereIs404OnGet() {
        UUID nobody = UUID.randomUUID();

        client.get()
                .uri(ME)
                .header(HEADER, nobody.toString())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class)
                .value(body -> {
                    assertThat(JsonPath.<String>read(body, "$.type")).isEqualTo("urn:memoirist:problem:not-found");
                    assertThat(JsonPath.<String>read(body, "$.detail")).isEqualTo("User not found.");
                    assertThat(body).doesNotContain(nobody.toString());
                });
    }

    @Test
    void aUserWhoIsNotThereIs404OnPatchAndNothingIsCreated() {
        UUID nobody = UUID.randomUUID();
        long before = countUsers();

        patch(nobody, Map.of("displayName", "Ghost"))
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:not-found");

        assertThat(countUsers()).isEqualTo(before);
    }

    @Test
    void anInvalidBodyForAUserWhoIsNotThereIs400BecauseTheBodyIsCheckedFirst() {
        // Validation runs before the controller, so it comes first. It leaks nothing: the 400 doesn't depend on whether
        // the user exists.
        patch(UUID.randomUUID(), Map.of("timezone", "Mars/Olympus_Mons"))
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:validation-failed");
    }

    // The header over real HTTP (task 2.5 tested these only through MockMvc)

    @Test
    void aHeaderThatIsNotAUuidIs400MalformedRequest() {
        client.get()
                .uri(ME)
                .header(HEADER, "not-a-uuid")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(JsonPath.<String>read(body, "$.type"))
                            .isEqualTo("urn:memoirist:problem:malformed-request");
                    assertThat(body).doesNotContain("not-a-uuid");
                });
    }

    @Test
    void anEmptyHeaderIs400NotTheDevUser() {
        client.get()
                .uri(ME)
                .header(HEADER, "")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:malformed-request");
    }

    @Test
    void aHeaderSentTwiceIs400() {
        client.get()
                .uri(ME)
                .header(
                        HEADER,
                        Fixtures.DEV_USER_ID.toString(),
                        UUID.randomUUID().toString())
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:malformed-request");
    }

    // PATCH: what changes

    @Test
    void patchChangesTheNameAndLeavesTheZone() {
        User alice = aUser("Alice");

        patch(alice.getId(), Map.of("displayName", "Alice Smith"))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(alice.getId().toString())
                .jsonPath("$.displayName")
                .isEqualTo("Alice Smith")
                .jsonPath("$.timezone")
                .isEqualTo("Australia/Melbourne");

        assertThat(row(alice.getId()))
                .containsEntry("display_name", "Alice Smith")
                .containsEntry("timezone", "Australia/Melbourne");
        assertThat(displayNameOf(alice.getId())).isEqualTo("Alice Smith");
    }

    @Test
    void patchChangesTheZoneAndLeavesTheName() {
        User alice = aUser("Alice");

        patch(alice.getId(), Map.of("timezone", "America/New_York"))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.timezone")
                .isEqualTo("America/New_York")
                .jsonPath("$.displayName")
                .isEqualTo("Alice");

        assertThat(row(alice.getId()))
                .containsEntry("timezone", "America/New_York")
                .containsEntry("display_name", "Alice");
    }

    @Test
    void patchCanChangeBothAtOnce() {
        User alice = aUser("Alice");

        patch(alice.getId(), Map.of("displayName", "Al", "timezone", "Asia/Kolkata"))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.displayName")
                .isEqualTo("Al")
                .jsonPath("$.timezone")
                .isEqualTo("Asia/Kolkata");

        assertThat(row(alice.getId())).containsEntry("display_name", "Al").containsEntry("timezone", "Asia/Kolkata");
    }

    @ParameterizedTest(name = "{0} is accepted as a zone")
    @ValueSource(strings = {"Australia/Melbourne", "America/New_York", "Europe/London", "UTC", "Etc/GMT+10"})
    void validZonesAreSaved(String zone) {
        User alice = aUser("Alice");

        patch(alice.getId(), Map.of("timezone", zone))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.timezone")
                .isEqualTo(zone);
    }

    @Test
    void theNameIsStrippedBeforeItIsSaved() {
        User alice = aUser("Alice");

        patch(alice.getId(), Map.of("displayName", "  Sam  "))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.displayName")
                .isEqualTo("Sam");

        assertThat(row(alice.getId())).containsEntry("display_name", "Sam");
    }

    @Test
    void aNameOfExactlyAHundredCharactersIsSaved() {
        User alice = aUser("Alice");
        String hundred = "n".repeat(100);

        patch(alice.getId(), Map.of("displayName", hundred))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.displayName")
                .isEqualTo(hundred);
    }

    @Test
    void anEmptyBodyObjectChangesNothingAndRunsNoUpdate() {
        User alice = aUser("Alice");

        try (SqlLog sql = SqlLog.capture()) {
            patch(alice.getId(), Map.of())
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.displayName")
                    .isEqualTo("Alice");

            // One SELECT to load the user. Hibernate saw no change, so it wrote nothing: that is dirty checking.
            assertThat(sql.statementsStartingWith("select")).hasSize(1);
            assertThat(sql.statementsStartingWith("update")).isEmpty();
        }
    }

    @Test
    void explicitNullsMeanNoChange() {
        User alice = aUser("Alice");

        patchRaw(alice.getId(), "{\"displayName\":null,\"timezone\":null}")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.displayName")
                .isEqualTo("Alice")
                .jsonPath("$.timezone")
                .isEqualTo("Australia/Melbourne");
    }

    @Test
    void aRealChangeRunsExactlyOneUpdate() {
        User alice = aUser("Alice");

        try (SqlLog sql = SqlLog.capture()) {
            patch(alice.getId(), Map.of("displayName", "Alice Smith", "timezone", "UTC"))
                    .expectStatus()
                    .isOk();

            // Two fields, one statement, and no save() call anywhere in the service.
            assertThat(sql.statementsStartingWith("update users")).hasSize(1);
        }
    }

    @Test
    void patchAsBobNeverTouchesAlice() {
        User alice = aUser("Alice");
        User bob = aUser("Bob");

        patch(bob.getId(), Map.of("displayName", "Robert", "timezone", "UTC"))
                .expectStatus()
                .isOk();

        assertThat(row(alice.getId()))
                .containsEntry("display_name", "Alice")
                .containsEntry("timezone", "Australia/Melbourne");
        assertThat(row(bob.getId())).containsEntry("display_name", "Robert").containsEntry("timezone", "UTC");
    }

    // PATCH: what is refused

    @ParameterizedTest(name = "a displayName of \"{0}\" is 400")
    @ValueSource(strings = {"", " ", "      ", "\t\n"})
    void aBlankNameIs400(String name) {
        User alice = aUser("Alice");

        patch(alice.getId(), Map.of("displayName", name))
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:validation-failed")
                .jsonPath("$.status")
                .isEqualTo(400)
                .jsonPath("$.instance")
                .isEqualTo(ME)
                .jsonPath("$.fieldErrors.displayName")
                .isEqualTo("size must be between 1 and 100")
                .jsonPath("$.timestamp")
                .exists();

        assertThat(row(alice.getId())).containsEntry("display_name", "Alice");
    }

    @Test
    void aNameOfAHundredAndOneCharactersIs400AndIsNotRepeated() {
        User alice = aUser("Alice");
        String tooLong = "n".repeat(101);

        patch(alice.getId(), Map.of("displayName", tooLong))
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(JsonPath.<String>read(body, "$.fieldErrors.displayName"))
                            .isEqualTo("size must be between 1 and 100");
                    assertThat(body).doesNotContain(tooLong);
                });

        assertThat(row(alice.getId())).containsEntry("display_name", "Alice");
    }

    @ParameterizedTest(name = "the zone \"{0}\" is 400")
    @ValueSource(
            strings = {
                "Mars/Olympus_Mons",
                "Melbourne",
                "australia/melbourne",
                " Australia/Melbourne",
                "+10:00",
                "UTC+10",
                "Z",
                ""
            })
    void anUnknownZoneIs400AndNothingChanges(String zone) {
        User alice = aUser("Alice");

        patch(alice.getId(), Map.of("timezone", zone))
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(JsonPath.<String>read(body, "$.type"))
                            .isEqualTo("urn:memoirist:problem:validation-failed");
                    // The response holds exactly our two fixed sentences, so the rejected text can't be in it. (A
                    // doesNotContain check on the body would fail for "Z" and "Melbourne": the timestamp and our own
                    // message contain them.)
                    assertThat(JsonPath.<Map<String, String>>read(body, "$.fieldErrors"))
                            .isEqualTo(Map.of("timezone", BAD_ZONE_MESSAGE));
                    assertThat(JsonPath.<String>read(body, "$.detail")).isEqualTo("timezone " + BAD_ZONE_MESSAGE);
                });

        assertThat(row(alice.getId())).containsEntry("timezone", "Australia/Melbourne");
    }

    @Test
    void twoWrongFieldsAreBothReportedInOneResponse() {
        User alice = aUser("Alice");

        patch(alice.getId(), Map.of("displayName", "  ", "timezone", "Mars/Olympus_Mons"))
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.fieldErrors.displayName")
                .isEqualTo("size must be between 1 and 100")
                .jsonPath("$.fieldErrors.timezone")
                .isEqualTo(BAD_ZONE_MESSAGE);
    }

    @Test
    void aGoodNameWithABadZoneSavesNeither() {
        // All or nothing: the body is refused before the service runs, so the valid half is not applied.
        User alice = aUser("Alice");

        patch(alice.getId(), Map.of("displayName", "Alice Smith", "timezone", "Mars/Olympus_Mons"))
                .expectStatus()
                .isBadRequest();

        assertThat(row(alice.getId()))
                .containsEntry("display_name", "Alice")
                .containsEntry("timezone", "Australia/Melbourne");
    }

    @Test
    void brokenJsonIs400MalformedRequest() {
        User alice = aUser("Alice");

        patchRaw(alice.getId(), "{\"displayName\": ")
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:malformed-request");
    }

    @Test
    void anArrayInsteadOfAnObjectIs400MalformedRequest() {
        User alice = aUser("Alice");

        patchRaw(alice.getId(), "[\"displayName\"]")
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:malformed-request");
    }

    @Test
    void noBodyAtAllIs400MalformedRequest() {
        User alice = aUser("Alice");

        client.patch()
                .uri(ME)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:malformed-request");
    }

    @Test
    void aBodyThatIsNotJsonIs415() {
        User alice = aUser("Alice");

        client.patch()
                .uri(ME)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.TEXT_PLAIN)
                .body("displayName=Sam")
                .exchange()
                .expectStatus()
                .isEqualTo(415)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:unsupported-media-type");
    }

    @Test
    void thereIsNoPutOrDeleteOnMe() {
        // PATCH is the only way to change the user (api.md), and deleting an account is Phase 2.
        User alice = aUser("Alice");

        client.put()
                .uri(ME)
                .header(HEADER, alice.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("displayName", "X"))
                .exchange()
                .expectStatus()
                .isEqualTo(405)
                .expectHeader()
                .value("Allow", allow -> assertThat(allow).contains("GET").contains("PATCH"))
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("urn:memoirist:problem:method-not-allowed");

        client.delete()
                .uri(ME)
                .header(HEADER, alice.getId().toString())
                .exchange()
                .expectStatus()
                .isEqualTo(405);
    }

    @Test
    void thereIsNoUsersByIdRoute() {
        // No endpoint takes a user id (D-11), so another user's id in the path is simply a path that doesn't exist.
        User bob = aUser("Bob");

        client.get()
                .uri("/api/v1/users/" + bob.getId())
                .exchange()
                .expectStatus()
                .isNotFound();
        client.get().uri("/api/v1/users").exchange().expectStatus().isNotFound();
    }

    // PATCH: the body can't reach anything else

    @Test
    void aPatchCannotChangeAnythingButTheTwoFields() {
        // TODO(human) PRACTICE: Prove the endpoint is safe against "mass assignment": a client adds fields the
        //   contract doesn't list ("email", "id", "createdAt", "birthday") next to a legal one. Send them, then check
        //   in the database that only the legal field changed, and that the response still has the same four keys.
        // Hint: Jackson skips JSON properties that the request record doesn't have, so the check is about what the
        //   record leaves out. Read the row back with JdbcClient rather than trusting the response, and use a second
        //   user's email as one of the values: that would also hit the UNIQUE constraint if it were applied.
        // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
        User alice = aUser("Alice");
        User bob = aUser("Bob");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", "Alice Smith");
        body.put("email", bob.getEmail());
        body.put("id", UUID.randomUUID().toString());
        body.put("createdAt", "1999-01-01T00:00:00Z");
        body.put("birthday", "1990-05-17");
        Map<String, Object> before = row(alice.getId());

        patch(alice.getId(), body).expectStatus().isOk().expectBody(Map.class).value(response -> {
            assertThat(response.keySet()).containsExactlyInAnyOrder("id", "email", "displayName", "timezone");
            assertThat(response)
                    .containsEntry("id", alice.getId().toString())
                    .containsEntry("email", alice.getEmail())
                    .containsEntry("displayName", "Alice Smith");
        });

        Map<String, Object> saved = row(alice.getId());
        assertThat(saved)
                .containsEntry("email", alice.getEmail())
                .containsEntry("display_name", "Alice Smith")
                .containsEntry("birthday", null)
                .containsEntry("created_at", before.get("created_at"));
        assertThat(jdbc.sql("SELECT count(*) FROM users WHERE id = :id")
                        .param("id", alice.getId())
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
        // └── END ANSWER ▲ ──
    }

    // The API description

    @Test
    void theApiDescriptionListsBothOperationsUnderTheUsersTag() {
        client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(JsonPath.<List<String>>read(body, "$.paths['/api/v1/users/me'].get.tags"))
                            .containsExactly("Users");
                    assertThat(JsonPath.<List<String>>read(body, "$.paths['/api/v1/users/me'].patch.tags"))
                            .containsExactly("Users");
                    Map<String, Object> getResponses = JsonPath.read(body, "$.paths['/api/v1/users/me'].get.responses");
                    Map<String, Object> patchResponses =
                            JsonPath.read(body, "$.paths['/api/v1/users/me'].patch.responses");
                    assertThat(getResponses.keySet()).containsExactlyInAnyOrder("200", "400", "404");
                    assertThat(patchResponses.keySet()).containsExactlyInAnyOrder("200", "400", "404", "415");
                    // The limits written on the request record reach the schema, for the frontend's generated types.
                    assertThat(JsonPath.<Integer>read(
                                    body, "$.components.schemas.UserUpdateRequest.properties.displayName.maxLength"))
                            .isEqualTo(100);
                    assertThat(JsonPath.<Integer>read(
                                    body, "$.components.schemas.UserUpdateRequest.properties.displayName.minLength"))
                            .isEqualTo(1);
                });
    }

    // Helpers

    /** Saves a new user whose name is {@code displayName}; the test's cleanup deletes it. */
    private User aUser(String displayName) {
        User user = Fixtures.user();
        user.setDisplayName(displayName);
        users.save(user);
        created.add(user.getId());
        return user;
    }

    private String displayNameOf(UUID userId) {
        return client.get()
                .uri(ME)
                .header(HEADER, userId.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("displayName")
                .toString();
    }

    private RestTestClient.ResponseSpec patch(UUID userId, Map<String, ?> body) {
        return client.patch()
                .uri(ME)
                .header(HEADER, userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec patchRaw(UUID userId, String json) {
        return client.patch()
                .uri(ME)
                .header(HEADER, userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange();
    }

    private Map<String, Object> row(UUID userId) {
        return jdbc.sql("SELECT email, display_name, timezone, birthday, created_at FROM users WHERE id = :id")
                .param("id", userId)
                .query()
                .singleRow();
    }

    private long countUsers() {
        return jdbc.sql("SELECT count(*) FROM users").query(Long.class).single();
    }
}

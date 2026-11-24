package com.memoirist.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.entry.RecentlyDeletedApiTest;
import com.memoirist.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The last check of Sprint 3 (task 3.17): {@code /v3/api-docs} says what {@code docs/API_CONTRACT.md} says, and what
 * the API really sends is what {@code /v3/api-docs} says. The frontend's types (task 4.2) are generated from the
 * document, so a field it calls optional, or an error it describes as a {@code TagResponse}, becomes a bug there.
 *
 * <p>Two kinds of check. The first reads the document alone: which endpoints exist, their tags, their documented
 * errors and content types. The second calls the API for every kind of answer it has (an open entry with photos, tags
 * and a soundtrack, a sealed Loot Drop, an opened one with a reply, an Era with a cover, ...) and walks each body
 * against the schema the document names for it: every field present, none extra, and a {@code null} only where the
 * schema allows one.
 */
class ApiDocsContractTest extends RecentlyDeletedApiTest {

    /** Every endpoint of API_CONTRACT's Phase 1 tables, and nothing else (the 405 and 406 answers are global). */
    private static final List<String> CONTRACT_ENDPOINTS = List.of(
            "GET /api/v1/users/me",
            "PATCH /api/v1/users/me",
            "POST /api/v1/eras",
            "GET /api/v1/eras",
            "GET /api/v1/eras/{eraId}",
            "PUT /api/v1/eras/{eraId}",
            "DELETE /api/v1/eras/{eraId}",
            "POST /api/v1/templates",
            "GET /api/v1/templates",
            "GET /api/v1/templates/{templateId}",
            "PUT /api/v1/templates/{templateId}",
            "DELETE /api/v1/templates/{templateId}",
            "POST /api/v1/tags",
            "GET /api/v1/tags",
            "PUT /api/v1/tags/{tagId}",
            "DELETE /api/v1/tags/{tagId}",
            "GET /api/v1/feed",
            "GET /api/v1/weekly-logs/{logId}",
            "PUT /api/v1/weekly-logs/{logId}",
            "POST /api/v1/weekly-logs/{logId}/close",
            "POST /api/v1/weekly-logs/{logId}/reopen",
            "POST /api/v1/entries",
            "GET /api/v1/entries",
            "GET /api/v1/entries/opened",
            "GET /api/v1/entries/deleted",
            "GET /api/v1/entries/on-this-day",
            "GET /api/v1/entries/{entryId}",
            "PUT /api/v1/entries/{entryId}",
            "DELETE /api/v1/entries/{entryId}",
            "POST /api/v1/entries/{entryId}/restore",
            "POST /api/v1/entries/{entryId}/erase",
            "POST /api/v1/entries/{entryId}/seal",
            "POST /api/v1/entries/{entryId}/mark-seen",
            "POST /api/v1/entries/{entryId}/media",
            "PUT /api/v1/entries/{entryId}/media/order",
            "DELETE /api/v1/media/{mediaId}",
            "POST /api/v1/music/search");

    /** The tag each path prefix must sit under (API_CONTRACT: one OpenAPI tag per resource). */
    private static final Map<String, String> TAG_OF_PREFIX = Map.of(
            "/api/v1/users", "Users",
            "/api/v1/eras", "Eras",
            "/api/v1/templates", "Templates",
            "/api/v1/tags", "Tags",
            "/api/v1/feed", "Feed",
            "/api/v1/weekly-logs", "Weekly Logs",
            "/api/v1/media", "Media",
            "/api/v1/music", "Music");

    private static final List<String> METHODS = List.of("get", "put", "post", "delete", "patch");

    // The document

    private Map<String, Object> docs() {
        RestTestClient.ResponseSpec response = client.get().uri("/v3/api-docs").exchange();
        response.expectStatus().isOk();
        return JsonPath.read(bodyText(response), "$");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object node) {
        return (Map<String, Object>) node;
    }

    /** Every operation as {@code "GET /api/v1/..."} with its JSON object. */
    private static Map<String, Map<String, Object>> operations(Map<String, Object> docs) {
        Map<String, Map<String, Object>> operations = new TreeMap<>();
        map(docs.get("paths"))
                .forEach((path, item) -> map(item).forEach((method, operation) -> {
                    if (METHODS.contains(method)) {
                        operations.put(method.toUpperCase() + " " + path, map(operation));
                    }
                }));
        return operations;
    }

    private static String tagFor(String path) {
        return path.startsWith("/api/v1/entries/") && path.endsWith("/media") || path.contains("/media/order")
                ? "Media"
                : TAG_OF_PREFIX.entrySet().stream()
                        .filter(prefix -> path.startsWith(prefix.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse("Entries");
    }

    // The document alone

    @Test
    void theDocumentHasExactlyTheEndpointsOfTheContract() {
        assertThat(operations(docs()).keySet()).containsExactlyInAnyOrderElementsOf(CONTRACT_ENDPOINTS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyEndpointHasItsResourcesTagAndASummary() {
        Map<String, Object> docs = docs();

        operations(docs).forEach((name, operation) -> {
            String path = name.substring(name.indexOf(' ') + 1);
            assertThat(operation.get("tags")).as(name + " tags").isEqualTo(List.of(tagFor(path)));
            assertThat((String) operation.get("summary")).as(name + " summary").isNotBlank();
        });
        assertThat(((List<Map<String, Object>>) docs.get("tags")).stream().map(tag -> tag.get("name")))
                .as("the tags, in the contract's order")
                .containsExactly(
                        "Users", "Eras", "Templates", "Tags", "Feed", "Weekly Logs", "Entries", "Media", "Music");
    }

    @Test
    void everyEndpointDocumentsItsErrors() {
        operations(docs()).forEach((name, operation) -> {
            Map<String, Object> responses = map(operation.get("responses"));
            assertThat(responses.keySet()).as(name).anyMatch(code -> code.startsWith("2"));
            // The X-Dev-User-Id header can be malformed on any call, so every call has a 400.
            assertThat(responses).as(name).containsKey("400");
            if (name.contains("{")) {
                assertThat(responses).as(name + " names a resource").containsKey("404");
            }
            Map<String, Object> body = map(operation.getOrDefault("requestBody", Map.of()));
            if (map(body.getOrDefault("content", Map.of())).containsKey("application/json")) {
                assertThat(responses).as(name + " reads JSON").containsKey("415");
            }
            responses.forEach(
                    (code, response) -> assertThat((String) map(response).get("description"))
                            .as(name + " " + code)
                            .isNotBlank());
        });
    }

    @Test
    void everyErrorIsAProblemAndEverySuccessIsJson() {
        operations(docs())
                .forEach((name, operation) -> map(operation.get("responses")).forEach((code, response) -> {
                    Map<String, Object> content = map(map(response).getOrDefault("content", Map.of()));
                    if (code.startsWith("4") || code.startsWith("5")) {
                        assertThat(content.keySet()).as(name + " " + code).containsExactly("application/problem+json");
                        assertThat(map(map(content.get("application/problem+json"))
                                        .get("schema")))
                                .as(name + " " + code)
                                .containsEntry("$ref", "#/components/schemas/Problem");
                    } else {
                        assertThat(content.keySet()).as(name + " " + code).isSubsetOf("application/json");
                    }
                }));
    }

    @Test
    void theProblemSchemaHasTheFieldsOfTheErrorBody() {
        Map<String, Object> problem =
                map(map(map(docs().get("components")).get("schemas")).get("Problem"));

        assertThat(map(problem.get("properties")).keySet())
                .containsExactlyInAnyOrder("type", "title", "status", "detail", "instance", "timestamp", "fieldErrors");
        assertThat(problem.get("required"))
                .isEqualTo(List.of("detail", "instance", "status", "timestamp", "title", "type"));
    }

    @Test
    void aCaptionIsATipTapDocumentNotJacksonsJsonNode() {
        Map<String, Object> schemas = map(map(docs().get("components")).get("schemas"));

        assertThat(schemas).doesNotContainKey("JsonNode").containsKey("TipTapDocument");
        for (String schema : List.of("EntryResponse", "EntryCreateRequest", "EntryUpdateRequest")) {
            assertThat(map(map(map(schemas.get(schema)).get("properties")).get("contentJson"))
                            .get("oneOf"))
                    .as(schema)
                    .isEqualTo(List.of(Map.of("$ref", "#/components/schemas/TipTapDocument"), Map.of("type", "null")));
        }
    }

    @Test
    void everyFieldOfAResponseIsRequiredAndTheNullableOnesSayTheyAre() {
        Map<String, Object> schemas = map(map(docs().get("components")).get("schemas"));
        List<String> responseSchemas = schemas.keySet().stream()
                .filter(name -> name.endsWith("Response") || name.equals("MusicResult"))
                .toList();

        assertThat(responseSchemas).hasSizeGreaterThanOrEqualTo(15);
        for (String name : responseSchemas) {
            Map<String, Object> schema = map(schemas.get(name));
            assertThat(new TreeSet<>(castList(schema.get("required"))))
                    .as(name)
                    .isEqualTo(new TreeSet<>(map(schema.get("properties")).keySet()));
        }
        // The contract's examples: a sealed Loot Drop hides these, an ongoing Era has no end.
        assertThat(allowsNull(map(properties(schemas, "EntryResponse").get("title")), schemas))
                .isTrue();
        assertThat(allowsNull(map(properties(schemas, "EntryResponse").get("unlockDate")), schemas))
                .isTrue();
        assertThat(allowsNull(map(properties(schemas, "EntryResponse").get("id")), schemas))
                .isFalse();
        assertThat(allowsNull(map(properties(schemas, "EraResponse").get("cover")), schemas))
                .isTrue();
        assertThat(allowsNull(map(properties(schemas, "EraResponse").get("endDate")), schemas))
                .isTrue();
        assertThat(allowsNull(map(properties(schemas, "UserResponse").get("birthday")), schemas))
                .isTrue();
    }

    private static Map<String, Object> properties(Map<String, Object> schemas, String name) {
        return map(map(schemas.get(name)).get("properties"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object list) {
        return (List<String>) list;
    }

    // What the API sends against what the document says

    /** The schema the document names for the answer to {@code method path}, as the {@code status} it documents. */
    private static Map<String, Object> answerSchema(
            Map<String, Object> docs, String method, String path, String status) {
        Map<String, Object> operation =
                map(map(map(docs.get("paths")).get(path)).get(method));
        Map<String, Object> response = map(map(operation.get("responses")).get(status));
        return map(map(map(response.get("content")).get("application/json")).get("schema"));
    }

    @Test
    void whatTheApiSendsIsWhatTheDocumentSaysForEveryKindOfAnswer() {
        Map<String, Object> docs = docs();
        Map<String, Object> schemas = map(map(docs.get("components")).get("schemas"));
        List<String> problems = new ArrayList<>();
        User user = aUser();

        // An open entry with a photo, a tag, a caption and a soundtrack, an Era that uses the photo as its cover.
        String open = createEntryId(
                user,
                json(
                        "entryDate",
                        W3,
                        "title",
                        "Arrived at the Grampians",
                        "contentJson",
                        doc("Just arrived at the campsite."),
                        "soundtrack",
                        "spotify:track:4uLU6hMCjMI75M1A2tKUQC"));
        Map<String, Object> photo = uploadPhoto(user, open);
        aTag(user, open, "Trips");
        String era = postEra(
                user,
                json(
                        "title", "Monash",
                        "startDate", "2026-01-01",
                        "soundtrack", "spotify:album:4uLU6hMCjMI75M1A2tKUQC",
                        "coverMediaId", photo.get("id")));
        String endedEra = createEra(user, "Short", "2026-10-01", "2026-10-31", true);
        aTemplate(user, "Trip day");

        // A sealed Loot Drop, an empty entry, one in Recently deleted, and an entry on this day in an earlier year.
        String sealed = entryOn(user, W3, "Sealed");
        sealUntil(user, sealed, "2027-01-01T00:00:00Z");
        String empty = createEntryId(user, json());
        String deleted = createTitledEntry(user);
        delete(user, deleted).expectStatus().isNoContent();
        entryOn(user, "2025-11-03", "A year ago");

        // A Loot Drop that has opened and a reply to it.
        String opened = entryOn(user, W2, "Opened");
        sealUntil(user, opened, "2026-11-02T00:00:00Z");
        clockAt("2026-11-03T00:00:00Z");
        String reply = createEntryId(user, json("title", "Reply", "replyToEntryId", opened));

        // Users and a birthday, both unset and set.
        check(
                problems,
                "GET /users/me (no birthday)",
                answer(user, "/api/v1/users/me"),
                schemaOf(docs, "get", "/api/v1/users/me"),
                schemas);
        client.patch()
                .uri("/api/v1/users/me")
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("birthday", "1990-05-17"))
                .exchange()
                .expectStatus()
                .isOk();
        check(
                problems,
                "GET /users/me (birthday)",
                answer(user, "/api/v1/users/me"),
                schemaOf(docs, "get", "/api/v1/users/me"),
                schemas);

        // Eras, templates, tags
        check(problems, "GET /eras", answer(user, "/api/v1/eras"), schemaOf(docs, "get", "/api/v1/eras"), schemas);
        check(
                problems,
                "GET /eras/{id}",
                answer(user, "/api/v1/eras/" + era),
                schemaOf(docs, "get", "/api/v1/eras/{eraId}"),
                schemas);
        check(
                problems,
                "GET /eras/{id} (ended)",
                answer(user, "/api/v1/eras/" + endedEra),
                schemaOf(docs, "get", "/api/v1/eras/{eraId}"),
                schemas);
        check(
                problems,
                "GET /templates",
                answer(user, "/api/v1/templates"),
                schemaOf(docs, "get", "/api/v1/templates"),
                schemas);
        check(problems, "GET /tags", answer(user, "/api/v1/tags"), schemaOf(docs, "get", "/api/v1/tags"), schemas);

        // The feed and a week
        check(problems, "GET /feed", answer(user, "/api/v1/feed"), schemaOf(docs, "get", "/api/v1/feed"), schemas);
        check(
                problems,
                "GET /weekly-logs/{id}",
                answer(user, "/api/v1/weekly-logs/" + weekIdOf(open)),
                schemaOf(docs, "get", "/api/v1/weekly-logs/{logId}"),
                schemas);

        // Entries: open, sealed, opened with a reply, a reply, empty
        Map<String, Object> entry = schemaOf(docs, "get", "/api/v1/entries/{entryId}");
        for (String id : List.of(open, sealed, opened, reply, empty)) {
            check(problems, "GET /entries/" + id, answer(user, "/api/v1/entries/" + id), entry, schemas);
        }

        // The lists
        Map<String, Object> page = schemaOf(docs, "get", "/api/v1/entries");
        check(problems, "GET /entries", answer(user, "/api/v1/entries"), page, schemas);
        check(
                problems,
                "GET /entries/deleted",
                answer(user, "/api/v1/entries/deleted"),
                schemaOf(docs, "get", "/api/v1/entries/deleted"),
                schemas);
        check(
                problems,
                "GET /entries/opened",
                answer(user, "/api/v1/entries/opened"),
                schemaOf(docs, "get", "/api/v1/entries/opened"),
                schemas);
        check(
                problems,
                "GET /entries/on-this-day",
                answer(user, "/api/v1/entries/on-this-day"),
                schemaOf(docs, "get", "/api/v1/entries/on-this-day"),
                schemas);

        // Media
        check(
                problems,
                "POST /entries/{id}/media",
                photo,
                answerSchema(docs, "post", "/api/v1/entries/{entryId}/media", "201"),
                schemas);

        assertThat(problems).as("where the API and /v3/api-docs disagree").isEmpty();
        // The data really covered the cases the checks are for.
        assertThat(answer(user, "/api/v1/entries/opened").toString()).contains("Opened");
        assertThat(answer(user, "/api/v1/entries/on-this-day").toString()).contains("A year ago");
        assertThat(answer(user, "/api/v1/entries/deleted").toString()).contains("deletedAt");
    }

    @Test
    void anErrorHasExactlyTheFieldsOfTheProblemSchema() {
        Map<String, Object> docs = docs();
        Map<String, Object> schemas = map(map(docs.get("components")).get("schemas"));
        Map<String, Object> problem = map(schemas.get("Problem"));
        List<String> problems = new ArrayList<>();
        User user = aUser();

        RestTestClient.ResponseSpec notFound = client.get()
                .uri("/api/v1/entries/" + UUID.randomUUID())
                .header(HEADER, user.getId().toString())
                .exchange();
        notFound.expectStatus().isNotFound();
        notFound.expectHeader().contentType("application/problem+json");
        check(problems, "404", JsonPath.read(bodyText(notFound), "$"), problem, schemas);

        RestTestClient.ResponseSpec invalid = postTag(user, json());
        invalid.expectStatus().isBadRequest();
        String invalidBody = bodyText(invalid);
        check(problems, "400 with fieldErrors", JsonPath.read(invalidBody, "$"), problem, schemas);
        assertThat(JsonPath.<Map<String, Object>>read(invalidBody, "$.fieldErrors"))
                .containsKey("name");

        assertThat(problems).isEmpty();
    }

    private Object answer(User user, String path) {
        RestTestClient.ResponseSpec response =
                client.get().uri(path).header(HEADER, user.getId().toString()).exchange();
        response.expectStatus().isOk();
        return JsonPath.read(bodyText(response), "$");
    }

    private RestTestClient.ResponseSpec postJson(User user, String path, Map<String, Object> body) {
        return client.post()
                .uri(path)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec postTag(User user, Map<String, Object> body) {
        return postJson(user, "/api/v1/tags", body);
    }

    private String postEra(User user, Map<String, Object> body) {
        RestTestClient.ResponseSpec response = postJson(user, "/api/v1/eras", body);
        response.expectStatus().isCreated();
        return bodyMap(response).get("id").toString();
    }

    private static Map<String, Object> schemaOf(Map<String, Object> docs, String method, String path) {
        return answerSchema(docs, method, path, "200");
    }

    // The walk

    /** Adds to {@code problems} every place where {@code json} is not what {@code schema} describes. */
    private static void check(
            List<String> problems, String where, Object json, Map<String, Object> schema, Map<String, Object> schemas) {
        walk(problems, where, json, schema, schemas);
    }

    @SuppressWarnings("unchecked")
    private static void walk(
            List<String> problems, String where, Object json, Map<String, Object> schema, Map<String, Object> schemas) {
        if (schema.containsKey("$ref")) {
            String name = ((String) schema.get("$ref")).substring("#/components/schemas/".length());
            walk(problems, where, json, map(schemas.get(name)), schemas);
            return;
        }
        if (json == null) {
            if (!allowsNull(schema, schemas)) {
                problems.add(where + " is null, but the schema does not allow null");
            }
            return;
        }
        if (schema.containsKey("oneOf")) {
            List<Map<String, Object>> options = (List<Map<String, Object>>) schema.get("oneOf");
            options.stream()
                    .filter(option -> !"null".equals(option.get("type")))
                    .forEach(option -> walk(problems, where, json, option, schemas));
            return;
        }
        if (json instanceof Map<?, ?> object) {
            Map<String, Object> properties = map(schema.getOrDefault("properties", Map.of()));
            if (properties.isEmpty()) {
                return; // a free-form object: a TipTap document, or fieldErrors
            }
            for (Object key : object.keySet()) {
                if (!properties.containsKey(key)) {
                    problems.add(where + "." + key + " is sent, but the schema has no such property");
                }
            }
            for (String required :
                    schema.containsKey("required") ? castList(schema.get("required")) : List.<String>of()) {
                if (!object.containsKey(required)) {
                    problems.add(where + "." + required + " is required by the schema, but not sent");
                }
            }
            properties.forEach((name, property) -> {
                if (object.containsKey(name)) {
                    walk(problems, where + "." + name, object.get(name), map(property), schemas);
                }
            });
        } else if (json instanceof List<?> list) {
            Map<String, Object> items = map(schema.get("items"));
            if (items == null || items.isEmpty()) {
                problems.add(where + " is a list, but the schema has no items");
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                walk(problems, where + "[" + i + "]", list.get(i), items, schemas);
            }
        } else {
            String type = primitiveTypeOf(json);
            if (!typesOf(schema).contains(type)) {
                problems.add(where + " is " + type + ", but the schema says " + typesOf(schema));
            }
            if (schema.containsKey("enum") && !((List<?>) schema.get("enum")).contains(json)) {
                problems.add(where + " is not one of the schema's enum values");
            }
        }
    }

    private static String primitiveTypeOf(Object json) {
        if (json instanceof Boolean) {
            return "boolean";
        }
        if (json instanceof Integer || json instanceof Long) {
            return "integer";
        }
        return json instanceof Number ? "number" : "string";
    }

    @SuppressWarnings("unchecked")
    private static List<String> typesOf(Map<String, Object> schema) {
        Object type = schema.get("type");
        if (type instanceof List<?> list) {
            return (List<String>) list;
        }
        return type == null ? List.of() : List.of((String) type);
    }

    @SuppressWarnings("unchecked")
    private static boolean allowsNull(Map<String, Object> schema, Map<String, Object> schemas) {
        if (typesOf(schema).contains("null")) {
            return true;
        }
        if (schema.containsKey("oneOf")) {
            return ((List<Map<String, Object>>) schema.get("oneOf"))
                    .stream().anyMatch(option -> allowsNull(option, schemas));
        }
        return false;
    }
}

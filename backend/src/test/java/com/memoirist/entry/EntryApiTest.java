package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.MutableClock;
import com.memoirist.MutableClockConfiguration;
import com.memoirist.template.Template;
import com.memoirist.template.TemplateRepository;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * What every {@code /api/v1/entries} test needs: the whole app in the {@code test} profile on a clock the test can
 * move, a way to make users, and short helpers for the five calls.
 *
 * <p>The clock starts every test at {@link MutableClockConfiguration#START} (Saturday 2026-10-31, 12:00 in
 * Melbourne). The tests that care about "today", the end of a day or the unlock moment move it with
 * {@link #clockAt}. Every test makes its own users and sends their id in {@code X-Dev-User-Id}; the users, with
 * everything that hangs off them, are deleted afterwards, so the seeded dev user and the other test classes that share
 * this Postgres container are left alone.
 *
 * <p>All the subclasses are configured identically, so they share one Spring context and one Postgres container.
 */
@IntegrationTest
@Import(MutableClockConfiguration.class)
abstract class EntryApiTest {

    protected static final String ENTRIES = "/api/v1/entries";
    protected static final String HEADER = "X-Dev-User-Id";

    @Autowired
    protected RestTestClient client;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected TemplateRepository templates;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected MutableClock clock;

    private final List<UUID> created = new ArrayList<>();

    @BeforeEach
    void startTheClockAtSaturdayNoonInMelbourne() {
        clock.set(MutableClockConfiguration.START);
    }

    @AfterEach
    void deleteTheUsersThisTestMade() {
        created.forEach(id ->
                jdbc.sql("DELETE FROM users WHERE id = :id").param("id", id).update());
    }

    // Time

    protected void clockAt(String instant) {
        clock.set(Instant.parse(instant));
    }

    // Users and their data

    /** A user in Melbourne, the default time zone (D-07). */
    protected User aUser() {
        return aUser("Australia/Melbourne");
    }

    protected User aUser(String timezone) {
        User user = Fixtures.user();
        user.setTimezone(timezone);
        users.save(user);
        created.add(user.getId());
        return user;
    }

    protected Template aTemplate(User owner, String name) {
        return templates.save(new Template(UUID.randomUUID(), owner.getId(), name, List.of("What happened?")));
    }

    // Requests

    /** A JSON object from alternating keys and values. A map, not {@code Map.of}, so a value may be null. */
    protected static Map<String, Object> json(Object... keysAndValues) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            body.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return body;
    }

    /** A TipTap document of one paragraph per string. */
    protected static Map<String, Object> doc(String... paragraphs) {
        List<Object> blocks = new ArrayList<>();
        for (String words : paragraphs) {
            blocks.add(paragraph(words));
        }
        return json("type", "doc", "content", blocks);
    }

    protected static Map<String, Object> paragraph(String words) {
        return json("type", "paragraph", "content", List.of(json("type", "text", "text", words)));
    }

    protected static Map<String, Object> heading(int level, String words) {
        return json(
                "type",
                "heading",
                "attrs",
                json("level", level),
                "content",
                List.of(json("type", "text", "text", words)));
    }

    protected RestTestClient.ResponseSpec post(User user, Map<String, Object> body) {
        return client.post()
                .uri(ENTRIES)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    protected RestTestClient.ResponseSpec postRaw(User user, String json) {
        return client.post()
                .uri(ENTRIES)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange();
    }

    protected RestTestClient.ResponseSpec get(User user, Object entryId) {
        return client.get()
                .uri(ENTRIES + "/" + entryId)
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    protected RestTestClient.ResponseSpec put(User user, Object entryId, Map<String, Object> body) {
        return client.put()
                .uri(ENTRIES + "/" + entryId)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    protected RestTestClient.ResponseSpec delete(User user, Object entryId) {
        return client.delete()
                .uri(ENTRIES + "/" + entryId)
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    protected RestTestClient.ResponseSpec seal(User user, Object entryId, Object unlockDate) {
        return client.post()
                .uri(ENTRIES + "/" + entryId + "/seal")
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("unlockDate", unlockDate.toString()))
                .exchange();
    }

    // Reading answers

    /** The answer's body as text, for a status the test has already checked or is about to. */
    protected static String bodyText(RestTestClient.ResponseSpec response) {
        return response.expectBody(String.class).returnResult().getResponseBody();
    }

    /** The body of an answer whose status the test has already checked, as a map. */
    @SuppressWarnings("unchecked")
    protected static Map<String, Object> bodyMap(RestTestClient.ResponseSpec response) {
        return response.expectBody(Map.class).returnResult().getResponseBody();
    }

    /** POSTs an entry that must be accepted ({@code 201}) and returns its JSON as a map. */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> createEntry(User user, Map<String, Object> body) {
        return post(user, body)
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    protected String createEntryId(User user, Map<String, Object> body) {
        return createEntry(user, body).get("id").toString();
    }

    /** An entry with a title, so that it is not "empty" and deleting it goes to Recently deleted (D-30). */
    protected String createTitledEntry(User user) {
        return createEntryId(user, json("title", "A title worth keeping"));
    }

    /** GETs an entry that must exist and returns its JSON as a map. */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getEntry(User user, Object entryId) {
        return get(user, entryId)
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    /** The error's {@code type} suffix, after checking that the body is a problem with the usual fields. */
    protected static String problemType(String body) {
        assertThat(JsonPath.<String>read(body, "$.type")).startsWith("urn:memoirist:problem:");
        assertThat(JsonPath.<Integer>read(body, "$.status")).isNotNull();
        return JsonPath.<String>read(body, "$.type").substring("urn:memoirist:problem:".length());
    }

    protected static Map<String, String> fieldErrors(String body) {
        return JsonPath.read(body, "$.fieldErrors");
    }

    // Reading the database

    /** The whole row, read with SQL so that an entry in Recently deleted (hidden from JPA) is seen too. */
    protected Map<String, Object> row(Object entryId) {
        return jdbc.sql("SELECT * FROM entries WHERE id = :id")
                .param("id", UUID.fromString(entryId.toString()))
                .query()
                .singleRow();
    }

    /** A TIMESTAMPTZ column read into a map comes back as a {@code java.sql.Timestamp}; this is its instant as text. */
    protected static String instantOf(Object timestamp) {
        return ((java.sql.Timestamp) timestamp).toInstant().toString();
    }

    protected boolean rowExists(Object entryId) {
        return jdbc.sql("SELECT count(*) FROM entries WHERE id = :id")
                        .param("id", UUID.fromString(entryId.toString()))
                        .query(Long.class)
                        .single()
                == 1;
    }

    protected long countEntries(User user) {
        return jdbc.sql("SELECT count(*) FROM entries WHERE user_id = :id")
                .param("id", user.getId())
                .query(Long.class)
                .single();
    }

    protected long countWeeks(User user) {
        return jdbc.sql("SELECT count(*) FROM weekly_logs WHERE user_id = :id")
                .param("id", user.getId())
                .query(Long.class)
                .single();
    }

    /** The first day (always a Monday) of the week row an entry is filed in. */
    protected String weekStartOf(Object entryId) {
        return jdbc.sql("""
                        SELECT w.start_date::text FROM weekly_logs w
                        JOIN entries e ON e.weekly_log_id = w.id WHERE e.id = :id
                        """)
                .param("id", UUID.fromString(entryId.toString()))
                .query(String.class)
                .single();
    }

    /**
     * A photo for an entry, written straight to the database: the upload endpoint arrives in task 3.6. The key is
     * only a name; the tests that sign it need no real object, because signing makes no call to storage.
     */
    protected UUID aPhoto(Object entryId, String objectKey, int order) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO media (id, entry_id, object_key, media_type, purpose, display_order, file_size_bytes)
                        VALUES (:id, :entryId, :key, 'image/jpeg', 'PHOTO', :order, 2048)
                        """)
                .param("id", id)
                .param("entryId", UUID.fromString(entryId.toString()))
                .param("key", objectKey)
                .param("order", order)
                .update();
        return id;
    }
}

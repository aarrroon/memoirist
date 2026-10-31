package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import com.memoirist.week.WeekApiTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * What the entry-list tests share on top of {@link WeekApiTest} (entries on given days, sealing, the movable clock and
 * the photo helpers): a short helper for {@code GET /api/v1/entries} and for the tags an entry carries.
 *
 * <p>The days are the same Mondays as the feed tests use ({@code W1} to {@code W4}, October 2026). The clock starts on
 * Saturday 2026-10-31 at 12:00 in Melbourne.
 */
public abstract class EntryListApiTest extends WeekApiTest {

    /** Far enough ahead that the clock never reaches it in a test. */
    protected static final String SEALED_UNTIL = "2027-01-01T00:00:00Z";

    /** The timeline with the query string the test chooses, such as {@code page=1&size=2&tagId=…}. */
    protected RestTestClient.ResponseSpec list(User user, String query) {
        return client.get()
                .uri(ENTRIES + (query.isEmpty() ? "" : "?" + query))
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    /** The timeline's body, which must be a {@code 200}, as text. */
    protected String listBody(User user, String query) {
        RestTestClient.ResponseSpec response = list(user, query);
        response.expectStatus().isOk();
        return bodyText(response);
    }

    /** Every entry the user has, in the order the timeline lists them (the first 100 rows). */
    protected String listBody(User user) {
        return listBody(user, "size=100");
    }

    /** The ids of the rows in a list body, in order. */
    protected static List<String> ids(String listBody) {
        return JsonPath.read(listBody, "$.items[*].id");
    }

    /** The one row with this id in a list body. */
    protected static Map<String, Object> rowIn(String listBody, String id) {
        List<Map<String, Object>> rows = JsonPath.read(listBody, "$.items[?(@.id == '" + id + "')]");
        assertThat(rows).as("the row of entry %s", id).hasSize(1);
        return rows.getFirst();
    }

    /** A tag of the user's, written straight to the database, and its id. */
    protected UUID newTag(User user, String name) {
        UUID tagId = UUID.randomUUID();
        jdbc.sql("INSERT INTO tags (id, user_id, name) VALUES (:id, :user, :name)")
                .param("id", tagId)
                .param("user", user.getId())
                .param("name", name)
                .update();
        return tagId;
    }

    /** Puts a tag on an entry, written straight to the database. */
    protected void tag(Object entryId, UUID tagId) {
        jdbc.sql("INSERT INTO entry_tags (entry_id, tag_id) VALUES (:entry, :tag)")
                .param("entry", UUID.fromString(entryId.toString()))
                .param("tag", tagId)
                .update();
    }

    /** An entry whose caption is exactly these blocks (a heading or a paragraph each), dated {@code day}. */
    protected String entryWithCaption(User user, String day, Object... blocks) {
        return createEntryId(
                user, json("entryDate", day, "contentJson", json("type", "doc", "content", List.of(blocks))));
    }

    /** Makes one entry look as if it was written at this instant, which the API itself never lets a test choose. */
    protected void writtenAt(Object entryId, String instant) {
        jdbc.sql("UPDATE entries SET created_at = :at::timestamptz WHERE id = :id")
                .param("at", instant)
                .param("id", UUID.fromString(entryId.toString()))
                .update();
    }
}

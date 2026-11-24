package com.memoirist.week;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.entry.EntryApiTest;
import com.memoirist.user.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * What the feed and week tests share on top of {@link EntryApiTest}: short helpers for the feed, the four week calls
 * and the Era calls the week tests need, and a way to build a journal of entries on given days.
 *
 * <p>The days used are Mondays and the days around them in October 2026 (the clock starts on Saturday 2026-10-31):
 * <pre>
 *   W1  Oct 05 - Oct 11      W3  Oct 19 - Oct 25      W5  Nov 02 - Nov 08 (a week that has not started: no entries)
 *   W2  Oct 12 - Oct 18      W4  Oct 26 - Nov 01
 * </pre>
 */
public abstract class WeekApiTest extends EntryApiTest {

    protected static final String FEED = "/api/v1/feed";
    protected static final String WEEKLY_LOGS = "/api/v1/weekly-logs";
    protected static final String ERAS = "/api/v1/eras";

    // The days of the weeks, as text, because every request and answer uses text.
    protected static final String W1 = "2026-10-05";
    protected static final String W2 = "2026-10-12";
    protected static final String W3 = "2026-10-19";
    protected static final String W4 = "2026-10-26";

    /** Gives an entry a tag of the user's, written straight to the database: the feed tests need no endpoint calls for it. */
    protected void aTag(User user, Object entryId, String name) {
        UUID tagId = UUID.randomUUID();
        jdbc.sql("INSERT INTO tags (id, user_id, name) VALUES (:id, :user, :name)")
                .param("id", tagId)
                .param("user", user.getId())
                .param("name", name)
                .update();
        jdbc.sql("INSERT INTO entry_tags (entry_id, tag_id) VALUES (:entry, :tag)")
                .param("entry", UUID.fromString(entryId.toString()))
                .param("tag", tagId)
                .update();
    }

    // The feed

    /** The feed as the user sees it, with the query string the test chooses, such as {@code page=1&size=2}. */
    protected RestTestClient.ResponseSpec feed(User user, String query) {
        return client.get()
                .uri(FEED + (query.isEmpty() ? "" : "?" + query))
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    /** The first 50 weeks of the feed, which must be a {@code 200}, as the answer's text. */
    protected String feedBody(User user) {
        RestTestClient.ResponseSpec response = feed(user, "page=0&size=50");
        response.expectStatus().isOk();
        return bodyText(response);
    }

    /** The start dates of the weeks in a feed or page body, in the order they are listed. */
    protected static List<String> weekStarts(String feedBody) {
        return JsonPath.read(feedBody, "$.items[*].startDate");
    }

    // One week

    protected RestTestClient.ResponseSpec getWeek(User user, Object weekId) {
        return client.get()
                .uri(WEEKLY_LOGS + "/" + weekId)
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    protected RestTestClient.ResponseSpec putWeek(User user, Object weekId, Map<String, Object> body) {
        return client.put()
                .uri(WEEKLY_LOGS + "/" + weekId)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    protected RestTestClient.ResponseSpec closeWeek(User user, Object weekId) {
        return postAction(user, WEEKLY_LOGS + "/" + weekId + "/close");
    }

    protected RestTestClient.ResponseSpec reopenWeek(User user, Object weekId) {
        return postAction(user, WEEKLY_LOGS + "/" + weekId + "/reopen");
    }

    private RestTestClient.ResponseSpec postAction(User user, String path) {
        return client.post().uri(path).header(HEADER, user.getId().toString()).exchange();
    }

    /** A week's answer (the body of a {@code 200}) as text. */
    protected String weekBody(User user, Object weekId) {
        RestTestClient.ResponseSpec response = getWeek(user, weekId);
        response.expectStatus().isOk();
        return bodyText(response);
    }

    /** The id of the week row an entry is filed in. */
    protected String weekIdOf(Object entryId) {
        return jdbc.sql("SELECT weekly_log_id FROM entries WHERE id = :id")
                .param("id", UUID.fromString(entryId.toString()))
                .query(UUID.class)
                .single()
                .toString();
    }

    // Entries on given days

    /** A titled entry with a one-paragraph caption, dated {@code day}, so it is neither empty nor sealed. */
    protected String entryOn(User user, String day, String title) {
        return createEntryId(user, json("entryDate", day, "title", title, "contentJson", doc("About " + title)));
    }

    /** Seals an entry until {@code unlockDate} (an instant as text) and checks that it worked. */
    protected void sealUntil(User user, String entryId, String unlockDate) {
        seal(user, entryId, unlockDate).expectStatus().isOk();
    }

    // Eras

    /** Creates an Era for the user and returns its id. {@code end} may be {@code null} for an Era that is ongoing. */
    protected String createEra(User user, String title, String start, String end, boolean pinned) {
        Map<String, Object> body = json("title", title, "startDate", start, "endDate", end, "pinnedInFeed", pinned);
        RestTestClient.ResponseSpec response = client.post()
                .uri(ERAS)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
        response.expectStatus().isCreated();
        return bodyMap(response).get("id").toString();
    }

    protected RestTestClient.ResponseSpec putEra(User user, String eraId, Map<String, Object> body) {
        return client.put()
                .uri(ERAS + "/" + eraId)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    protected void deleteEra(User user, String eraId) {
        client.delete()
                .uri(ERAS + "/" + eraId)
                .header(HEADER, user.getId().toString())
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    /** The titles of the Eras a week answer lists, in the order it lists them. */
    protected static List<String> eraTitles(String weekJson) {
        return JsonPath.read(weekJson, "$.eras[*].title");
    }
}

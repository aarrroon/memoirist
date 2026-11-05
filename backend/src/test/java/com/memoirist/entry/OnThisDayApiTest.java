package com.memoirist.entry;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * What the On this day tests share on top of {@link EntryOpenedApiTest}: short helpers for
 * {@code GET /api/v1/entries/on-this-day} and a few entries dated in earlier years.
 *
 * <p>The clock starts every test at Saturday 2026-10-31 at 12:00 in Melbourne (01:00 UTC), so the strip looks for
 * 31 October in earlier years unless a test moves the clock. Entries are made through the API with a memory date in the
 * past, which the API allows back to 1900-01-01 (D-09); the clock only decides "today".
 */
public abstract class OnThisDayApiTest extends EntryOpenedApiTest {

    protected static final String ON_THIS_DAY = ENTRIES + "/on-this-day";

    protected RestTestClient.ResponseSpec onThisDay(User user) {
        return client.get()
                .uri(ON_THIS_DAY)
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    /** The strip's body, which must be a {@code 200}, as text. */
    protected String onThisDayBody(User user) {
        RestTestClient.ResponseSpec response = onThisDay(user);
        response.expectStatus().isOk();
        return bodyText(response);
    }

    /** The ids in the strip, in the order they are listed. */
    protected List<String> onThisDayIds(User user) {
        return JsonPath.read(onThisDayBody(user), "$[*].id");
    }

    /** The memory dates in the strip, in the order they are listed. */
    protected List<String> onThisDayDates(User user) {
        return JsonPath.read(onThisDayBody(user), "$[*].entryDate");
    }

    /** The rows in the strip, in order. */
    protected List<Map<String, Object>> onThisDayRows(User user) {
        return JsonPath.read(onThisDayBody(user), "$");
    }

    /** Brings an entry back from Recently deleted and checks that it worked. */
    protected void restore(User user, Object entryId) {
        client.post()
                .uri(ENTRIES + "/" + entryId + "/restore")
                .header(HEADER, user.getId().toString())
                .exchange()
                .expectStatus()
                .isOk();
    }

    /** Moves the user to another time zone, through the API a client uses. */
    protected void moveTo(User user, String timezone) {
        client.patch()
                .uri("/api/v1/users/me")
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("timezone", timezone))
                .exchange()
                .expectStatus()
                .isOk();
    }
}

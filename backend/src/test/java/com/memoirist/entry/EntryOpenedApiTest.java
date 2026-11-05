package com.memoirist.entry;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.user.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * What the Opened strip tests share on top of {@link EntryListApiTest}: short helpers for {@code GET /entries/opened}
 * and {@code POST /entries/{id}/mark-seen}, a Loot Drop that is sealed until a given instant, and a way to read the
 * seen mark straight from the database.
 *
 * <p>The clock starts every test at Saturday 2026-10-31 01:00 UTC (12:00 in Melbourne). The unlock instants below are
 * all 09:00 in Melbourne, which is 22:00 UTC the day before, the time the seal dialog offers (D-23), and they are
 * ordered {@link #OPENS_1} before {@link #OPENS_2} before {@link #OPENS_3}.
 */
public abstract class EntryOpenedApiTest extends EntryListApiTest {

    protected static final String OPENS_1 = "2026-11-02T22:00:00Z"; // Tuesday 3 Nov, 09:00 in Melbourne
    protected static final String OPENS_2 = "2026-11-03T22:00:00Z";
    protected static final String OPENS_3 = "2026-11-04T22:00:00Z";

    /** A moment after all three, when every Loot Drop above has opened. */
    protected static final String AFTER_ALL = "2026-11-10T00:00:00Z";

    // The strip

    protected RestTestClient.ResponseSpec opened(User user) {
        return client.get()
                .uri(ENTRIES + "/opened")
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    /** The strip's body, which must be a {@code 200}, as text. */
    protected String openedBody(User user) {
        RestTestClient.ResponseSpec response = opened(user);
        response.expectStatus().isOk();
        return bodyText(response);
    }

    /** The ids in the strip, in the order they are listed. */
    protected List<String> openedIds(User user) {
        return JsonPath.read(openedBody(user), "$[*].id");
    }

    /** The rows in the strip, in order. */
    protected List<Map<String, Object>> openedRows(User user) {
        return JsonPath.read(openedBody(user), "$");
    }

    // Marking one seen

    protected RestTestClient.ResponseSpec markSeen(User user, Object entryId) {
        return client.post()
                .uri(ENTRIES + "/" + entryId + "/mark-seen")
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    /** Marks an entry seen and checks that it was accepted ({@code 204}). */
    protected void markSeenOk(User user, Object entryId) {
        markSeen(user, entryId).expectStatus().isNoContent();
    }

    /** The entry's seen mark as an instant in text, or {@code null} when it has none, read with SQL. */
    protected String seenAt(Object entryId) {
        Object mark = row(entryId).get("unlock_seen_at");
        return mark == null ? null : instantOf(mark);
    }

    // Loot Drops

    /** A titled entry with a one-paragraph caption, dated in week W4 and sealed until {@code unlockDate}. */
    protected String aLootDrop(User user, String title, String unlockDate) {
        String id = entryOn(user, W4, title);
        sealUntil(user, id, unlockDate);
        return id;
    }

    /** Makes an existing entry's unlock date this instant, which the API itself never lets a test choose (it must be in the future). */
    protected void unlockedAt(Object entryId, String instant) {
        jdbc.sql("UPDATE entries SET unlock_date = :at::timestamptz WHERE id = :id")
                .param("at", instant)
                .param("id", UUID.fromString(entryId.toString()))
                .update();
    }
}

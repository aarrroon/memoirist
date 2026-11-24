package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.media.MediaApiTest;
import com.memoirist.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * What the Recently deleted tests share (task 3.11, D-30), on top of {@link MediaApiTest} (the feed, list, Opened strip
 * and media helpers, a real SeaweedFS and the movable clock): short helpers for {@code GET /entries/deleted},
 * {@code restore} and {@code erase}, an entry that has files really stored for it, and a way to send the same request
 * from several threads at once.
 *
 * <p>The clock starts every test at Saturday 2026-10-31 at 01:00 UTC. {@link #deletedAt} reads when an entry went to
 * Recently deleted, as the clock said at the time of the {@code DELETE}.
 */
public abstract class RecentlyDeletedApiTest extends MediaApiTest {

    protected static final String DELETED = ENTRIES + "/deleted";

    @Autowired
    protected RecentlyDeletedSweeper sweeper;

    // The three new calls

    protected RestTestClient.ResponseSpec deletedList(User user, String query) {
        return client.get()
                .uri(DELETED + (query.isEmpty() ? "" : "?" + query))
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    /** The Recently deleted list's body, which must be a {@code 200}, as text. */
    protected String deletedBody(User user, String query) {
        RestTestClient.ResponseSpec response = deletedList(user, query);
        response.expectStatus().isOk();
        return bodyText(response);
    }

    protected String deletedBody(User user) {
        return deletedBody(user, "size=50");
    }

    /** The ids in the user's Recently deleted list (the first 50 rows), in the order they are listed. */
    protected List<String> deletedIds(User user) {
        return ids(deletedBody(user));
    }

    protected RestTestClient.ResponseSpec restore(User user, Object entryId) {
        return client.post()
                .uri(ENTRIES + "/" + entryId + "/restore")
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    protected RestTestClient.ResponseSpec erase(User user, Object entryId) {
        return client.post()
                .uri(ENTRIES + "/" + entryId + "/erase")
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    // Making and moving entries

    /** Deletes an entry that must be accepted ({@code 204}) at the moment the clock says now. */
    protected void deleteOk(User user, Object entryId) {
        delete(user, entryId).expectStatus().isNoContent();
    }

    /** Moves the clock to this instant and deletes the entry then, so its {@code deleted_at} is that instant. */
    protected void deleteAt(String instant, User user, Object entryId) {
        clockAt(instant);
        deleteOk(user, entryId);
    }

    /** When the entry went to Recently deleted, read with SQL, or {@code null} when it is not there. */
    protected String deletedAt(Object entryId) {
        Object stamp = row(entryId).get("deleted_at");
        return stamp == null ? null : instantOf(stamp);
    }

    /**
     * Gives an entry a photo that really is in storage: the file is put in the bucket and its row is written, so a test
     * can see whether erasing the entry takes the file away. Returns the object key.
     */
    protected String aStoredPhoto(Object entryId, int order) {
        String key = "users/test/entries/" + entryId + "/" + UUID.randomUUID() + ".jpg";
        storage.put(key, bytes(256, order), "image/jpeg");
        aPhoto(entryId, key, order);
        return key;
    }

    /** An entry with a title, a caption and these many stored photos, dated {@code day}. Returns its id. */
    protected String anEntryWithPhotos(User user, String day, String title, int photos) {
        String id = entryOn(user, day, title);
        for (int order = 0; order < photos; order++) {
            aStoredPhoto(id, order);
        }
        return id;
    }

    // Reading answers

    protected static List<String> deletedAtsIn(String listBody) {
        return JsonPath.read(listBody, "$.items[*].deletedAt");
    }

    // Several requests at once

    /** Runs {@code call} on {@code callers} threads that are all released together, and returns the status codes. */
    protected static List<Integer> atTheSameTime(int callers, IntFunction<Integer> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int caller = 0; caller < callers; caller++) {
                int number = caller;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.apply(number);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}

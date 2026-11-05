package com.memoirist.tag;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.SqlLog;
import com.memoirist.user.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Requests for the same tags at the same moment. The unique index on {@code (user_id, lower(name))} is what stops two
 * tags with one name, and the service is written so that the request that loses the race gets a proper answer ({@code 200}
 * or {@code 409}), not a {@code 500} from the index. A tag that is deleted while an entry is being tagged with it is the
 * other race: the entry has to find out (404), not break the foreign key.
 *
 * <p>The outcome of each race below is the same whichever request wins and whether or not they truly overlap, so the
 * tests can't fail by bad luck; they fail when the code lets the database's error through.
 */
class TagRaceTest extends TagApiTest {

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    TagRepository tagRepository;

    /** Runs {@code call} on {@code callers} threads that are all released together, and returns the status codes. */
    private static List<Integer> atTheSameTime(int callers, IntFunction<Integer> call) throws Exception {
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

    // TODO(human) PRACTICE: Write the test that proves five requests creating the same new tag at the same moment give
    //   exactly one 201, four 200s and one row. Use the atTheSameTime helper above. Repeat it for a few names, because
    //   a race that is not hit once may be hit the next time.
    // Hint: The outcome must not depend on which request wins, or the test would pass or fail by luck. Compare the
    //   statuses with containsExactlyInAnyOrder. Then check the database, not only the answers: how many rows does the
    //   user have for that name, and do all five answers name the same tag id? Break TagService.create (a select first,
    //   then insert) to see this test fail with a 500.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    @Test
    void requestsThatCreateTheSameNewNameAtTheSameMomentGiveOneTagAndOneCreated() throws Exception {
        User alice = aUser();

        for (String name : List.of("Work", "Family", "Going out", "Travel")) {
            // Five threads add to it, so it has to be a list that is safe to share.
            List<String> ids = Collections.synchronizedList(new ArrayList<>());
            List<Integer> statuses = atTheSameTime(5, caller -> {
                // Half of them spell the name in capitals: the same name, ignoring case.
                var result = postTag(alice, caller % 2 == 0 ? name : name.toUpperCase())
                        .expectBody(Map.class)
                        .returnResult();
                ids.add(result.getResponseBody().get("id").toString());
                return result.getStatus().value();
            });

            assertThat(statuses).as(name).containsExactlyInAnyOrder(201, 200, 200, 200, 200);
            assertThat(ids).as("all five name the same tag").hasSize(5).containsOnly(ids.get(0));
        }

        assertThat(countTags(alice)).as("one row per name").isEqualTo(4);
        // Whichever request won keeps its spelling, so compare the names without their capitals.
        assertThat(tagNames(alice).stream().map(String::toLowerCase))
                .containsExactly("family", "going out", "travel", "work");
    }
    // └── END ANSWER ▲ ──

    @Test
    void twoRenamesToTheSameFreeNameGiveOneOkAndOneConflictAndOneTagHasTheName() throws Exception {
        User alice = aUser();
        for (int round = 0; round < 4; round++) {
            String target = "Target " + round;
            UUID first = aTag(alice, "First " + round);
            UUID second = aTag(alice, "Second " + round);
            List<UUID> tagIds = List.of(first, second);

            List<Integer> statuses = atTheSameTime(
                    2,
                    caller -> putTag(alice, tagIds.get(caller), target)
                            .returnResult()
                            .getStatus()
                            .value());

            assertThat(statuses).as(target).containsExactlyInAnyOrder(200, 409);
            assertThat(tagNames(alice).stream()
                            .filter(name -> name.equals(target))
                            .count())
                    .as("exactly one tag has the name")
                    .isEqualTo(1);
        }
    }

    @Test
    void aTagDeletedWhileAnEntryIsBeingTaggedWithItMakesTheEntryUpdate404AndLeavesTheEntryAlone() throws Exception {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        String id = createEntryId(alice, json("title", "Mine"));
        Map<String, Object> before = row(id);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // A delete that has run but not yet committed: the tag row is locked.
            Future<?> deleter = pool.submit(() -> tx.executeWithoutResult(status -> {
                jdbc.sql("DELETE FROM tags WHERE id = :id").param("id", work).update();
                deleted.countDown();
                try {
                    assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(deleted.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> update = pool.submit(() -> {
                Map<String, Object> body =
                        json("version", 0, "entryDate", "2026-10-31", "title", "Mine", "tagIds", ids(work));
                return put(alice, id, body).returnResult().getStatus().value();
            });
            Thread.sleep(500);
            assertThat(update.isDone())
                    .as("the update waits for the delete instead of reading a tag that is about to go")
                    .isFalse();

            release.countDown();
            deleter.get(30, TimeUnit.SECONDS);
            assertThat(update.get(30, TimeUnit.SECONDS))
                    .as("the tag is gone by the time it looks")
                    .isEqualTo(404);
        } finally {
            pool.shutdownNow();
        }

        assertThat(row(id)).isEqualTo(before);
        assertThat(linkedTagIds(id)).isEmpty();
    }

    @Test
    void theLookupThatChecksTheTagsAsksForAShareLockSoSeveralEditsNeverWaitForEachOther() {
        User alice = aUser();
        UUID work = aTag(alice, "Work");
        TransactionTemplate tx = new TransactionTemplate(transactions);

        String sql = tx.execute(status -> {
            try (SqlLog log = SqlLog.capture()) {
                assertThat(tagRepository.findAndLockByIdInAndUserId(List.of(work), alice.getId()))
                        .hasSize(1);
                return String.join("\n", log.statements());
            }
        });

        // FOR SHARE conflicts with a delete of the row but not with another FOR SHARE.
        assertThat(sql).contains("for share");
    }
}

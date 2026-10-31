package com.memoirist.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.SqlLog;
import com.memoirist.entry.EntryRepository;
import com.memoirist.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two requests changing one entry's carousel at the same moment. The entry's row lock makes them take turns, so the
 * positions stay {@code 0, 1, 2, ...} with no gap and no repeat, and the unique constraint never has to be the thing
 * that stops a bad write.
 */
class MediaConcurrencyTest extends MediaApiTest {

    @Autowired
    EntryRepository entries;

    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void uploadsToTheSameEntryAtTheSameTimeAllGetTheirOwnPosition() throws Exception {
        User user = aUser();
        String entryId = createTitledEntry(user);
        int uploads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(uploads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < uploads; i++) {
                long seed = 100 + i;
                statuses.add(pool.submit(() -> {
                    go.await();
                    return upload(user, entryId, bytes(1024, seed), "image/jpeg")
                            .returnResult()
                            .getStatus()
                            .value();
                }));
            }
            go.countDown();
            for (Future<Integer> status : statuses) {
                assertThat(status.get(60, TimeUnit.SECONDS)).isEqualTo(201);
            }
        } finally {
            pool.shutdownNow();
        }

        // Without the lock two uploads can both read "3 items" and both insert at position 3, and the deferred unique
        // constraint then fails one of them at commit, which would be a 500 here.
        assertThat(positions(entryId)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        assertThat(objectKeysOf(entryId))
                .hasSize(8)
                .doesNotHaveDuplicates()
                .allSatisfy(key -> assertThat(stored(key)).isTrue());
    }

    @Test
    void anUploadAndADeleteAtTheSameTimeLeaveNoGap() throws Exception {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 6);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                String doomed = ids.get(i * 2);
                statuses.add(pool.submit(() -> {
                    go.await();
                    return deleteMedia(user, doomed).returnResult().getStatus().value();
                }));
                long seed = 200 + i;
                statuses.add(pool.submit(() -> {
                    go.await();
                    return upload(user, entryId, bytes(1024, seed), "image/jpeg")
                            .returnResult()
                            .getStatus()
                            .value();
                }));
            }
            go.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> status : statuses) {
                codes.add(status.get(60, TimeUnit.SECONDS));
            }
            assertThat(codes).containsOnly(201, 204);
        } finally {
            pool.shutdownNow();
        }

        // Six items, three deleted and three added.
        assertThat(positions(entryId)).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    void theLockingLookupTakesARowLockAndMakesASecondRequestForItWait() throws Exception {
        User user = aUser();
        String entryId = createTitledEntry(user);
        UUID id = UUID.fromString(entryId);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch secondIsWaiting = new CountDownLatch(1);
        AtomicReference<Instant> secondAsked = new AtomicReference<>();
        AtomicReference<Instant> secondAcquired = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> tx.executeWithoutResult(status -> {
                String sql;
                try (SqlLog log = SqlLog.capture()) {
                    entries.findForUpdateByIdAndUserId(id, user.getId()).orElseThrow();
                    sql = String.join("\n", log.statements()).toLowerCase();
                }
                assertThat(sql).as("the lock is in the query").contains("for no key update");
                holding.countDown();
                try {
                    assertThat(secondIsWaiting.await(10, TimeUnit.SECONDS)).isTrue();
                    // Give the second request time to reach the lock and block on it.
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> second = pool.submit(() -> tx.executeWithoutResult(status -> {
                secondAsked.set(Instant.now());
                secondIsWaiting.countDown();
                entries.findForUpdateByIdAndUserId(id, user.getId()).orElseThrow();
                secondAcquired.set(Instant.now());
            }));
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // The first transaction held the row for about half a second after the second one asked for it.
        assertThat(Duration.between(secondAsked.get(), secondAcquired.get()))
                .as("how long the second request waited for the lock")
                .isGreaterThanOrEqualTo(Duration.ofMillis(400));
    }
}

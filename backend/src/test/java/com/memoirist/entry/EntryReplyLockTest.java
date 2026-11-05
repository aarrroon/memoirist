package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memoirist.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The lock behind a reply (task 3.13): checking a reply's target takes {@code FOR SHARE} on the Loot Drop's row until
 * the transaction ends. A race can't be made to fail reliably (a first attempt, with eight threads, passed even with the
 * lock removed), so this proves the lock itself: while one transaction holds it, a second connection can't take the
 * lock a delete or an erase takes ({@code FOR NO KEY UPDATE}), but can take another share, so two replies don't wait
 * for each other.
 *
 * <p>{@code NOWAIT} makes a lock that can't be had fail at once instead of waiting, which is what turns "would block"
 * into something a test can assert. The test method isn't transactional, so {@link #jdbc} is a different connection
 * from the one the transaction template holds.
 */
class EntryReplyLockTest extends RecentlyDeletedApiTest {

    private static final String TAKE_LOCK = "SELECT id FROM entries WHERE id = :id FOR %s NOWAIT";

    @Autowired
    EntryRepository entries;

    @Autowired
    PlatformTransactionManager transactionManager;

    /**
     * Tries the lock from "another request": a transaction of its own, so a connection of its own. (A plain
     * {@code jdbc} call made while the first transaction is open would join that transaction's connection, since the
     * JPA transaction manager shares its connection with the thread, and a lock never blocks its own holder. The first
     * version of this test failed for that reason, with the lock in place.)
     */
    private void lockWith(String mode, String id) {
        TransactionTemplate otherRequest = new TransactionTemplate(transactionManager);
        otherRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        otherRequest.executeWithoutResult(status -> jdbc.sql(TAKE_LOCK.formatted(mode))
                .param("id", UUID.fromString(id))
                .query()
                .listOfRows());
    }

    // TODO(human) PRACTICE: Write the test that proves a row lock is really held, without relying on timing. Open a
    //   transaction that calls the locking finder, and while it is still open try to take the lock a delete would
    //   take (FOR NO KEY UPDATE) from a second connection, using NOWAIT so a lock that can't be had fails at once
    //   instead of hanging. Assert the failure, then assert the lock is free again once the transaction has ended.
    // Hint: lockWith() above already opens the second connection with REQUIRES_NEW, and says why a plain jdbc call
    //   would not work (a lock never blocks the transaction that holds it). The message to look for is "could not
    //   obtain lock". Then remove @Lock from the finder and check that your test fails; a test that cannot fail
    //   proves nothing.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    @Test
    void whileAReplyChecksItsTargetNoDeleteOrEraseCanLockTheLootDrop() {
        User alice = aUser();
        String drop = aLootDrop(alice, "Dear future me", OPENS_1);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(entries.findAndLockByIdAndUserId(UUID.fromString(drop), alice.getId()))
                    .isPresent();

            assertThatThrownBy(() -> lockWith("NO KEY UPDATE", drop))
                    .as("a delete or an erase has to wait for the reply")
                    .hasMessageContaining("could not obtain lock");
        });

        lockWith("NO KEY UPDATE", drop); // the transaction is over, so the lock is free again
    }
    // └── END ANSWER ▲ ──

    @Test
    void twoRepliesCanLookAtTheSameLootDropAtTheSameTime() {
        User alice = aUser();
        String drop = aLootDrop(alice, "Dear future me", OPENS_1);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entries.findAndLockByIdAndUserId(UUID.fromString(drop), alice.getId());

            lockWith("SHARE", drop); // another reply: no wait, no error
        });
    }

    @Test
    void theFinderIsStillScopedToTheUser() {
        User alice = aUser();
        User bob = aUser();
        String drop = aLootDrop(alice, "Dear future me", OPENS_1);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> assertThat(entries.findAndLockByIdAndUserId(UUID.fromString(drop), bob.getId()))
                                .isEmpty());
    }
}

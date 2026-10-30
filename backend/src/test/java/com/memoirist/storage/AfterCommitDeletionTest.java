package com.memoirist.storage;

import static com.memoirist.storage.StorageServiceIntegrationTest.newKey;
import static com.memoirist.storage.StorageServiceIntegrationTest.randomBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memoirist.IntegrationTest;
import com.memoirist.SeaweedFsTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * "After-commit object deletion" (task 3.1): {@link StorageService#deleteAfterCommit} against a real transaction
 * manager and a real SeaweedFS. A {@link TransactionTemplate} stands in for a service method annotated
 * {@code @Transactional}: it begins a transaction, runs the lambda, and commits, or rolls back if the lambda throws.
 */
@IntegrationTest
@Import(SeaweedFsTestConfiguration.class)
class AfterCommitDeletionTest {

    @Autowired
    StorageService storage;

    @Autowired
    StorageProperties properties;

    @Autowired
    S3Client s3;

    @Autowired
    TransactionTemplate transaction;

    @Test
    void aCommittedTransactionDeletesTheObjectsAfterTheCommitNotBefore() {
        String first = stored("jpg");
        String second = stored("jpg");

        transaction.executeWithoutResult(status -> {
            storage.deleteAfterCommit(List.of(first, second));

            // Still inside the transaction: nothing has been deleted yet, so a failing commit would lose nothing.
            assertThat(exists(first)).isTrue();
            assertThat(exists(second)).isTrue();
        });

        assertThat(exists(first)).isFalse();
        assertThat(exists(second)).isFalse();
    }

    // TODO(human) PRACTICE: Prove that a rolled-back transaction never loses a file. Store an object, ask for its
    //   deletion inside a transaction that then fails, and check the object is still there afterwards. Then do the
    //   same with setRollbackOnly() instead of an exception. Decide what the test must assert besides "still stored",
    //   so it can't pass just because the delete never ran at all.
    // Hint: TransactionTemplate.executeWithoutResult rolls back when the lambda throws, and rethrows. The test above
    //   already proves a commit deletes, which is what makes "still stored" mean something here. The thing to guard
    //   against is a listener that deletes on any outcome, so an event published inside the transaction must not act
    //   until the transaction says it committed.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    @Test
    void aRolledBackTransactionKeepsTheObjects() {
        String key = stored("jpg");

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                    storage.deleteAfterCommit(List.of(key));
                    throw new IllegalStateException("the database insert failed");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("the database insert failed");

        assertThat(exists(key)).as("rolled back by an exception").isTrue();

        transaction.executeWithoutResult(status -> {
            storage.deleteAfterCommit(List.of(key));
            status.setRollbackOnly();
        });

        assertThat(exists(key)).as("rolled back with setRollbackOnly()").isTrue();

        // The same key in a transaction that does commit: it goes. The rollbacks above did not queue it for later.
        transaction.executeWithoutResult(status -> storage.deleteAfterCommit(List.of(key)));

        assertThat(exists(key)).as("committed").isFalse();
    }
    // └── END ANSWER ▲ ──

    @Test
    void withNoTransactionTheObjectsAreDeletedAtOnce() {
        String key = stored("jpg");

        // No transaction here. A listener that waited for a commit would wait forever and the file would leak, so
        // the listener runs the event at once (fallbackExecution = true).
        storage.deleteAfterCommit(List.of(key));

        assertThat(exists(key)).isFalse();
    }

    @Test
    void aKeyThatIsAlreadyGoneDoesNotStopTheRestOrFailTheCommit() {
        String gone = newKey("jpg");
        String present = stored("jpg");

        transaction.executeWithoutResult(status -> storage.deleteAfterCommit(List.of(gone, present)));

        assertThat(exists(present)).isFalse();
    }

    private String stored(String extension) {
        String key = newKey(extension);
        storage.put(key, randomBytes(128, 7), "image/jpeg");
        assertThat(exists(key)).isTrue();
        return key;
    }

    private boolean exists(String key) {
        try {
            s3.headObject(request -> request.bucket(properties.bucket()).key(key));
            return true;
        } catch (NoSuchKeyException missing) {
            return false;
        }
    }
}

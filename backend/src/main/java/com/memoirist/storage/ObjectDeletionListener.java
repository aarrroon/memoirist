package com.memoirist.storage;

import com.memoirist.common.StorageUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

/**
 * Deletes stored objects after the transaction that asked for it has committed (TECH_STACK §2 Storage, Deletion).
 *
 * <p>Services call {@link StorageService#deleteAfterCommit}, which publishes an {@link ObjectDeletionRequested}. Spring
 * holds a {@code @TransactionalEventListener} event back until the publishing transaction finishes, then delivers it
 * only for the phase named here. A rollback drops the event, and the files stay.
 *
 * <p>The deleting is best effort. By the time it runs the rows are already gone, so a file that survives is only
 * wasted space: the listener logs the key and carries on with the others, and it never lets an exception reach the
 * request that has already committed. A later sweep can collect the stragglers (not built yet).
 */
@Component
class ObjectDeletionListener {

    private static final Logger log = LoggerFactory.getLogger(ObjectDeletionListener.class);

    private final StorageService storage;

    ObjectDeletionListener(StorageService storage) {
        this.storage = storage;
    }

    // TODO(human) PRACTICE: Write the listener that deletes the files after the commit. Three things to decide: which
    //   transaction phase, what happens when there is no transaction at all (a caller who forgot @Transactional), and
    //   what a failing delete does to the other keys and to the request that already committed.
    // Hint: AFTER_COMMIT is the phase. Without fallbackExecution = true, an event published outside a transaction is
    //   silently dropped, and the files would leak with no sign of it. An exception thrown from an after-commit
    //   callback is rethrown to whoever called commit(), so a 200 would turn into a 500 for data that is saved. Log
    //   the key (an ID, not content) and never the SDK's message or a URL (.claude/rules/security.md).
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void deleteObjects(ObjectDeletionRequested event) {
        for (String objectKey : event.objectKeys()) {
            try {
                storage.delete(objectKey);
            } catch (RuntimeException failure) {
                log.warn("Could not delete stored object {} ({}); it stays in storage", objectKey, describe(failure));
            }
        }
    }
    // └── END ANSWER ▲ ──

    /** The exception's class, and the HTTP status if storage answered. Never its message: that can name a host. */
    private static String describe(RuntimeException failure) {
        Throwable cause = failure instanceof StorageUnavailableException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        String status = cause instanceof AwsServiceException service ? ", HTTP " + service.statusCode() : "";
        return cause.getClass().getName() + status;
    }
}

package com.memoirist.entry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Erases the entries that have waited in Recently deleted for more than 30 days, with their stored files (D-30). The
 * daily {@link RecentlyDeletedSweepJob} only calls {@link #sweep()}; the work is here so that something else can call it
 * too (Sprint 5 may start it from an outside scheduler, Q5) and so a test can call it with a clock it has moved.
 *
 * <p><b>Safe to repeat, and to overlap.</b> A second call finds nothing left to do. Two calls at the same moment (two
 * app instances, or a scheduler and an outside trigger) never erase the same entry twice or step on each other: each
 * entry is erased in its own transaction, after a lock (see {@link #eraseIfStillDue}).
 *
 * <p><b>One entry at a time, and one failure doesn't stop the others.</b> Each erase is its own transaction, so a
 * row that can't be removed costs one entry, and the next call tries it again. A stored file that can't be deleted is
 * different: it is deleted best effort after the commit (ObjectDeletionListener), so a failure there is only logged,
 * the row is already gone and no later sweep finds the file; it stays in storage as an orphan until the straggler
 * sweep (not built yet) exists. One transaction for the whole run would roll every entry back for one bad one, and
 * would hold the locks of thousands of rows at once.
 *
 * <p>It has no current user, because nobody asked: it works through every user's entries and takes no id from a
 * request. Every entry goes through {@link EntryErasure}, the one place that deletes an entry row and its files.
 */
@Service
public class RecentlyDeletedSweeper {

    /** How long an entry waits in Recently deleted before the sweep erases it (D-30). */
    public static final Duration RETENTION = Duration.ofDays(30);

    private static final Logger log = LoggerFactory.getLogger(RecentlyDeletedSweeper.class);

    private final EntryRepository entries;
    private final EntryErasure erasure;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public RecentlyDeletedSweeper(
            EntryRepository entries, EntryErasure erasure, PlatformTransactionManager transactions, Clock clock) {
        this.entries = entries;
        this.erasure = erasure;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /**
     * Erases every entry that was deleted more than {@link #RETENTION} before now: one deleted exactly 30 days ago is
     * still kept, and goes at the first sweep after that. Entries younger than that are left alone.
     *
     * @return how many entries this call erased; {@code 0} when there was nothing to do
     */
    public int sweep() {
        Instant cutoff = Instant.now(clock).minus(RETENTION);
        List<UUID> due = entries.findDeletedIdsBefore(cutoff);
        int erased = 0;
        for (UUID id : due) {
            try {
                if (eraseIfStillDue(id, cutoff)) {
                    erased++;
                }
            } catch (RuntimeException failure) {
                // The id and the kind of failure, never its message: a database message can carry the row. The entry
                // is still in Recently deleted and still due, so the next sweep tries it again.
                log.warn(
                        "Recently deleted: entry {} was not erased ({})",
                        id,
                        failure.getClass().getName());
            }
        }
        return erased;
    }

    // TODO(human) PRACTICE: Write eraseIfStillDue: erase one entry from the sweep's list, in a transaction of its own,
    //   but only if it is still due. The list was read a moment ago with no lock, so the user may have restored or
    //   erased the entry since, or may be doing so right now. Decide what the transaction does about each, and what
    //   it returns.
    // Hint: D-30 and EntryRepository.findDeletedForSweep. The finder checks "still in Recently deleted and older than
    //   the cutoff" under a lock that does not wait (SKIP LOCKED), so the check and the claim on the row are one step
    //   and nothing can restore the entry between them. Use `transaction` (a TransactionTemplate, because a
    //   @Transactional method called from inside this class would not go through the proxy) and EntryErasure.erase,
    //   which has to run inside the transaction.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    private boolean eraseIfStillDue(UUID id, Instant cutoff) {
        return Boolean.TRUE.equals(transaction.execute(status -> entries.findDeletedForSweep(id, cutoff)
                .map(entry -> {
                    erasure.erase(entry);
                    return true;
                })
                .orElse(false)));
    }
    // └── END ANSWER ▲ ──
}

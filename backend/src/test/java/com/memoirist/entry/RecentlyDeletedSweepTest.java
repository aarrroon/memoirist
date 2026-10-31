package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.memoirist.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The sweep that erases entries after 30 days in Recently deleted (task 3.11, D-30), called as {@code sweep()} on a
 * clock the test moves, the way the daily job and Sprint 5's outside trigger call it. The 30-day line, the files, a
 * second call, a restore, a failing entry and a row that is locked at that moment are each a test below.
 *
 * <p>Entries are deleted on {@link #DAY_0} and the clock is then moved to the day the sweep should run.
 */
class RecentlyDeletedSweepTest extends RecentlyDeletedApiTest {

    /** The day the entries below are deleted: 30 days before 2026-10-31, the day the clock starts every test on. */
    private static final String DAY_0 = "2026-10-01T00:00:00Z";

    private static final Duration THIRTY_DAYS = Duration.ofDays(30);

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    EntryRepository entries;

    private void sweepClockAt(Instant moment) {
        clock.set(moment);
    }

    private static Instant afterDay0(Duration wait) {
        return Instant.parse(DAY_0).plus(wait);
    }

    // The 30-day rule

    @Test
    void anEntryDeletedMoreThanThirtyDaysAgoIsErasedWithItsFiles() {
        User alice = aUser();
        String id = anEntryWithPhotos(alice, W1, "Old", 2);
        List<String> keys = objectKeysOf(id);
        deleteAt(DAY_0, alice, id);
        sweepClockAt(afterDay0(THIRTY_DAYS.plusDays(1)));

        int erased = sweeper.sweep();

        assertThat(erased).isEqualTo(1);
        assertThat(rowExists(id)).isFalse();
        assertThat(mediaRowCount(id)).isZero();
        assertThat(keys).allSatisfy(key -> assertThat(stored(key)).isFalse());
        assertThat(deletedIds(alice)).isEmpty();
    }

    @Test
    void anEntryYoungerThanThirtyDaysIsLeftAloneWithItsFiles() {
        User alice = aUser();
        String id = anEntryWithPhotos(alice, W1, "Young", 1);
        String key = objectKeysOf(id).get(0);
        deleteAt(DAY_0, alice, id);
        sweepClockAt(afterDay0(THIRTY_DAYS.minusDays(1)));

        int erased = sweeper.sweep();

        assertThat(erased).isZero();
        assertThat(deletedIds(alice)).containsExactly(id);
        assertThat(stored(key)).isTrue();
    }

    // TODO(human) PRACTICE: Write the test for the edge of the 30-day rule, with the movable clock. D-30 says an entry
    //   waits 30 days and the sprint plan says the sweep erases entries "deleted more than 30 days ago". Pin down what
    //   happens one second before, exactly at, and one second after the 30 days are over, and what the same entry still
    //   allows (restore) at the edge. Write down which side the edge falls on, so a change of `<` to `<=` is noticed.
    // Hint: delete at DAY_0, then move the clock with sweepClockAt(afterDay0(...)) and call sweeper.sweep() each time.
    //   The repository query is `deleted_at < cutoff` with cutoff = now - 30 days, and a sweep that erases nothing
    //   returns zero. Check the list as well as the return value, and finish with the entry restorable at the edge.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    @Test
    void theThirtyDayLineIsStrictSoAnEntryDeletedExactlyThirtyDaysAgoIsKeptAndAMomentLaterIsNot() {
        User alice = aUser();
        String id = anEntryWithPhotos(alice, W1, "On the line", 1);
        deleteAt(DAY_0, alice, id);

        sweepClockAt(afterDay0(THIRTY_DAYS.minusSeconds(1)));
        assertThat(sweeper.sweep()).as("one second short of 30 days").isZero();

        sweepClockAt(afterDay0(THIRTY_DAYS));
        assertThat(sweeper.sweep())
                .as("exactly 30 days: not more than 30 days yet")
                .isZero();
        assertThat(deletedIds(alice)).containsExactly(id);
        // The user can still save it on the day itself.
        restore(alice, id).expectStatus().isOk();
        deleteAt("2026-10-31T01:00:00Z", alice, id);

        // Deleted again on 2026-10-31, so the 30 days start over; this is the same edge from the other side.
        sweepClockAt(Instant.parse("2026-10-31T01:00:00Z").plus(THIRTY_DAYS).plusSeconds(1));
        assertThat(sweeper.sweep()).as("one second over 30 days").isEqualTo(1);
        assertThat(rowExists(id)).isFalse();
    }
    // └── END ANSWER ▲ ──

    @Test
    void deletingAgainStartsTheThirtyDaysOver() {
        User alice = aUser();
        String id = entryOn(alice, W1, "Back and forth");
        deleteAt(DAY_0, alice, id);
        clockAt("2026-10-11T00:00:00Z");
        restore(alice, id).expectStatus().isOk();
        deleteAt("2026-10-20T00:00:00Z", alice, id);

        sweepClockAt(afterDay0(THIRTY_DAYS.plusDays(5)));
        assertThat(sweeper.sweep())
                .as("35 days after the first delete, 15 after the second")
                .isZero();

        sweepClockAt(Instant.parse("2026-11-20T00:00:00Z"));
        assertThat(sweeper.sweep()).as("31 days after the second delete").isEqualTo(1);
    }

    @Test
    void aRestoredEntryIsNeverErasedByTheSweep() {
        User alice = aUser();
        String id = anEntryWithPhotos(alice, W1, "Saved in time", 1);
        String key = objectKeysOf(id).get(0);
        deleteAt(DAY_0, alice, id);
        clockAt("2026-10-25T00:00:00Z");
        restore(alice, id).expectStatus().isOk();
        sweepClockAt(afterDay0(Duration.ofDays(90)));

        assertThat(sweeper.sweep()).isZero();

        assertThat(rowExists(id)).isTrue();
        assertThat(stored(key)).isTrue();
        getEntry(alice, id);
    }

    // What the sweep touches

    @Test
    void theSweepErasesEveryUsersOldEntriesAndNobodysYoungOnesOrLiveOnes() {
        User alice = aUser();
        User bob = aUser();
        String aliceOld = anEntryWithPhotos(alice, W1, "Alice old", 1);
        String aliceYoung = anEntryWithPhotos(alice, W1, "Alice young", 1);
        String aliceLive = anEntryWithPhotos(alice, W1, "Alice live", 1);
        String bobOld = anEntryWithPhotos(bob, W1, "Bob old", 1);
        List<String> kept =
                List.of(objectKeysOf(aliceYoung).get(0), objectKeysOf(aliceLive).get(0));
        deleteAt(DAY_0, alice, aliceOld);
        deleteAt(DAY_0, bob, bobOld);
        deleteAt("2026-10-20T00:00:00Z", alice, aliceYoung);
        sweepClockAt(afterDay0(THIRTY_DAYS.plusDays(2)));

        assertThat(sweeper.sweep()).isEqualTo(2);

        assertThat(rowExists(aliceOld)).isFalse();
        assertThat(rowExists(bobOld)).isFalse();
        assertThat(deletedIds(alice)).containsExactly(aliceYoung);
        assertThat(rowExists(aliceLive)).isTrue();
        assertThat(kept).allSatisfy(key -> assertThat(stored(key)).isTrue());
    }

    @Test
    void aSealedEntryIsErasedLikeAnyOtherAfterThirtyDays() {
        User alice = aUser();
        String drop = aLootDrop(alice, "Sealed for long", SEALED_UNTIL);
        String key = aStoredPhoto(drop, 0);
        deleteAt(DAY_0, alice, drop);
        sweepClockAt(afterDay0(THIRTY_DAYS.plusDays(1)));

        assertThat(sweeper.sweep()).isEqualTo(1);

        assertThat(rowExists(drop)).isFalse();
        assertThat(stored(key)).isFalse();
    }

    // Safe to repeat

    @Test
    void aSecondSweepFindsNothingToDoAndAnEmptyDatabaseIsNotAnError() {
        User alice = aUser();
        String id = entryOn(alice, W1, "Once");
        deleteAt(DAY_0, alice, id);
        sweepClockAt(afterDay0(THIRTY_DAYS.plusDays(1)));

        assertThat(sweeper.sweep()).isEqualTo(1);
        assertThat(sweeper.sweep()).as("the same day, again").isZero();
        assertThat(sweeper.sweep()).as("and again").isZero();
    }

    // A failing entry and a locked row

    @Test
    void anEntryThatCannotBeErasedDoesNotStopTheOthersAndIsTriedAgainAtTheNextSweep() {
        User alice = aUser();
        String first = anEntryWithPhotos(alice, W1, "First", 1);
        String broken = anEntryWithPhotos(alice, W1, "Broken", 1);
        String last = anEntryWithPhotos(alice, W1, "Last", 1);
        String brokenKey = objectKeysOf(broken).get(0);
        deleteAt("2026-10-01T00:00:00Z", alice, first);
        deleteAt("2026-10-01T01:00:00Z", alice, broken);
        deleteAt("2026-10-01T02:00:00Z", alice, last);
        sweepClockAt(afterDay0(THIRTY_DAYS.plusDays(1)));
        // Asking for the broken entry's files to be deleted fails, inside the erase, before the commit.
        doThrow(new IllegalStateException("storage is on fire"))
                .when(storage)
                .deleteAfterCommit(argThat((Collection<String> keys) -> keys.contains(brokenKey)));

        int erased = sweeper.sweep();

        assertThat(erased).as("the one before it and the one after it").isEqualTo(2);
        assertThat(rowExists(first)).isFalse();
        assertThat(rowExists(last)).isFalse();
        assertThat(rowExists(broken)).as("rolled back, still waiting").isTrue();
        assertThat(mediaRowCount(broken)).isEqualTo(1);
        assertThat(stored(brokenKey)).isTrue();

        Mockito.reset(storage);

        assertThat(sweeper.sweep()).as("the next sweep tries it again").isEqualTo(1);
        assertThat(rowExists(broken)).isFalse();
        assertThat(stored(brokenKey)).isFalse();
    }

    @Test
    void aRowThatAnotherTransactionHasLockedIsSkippedWithoutWaitingAndErasedByALaterSweep() {
        User alice = aUser();
        String id = entryOn(alice, W1, "Busy");
        deleteAt(DAY_0, alice, id);
        sweepClockAt(afterDay0(THIRTY_DAYS.plusDays(1)));
        TransactionTemplate transaction = new TransactionTemplate(transactions);

        int whileLocked = transaction.execute(status -> {
            // What an erase or a restore does for the length of its transaction.
            entries.findDeletedForUpdateByIdAndUserId(id(id), alice.getId()).orElseThrow();
            return sweepOnAnotherThread();
        });

        assertThat(whileLocked)
                .as("the sweep did not wait for the lock, and did not erase the row")
                .isZero();
        assertThat(rowExists(id)).isTrue();
        assertThat(sweeper.sweep()).as("the lock is gone: now it is erased").isEqualTo(1);
        assertThat(rowExists(id)).isFalse();
    }

    @Test
    void anEntryBeingRestoredAtThatMomentIsNotErasedWhenTheRestoreCommits() {
        User alice = aUser();
        String id = entryOn(alice, W1, "Being saved");
        deleteAt(DAY_0, alice, id);
        sweepClockAt(afterDay0(THIRTY_DAYS.plusDays(1)));
        TransactionTemplate transaction = new TransactionTemplate(transactions);

        int whileRestoring = transaction.execute(status -> {
            Entry entry = entries.findDeletedForUpdateByIdAndUserId(id(id), alice.getId())
                    .orElseThrow();
            entry.setDeletedAt(null);
            entries.saveAndFlush(entry);
            return sweepOnAnotherThread();
        });

        assertThat(whileRestoring).isZero();
        assertThat(sweeper.sweep())
                .as("restored, so not in Recently deleted any more")
                .isZero();
        assertThat(rowExists(id)).isTrue();
        assertThat(deletedAt(id)).isNull();
    }

    // Helpers

    private static java.util.UUID id(String text) {
        return java.util.UUID.fromString(text);
    }

    /** Runs the sweep in a thread of its own, which is a transaction of its own, and waits for it (not for ever). */
    private int sweepOnAnotherThread() {
        return CompletableFuture.supplyAsync(sweeper::sweep)
                .orTimeout(30, TimeUnit.SECONDS)
                .join();
    }
}

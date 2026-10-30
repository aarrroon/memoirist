package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Optimistic locking on {@link Entry#getVersion()} (D-23, task 2.4): two devices edit the same entry, and the second
 * save is refused instead of silently overwriting the first.
 *
 * <p>This test class is <em>not</em> {@code @Transactional}. Two people editing at once means two transactions that
 * both commit, and a test-managed transaction that rolls back could never show that. Each step uses its own
 * {@link TransactionTemplate}, so the rows really are committed. {@link #removeTheTestUser()} deletes them afterwards
 * (a user's entries and weeks go with it, through {@code ON DELETE CASCADE}).
 */
@IntegrationTest
class EntryVersionTest {

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    UserRepository users;

    @Autowired
    WeeklyLogRepository weeks;

    @Autowired
    EntryRepository entries;

    TransactionTemplate tx;
    TransactionTemplate separateTx;
    UUID userId;
    UUID entryId;

    @BeforeEach
    void anEntryAtVersionZero() {
        tx = new TransactionTemplate(transactionManager);
        // REQUIRES_NEW: even inside another transaction, run in a new one with its own connection. That is how the
        // test plays "the other device" in the middle of the first device's transaction.
        separateTx = new TransactionTemplate(transactionManager);
        separateTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        tx.executeWithoutResult(status -> {
            User user = users.save(Fixtures.user());
            WeeklyLog week = weeks.save(Fixtures.week(user.getId()));
            Entry entry = entries.save(Fixtures.entry(user.getId(), week));
            userId = user.getId();
            entryId = entry.getId();
        });
    }

    @AfterEach
    void removeTheTestUser() {
        jdbc.sql("DELETE FROM users WHERE id = :id").param("id", userId).update();
    }

    @Test
    void theVersionStartsAtZeroAndGoesUpByOneOnEveryChange() {
        assertThat(versionInDatabase()).isZero();

        tx.executeWithoutResult(status -> load().setTitle("First edit"));
        assertThat(versionInDatabase()).isEqualTo(1L);

        tx.executeWithoutResult(status -> load().setTitle("Second edit"));
        assertThat(versionInDatabase()).isEqualTo(2L);

        tx.executeWithoutResult(status -> load().setTitle("Second edit"));
        assertThat(versionInDatabase())
                .as("saving the same values again is not a change, so the version stays")
                .isEqualTo(2L);
    }

    @Test
    void savingACopyThatAnotherDeviceHasSinceChangedIsRefused() {
        // Device A reads the entry at version 0 and keeps the copy while the user types.
        Entry copyOnDeviceA = tx.execute(status -> load());
        // Device B reads it, edits it and saves first: version 1.
        tx.executeWithoutResult(status -> load().setTitle("Edited on device B"));

        // Device A saves its copy. Hibernate compares its version (0) with the row's (1).
        copyOnDeviceA.setTitle("Edited on device A");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> entries.save(copyOnDeviceA)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(load(tx).getTitle()).as("device B's edit survives").isEqualTo("Edited on device B");
        assertThat(versionInDatabase()).isEqualTo(1L);
    }

    @Test
    void anEditThatRacesAnotherDevicesCommitInsideOneTransactionIsRefused() {
        // The check the service can't do: it compared versions, they matched, and then the other device committed
        // before this transaction did. The UPDATE says "where id = ? and version = 0", finds no row, and Hibernate
        // reports that the row was changed by someone else.
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    Entry mine = load(); // version 0
                    separateTx.executeWithoutResult(other -> load().setTitle("The other device got there first"));
                    mine.setTitle("Mine, based on stale data");
                }))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(load(tx).getTitle()).isEqualTo("The other device got there first");
    }

    private Entry load() {
        return entries.findByIdAndUserId(entryId, userId).orElseThrow();
    }

    private Entry load(TransactionTemplate template) {
        return template.execute(status -> load());
    }

    private long versionInDatabase() {
        return jdbc.sql("SELECT version FROM entries WHERE id = :id")
                .param("id", entryId)
                .query(Long.class)
                .single();
    }
}

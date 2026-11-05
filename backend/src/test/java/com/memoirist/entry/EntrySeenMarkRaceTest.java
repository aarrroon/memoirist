package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What happens when a {@code mark-seen} commits between another request's read of the entry and its save (task 3.10).
 * It is the one place where the seen mark has two writers, so the two outcomes are written down here as tests:
 *
 * <ul>
 *   <li><b>A save that did not touch the mark writes its old value back.</b> {@code Entry} is saved with a full-row
 *       {@code UPDATE} (no {@code @DynamicUpdate}), so a {@code PUT} that read the entry before the mark landed puts the
 *       mark back to empty. Nothing is refused (the mark does not raise {@code version}), and the cost is that the
 *       Opened strip shows that Loot Drop once more. That is harmless, and the first test pins it.
 *   <li><b>A seal always wins over a mark.</b> {@code seal} sets the mark to empty. If a {@code mark-seen} lands between
 *       its read and its write, the full-row {@code UPDATE} still writes empty, so a sealed Loot Drop never keeps a
 *       seen mark from before the seal (which would hide it from the strip when it opens again). The second test
 *       guards that. Adding {@code @DynamicUpdate} to {@code Entry} would make the first outcome better and this one
 *       wrong: Hibernate would see "empty to empty" as no change and leave the mark in place.
 * </ul>
 *
 * <p>Like {@link EntryVersionTest}, this class is not {@code @Transactional}: two requests that both commit need two
 * real transactions, and {@link #removeTheTestUser()} deletes the rows afterwards.
 */
@IntegrationTest
class EntrySeenMarkRaceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private static final Instant OPENED = NOW.minusSeconds(86_400);
    private static final Instant SEALED_UNTIL = NOW.plusSeconds(86_400);

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
    TransactionTemplate otherRequestTx;
    UUID userId;
    UUID entryId;

    @BeforeEach
    void anOpenedLootDropThatHasNotBeenSeen() {
        tx = new TransactionTemplate(transactionManager);
        // REQUIRES_NEW plays "the other request" in the middle of the first one's transaction, on its own connection.
        otherRequestTx = new TransactionTemplate(transactionManager);
        otherRequestTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        tx.executeWithoutResult(status -> {
            User user = users.save(Fixtures.user());
            WeeklyLog week = weeks.save(Fixtures.week(user.getId()));
            Entry entry = Fixtures.entry(user.getId(), week);
            entry.setUnlockDate(OPENED);
            entries.save(entry);
            userId = user.getId();
            entryId = entry.getId();
        });
    }

    @AfterEach
    void removeTheTestUser() {
        jdbc.sql("DELETE FROM users WHERE id = :id").param("id", userId).update();
    }

    @Test
    void aSaveThatReadTheEntryBeforeTheMarkLandedIsNotRefusedAndWritesTheEmptyMarkBack() {
        tx.executeWithoutResult(status -> {
            Entry mine = load(); // no seen mark, version 0
            otherRequestTx.executeWithoutResult(
                    other -> assertThat(entries.markSeen(entryId, userId, NOW)).isOne());
            mine.setTitle("Edited at the moment the strip was tapped");
        });

        assertThat(titleInDatabase())
                .as("the save was not refused: the mark is not a new version")
                .isNotNull();
        assertThat(seenMarkInDatabase()).as("the save wrote what it had read").isNull();
    }

    @Test
    void aSealAlwaysLeavesTheMarkEmptyEvenIfAMarkLandedBetweenItsReadAndItsWrite() {
        tx.executeWithoutResult(status -> {
            Entry mine = load();
            otherRequestTx.executeWithoutResult(
                    other -> assertThat(entries.markSeen(entryId, userId, NOW)).isOne());
            // What EntryService.seal does to the entity it read.
            mine.setUnlockDate(SEALED_UNTIL);
            mine.setUnlockSeenAt(null);
        });

        assertThat(unlockDateInDatabase()).as("the seal was saved").isEqualTo(SEALED_UNTIL);
        assertThat(seenMarkInDatabase())
                .as("a sealed Loot Drop must not keep a seen mark from before the seal")
                .isNull();
    }

    private Entry load() {
        return entries.findByIdAndUserId(entryId, userId).orElseThrow();
    }

    private Instant seenMarkInDatabase() {
        return jdbc.sql("SELECT unlock_seen_at FROM entries WHERE id = :id")
                .param("id", entryId)
                .query((rs, row) ->
                        rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant())
                .list()
                .getFirst();
    }

    private Instant unlockDateInDatabase() {
        return jdbc.sql("SELECT unlock_date FROM entries WHERE id = :id")
                .param("id", entryId)
                .query((rs, row) -> rs.getTimestamp(1).toInstant())
                .single();
    }

    private String titleInDatabase() {
        return jdbc.sql("SELECT title FROM entries WHERE id = :id")
                .param("id", entryId)
                .query(String.class)
                .single();
    }
}

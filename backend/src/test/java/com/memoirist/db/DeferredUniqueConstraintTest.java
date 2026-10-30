package com.memoirist.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memoirist.IntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code uq_media_entry_order} is {@code DEFERRABLE INITIALLY DEFERRED}: Postgres checks it when the transaction
 * commits, not after each statement. That is what lets a reorder swap two positions in one transaction (task 2.3).
 *
 * <p>This test class has no {@code @Transactional}. The behaviour under test happens <em>at commit</em>, and a test
 * transaction that is always rolled back never commits. So the test opens and commits real transactions with a
 * {@link TransactionTemplate}, and removes its rows afterwards.
 */
@IntegrationTest
class DeferredUniqueConstraintTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate transaction;

    private final UUID user = UUID.randomUUID();
    private final UUID week = UUID.randomUUID();
    private final UUID entry = UUID.randomUUID();
    private final UUID photoA = UUID.randomUUID();
    private final UUID photoB = UUID.randomUUID();

    @BeforeEach
    void commitOneEntryWithTwoPhotos() {
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            jdbc.sql("INSERT INTO users (id, email) VALUES (:id, :email)")
                    .param("id", user)
                    .param("email", user + "@example.test")
                    .update();
            jdbc.sql("INSERT INTO weekly_logs (id, user_id, start_date, end_date)"
                            + " VALUES (:id, :user, DATE '2026-10-26', DATE '2026-11-01')")
                    .param("id", week)
                    .param("user", user)
                    .update();
            jdbc.sql("INSERT INTO entries (id, user_id, weekly_log_id, entry_date)"
                            + " VALUES (:id, :user, :week, DATE '2026-10-28')")
                    .param("id", entry)
                    .param("user", user)
                    .param("week", week)
                    .update();
            insertPhoto(photoA, 0);
            insertPhoto(photoB, 1);
        });
    }

    @AfterEach
    void deleteEverythingTheTestCommitted() {
        // The foreign keys cascade from the user down to the media, so one delete removes it all.
        jdbc.sql("DELETE FROM users WHERE id = :id").param("id", user).update();
    }

    @Test
    void twoPhotosCanSwapPositionsInOneTransaction() {
        transaction.executeWithoutResult(status -> {
            move(photoA, 1); // photoB is also at 1 now: an immediate unique check would fail here
            move(photoB, 0);
        });

        assertThat(photosInOrder()).containsExactly(photoB, photoA);
    }

    @Test
    void aClashingPositionOnlyFailsWhenTheTransactionCommits() {
        // TODO(human) PRACTICE: Show that moving photoA onto photoB's position is accepted by the UPDATE itself and
        //   rejected only when the transaction commits, and that the failed commit leaves the old order in place.
        //   Write it with the TransactionTemplate that @BeforeEach created.
        // Hint: the constraint is DEFERRABLE INITIALLY DEFERRED (DATABASE_SCHEMA.md, media). How can the test prove
        //   the failure came at the commit, not at the statement? Which exception does Spring throw for a violation
        //   that Postgres reports from the COMMIT? (Run it once with a wrong guess and read the failure.)
        // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
        AtomicBoolean updateWasAccepted = new AtomicBoolean();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                    move(photoA, 1); // photoB is already at 1
                    updateWasAccepted.set(true); // reached only if the UPDATE did not throw
                }))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_media_entry_order");

        assertThat(updateWasAccepted).isTrue();
        assertThat(photosInOrder()).containsExactly(photoA, photoB);
        // └── END ANSWER ▲ ──
    }

    @Test
    void settingTheConstraintImmediateMakesTheSameMoveFailAtTheStatement() {
        AtomicBoolean updateWasAccepted = new AtomicBoolean();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                    jdbc.sql("SET CONSTRAINTS uq_media_entry_order IMMEDIATE").update();
                    move(photoA, 1);
                    updateWasAccepted.set(true);
                }))
                .hasMessageContaining("uq_media_entry_order");

        assertThat(updateWasAccepted).isFalse();
    }

    private void insertPhoto(UUID id, int position) {
        jdbc.sql("INSERT INTO media (id, entry_id, object_key, media_type, display_order, file_size_bytes)"
                        + " VALUES (:id, :entry, :key, 'image/jpeg', :position, 1024)")
                .param("id", id)
                .param("entry", entry)
                .param("key", "users/" + user + "/" + id + ".jpg")
                .param("position", position)
                .update();
    }

    private void move(UUID photo, int position) {
        jdbc.sql("UPDATE media SET display_order = :position WHERE id = :id")
                .param("position", position)
                .param("id", photo)
                .update();
    }

    private List<UUID> photosInOrder() {
        return jdbc.sql("SELECT id FROM media WHERE entry_id = :entry ORDER BY display_order")
                .param("entry", entry)
                .query(UUID.class)
                .list();
    }
}

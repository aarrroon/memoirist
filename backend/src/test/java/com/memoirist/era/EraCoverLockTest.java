package com.memoirist.era;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memoirist.entry.RecentlyDeletedApiTest;
import com.memoirist.media.MediaRepository;
import com.memoirist.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The lock behind a cover (task 3.15): checking a cover photo takes {@code FOR SHARE} on its row until the transaction
 * ends, so deleting the photo, or erasing its entry (the cascade deletes the row), has to wait until the Era has been
 * written. Without it the delete could land between the check and the insert, and the foreign key would fail with a
 * {@code 500}. The same proof as {@code EntryReplyLockTest}: {@code NOWAIT} turns "would block" into an error, and a
 * second transaction of its own (REQUIRES_NEW) is a second connection, because a lock never blocks its own holder.
 */
class EraCoverLockTest extends RecentlyDeletedApiTest {

    private static final String TAKE_LOCK = "SELECT id FROM media WHERE id = :id FOR %s NOWAIT";

    @Autowired
    MediaRepository mediaRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    private void lockWith(String mode, String mediaId) {
        TransactionTemplate otherRequest = new TransactionTemplate(transactionManager);
        otherRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        otherRequest.executeWithoutResult(status -> jdbc.sql(TAKE_LOCK.formatted(mode))
                .param("id", UUID.fromString(mediaId))
                .query()
                .listOfRows());
    }

    private String aPhotoId(User owner) {
        String entryId = entryOn(owner, W4, "Beach");
        return aPhoto(entryId, "users/test/covers/lock-" + entryId + ".jpg", 0).toString();
    }

    @Test
    void whileACoverIsBeingCheckedNoDeleteCanTakeThePhotoRow() {
        User alice = aUser();
        String photo = aPhotoId(alice);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(mediaRepository.findAndLockByIdAndEntryUserId(UUID.fromString(photo), alice.getId()))
                    .isPresent();

            assertThatThrownBy(() -> lockWith("UPDATE", photo))
                    .as("a delete of the photo has to wait for the Era")
                    .hasMessageContaining("could not obtain lock");
        });

        lockWith("UPDATE", photo); // the transaction is over, so the lock is free again
    }

    @Test
    void twoErasCanCheckTheSamePhotoAtTheSameTime() {
        User alice = aUser();
        String photo = aPhotoId(alice);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            mediaRepository.findAndLockByIdAndEntryUserId(UUID.fromString(photo), alice.getId());

            lockWith("SHARE", photo); // another Era: no wait, no error
        });
    }

    @Test
    void theFinderIsStillScopedToTheUserAndLeavesOutRecentlyDeleted() {
        User alice = aUser();
        User bob = aUser();
        String entryId = entryOn(alice, W4, "Beach");
        String photo = aPhoto(entryId, "users/test/covers/scoped.jpg", 0).toString();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(mediaRepository.findAndLockByIdAndEntryUserId(UUID.fromString(photo), bob.getId()))
                    .isEmpty();
            assertThat(mediaRepository.findAndLockByIdAndEntryUserId(UUID.fromString(photo), alice.getId()))
                    .isPresent();
        });

        deleteOk(alice, entryId);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> assertThat(
                                mediaRepository.findAndLockByIdAndEntryUserId(UUID.fromString(photo), alice.getId()))
                        .isEmpty());
    }
}

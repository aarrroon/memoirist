package com.memoirist.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.memoirist.common.StorageUnavailableException;
import com.memoirist.entry.Entry;
import com.memoirist.entry.EntryErasure;
import com.memoirist.entry.EntryRepository;
import com.memoirist.user.User;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stored files and the rows that point at them (D-30), in the cases where the two can drift apart:
 *
 * <ul>
 *   <li>the life of an entry's files: deleting the entry keeps them, erasing it removes them, a rolled-back erasure
 *       keeps them;
 *   <li>an upload that fails after its file is stored: the file is taken out again, whichever way the write fails.
 * </ul>
 */
class MediaCleanupTest extends MediaApiTest {

    private static final String SOME_DAY_AHEAD = "2026-11-30T00:00:00Z";

    @Autowired
    EntryErasure erasure;

    @Autowired
    EntryRepository entries;

    @Autowired
    PlatformTransactionManager transactions;

    // An entry's files over its life (D-30)

    @Test
    void deletingAnEntryKeepsItsRowsAndItsFiles() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        List<String> ids = uploadPhotos(user, entryId, 2);
        List<String> keys = objectKeysOf(entryId);

        delete(user, entryId).expectStatus().isNoContent();

        assertThat(row(entryId).get("deleted_at"))
                .as("it waits in Recently deleted")
                .isNotNull();
        assertThat(idsInOrder(entryId)).isEqualTo(ids);
        assertThat(keys).allSatisfy(key -> assertThat(stored(key)).isTrue());
    }

    @Test
    void anEntryWithOnlyAPhotoIsNotEmptyAndSoItsFileIsKeptToo() {
        User user = aUser();
        String entryId = createEntryId(user, json());
        uploadPhoto(user, entryId);
        String key = objectKeysOf(entryId).get(0);

        // No title and no caption, but a photo: there is something to bring back (D-30).
        delete(user, entryId).expectStatus().isNoContent();

        assertThat(rowExists(entryId)).isTrue();
        assertThat(stored(key)).isTrue();
    }

    @Test
    void erasingAnEntryInRecentlyDeletedRemovesItsRowsAndItsFiles() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhotos(user, entryId, 3);
        List<String> keys = objectKeysOf(entryId);
        delete(user, entryId).expectStatus().isNoContent();

        eraseInATransaction(user, entryId);

        assertThat(rowExists(entryId)).as("the entry").isFalse();
        assertThat(mediaRowCount(entryId)).as("its media rows, by cascade").isZero();
        assertThat(keys)
                .as("its files")
                .allSatisfy(key -> assertThat(stored(key)).isFalse());
    }

    @Test
    void erasingOneEntryLeavesAnotherEntrysFilesAlone() {
        User user = aUser();
        String erased = createTitledEntry(user);
        String kept = createTitledEntry(user);
        uploadPhoto(user, erased);
        uploadPhoto(user, kept);
        String keptKey = objectKeysOf(kept).get(0);
        delete(user, erased).expectStatus().isNoContent();

        eraseInATransaction(user, erased);

        assertThat(stored(keptKey)).isTrue();
        assertThat(mediaRowCount(kept)).isEqualTo(1);
    }

    @Test
    void anErasureThatRollsBackLosesNoFile() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhotos(user, entryId, 2);
        List<String> keys = objectKeysOf(entryId);
        delete(user, entryId).expectStatus().isNoContent();

        // The files are deleted after the commit, so a transaction that doesn't commit deletes nothing.
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    erasure.erase(entries.findDeletedByIdAndUserId(UUID.fromString(entryId), user.getId())
                            .orElseThrow());
                    throw new IllegalStateException("something later in the same transaction failed");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(rowExists(entryId)).isTrue();
        assertThat(mediaRowCount(entryId)).isEqualTo(2);
        assertThat(keys).allSatisfy(key -> assertThat(stored(key)).isTrue());
    }

    @Test
    void anErasureOutsideATransactionIsRefusedAndDeletesNothing() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhoto(user, entryId);
        String key = objectKeysOf(entryId).get(0);
        delete(user, entryId).expectStatus().isNoContent();
        Entry entry = entries.findDeletedByIdAndUserId(UUID.fromString(entryId), user.getId())
                .orElseThrow();

        // Without a transaction the files would go at once and the row afterwards: the wrong way round.
        assertThatThrownBy(() -> erasure.erase(entry)).isInstanceOf(IllegalTransactionStateException.class);

        assertThat(rowExists(entryId)).isTrue();
        assertThat(stored(key)).isTrue();
    }

    @Test
    void anEntryThatIsDeletedAndHasMediaCanBeErasedEvenAfterItsFilesWereAlreadyGone() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        String id = uploadPhoto(user, entryId).get("id").toString();
        String key = objectKeyOf(id);
        delete(user, entryId).expectStatus().isNoContent();
        storage.delete(key);

        // Deleting a key that isn't there is not an error, so a sweep that meets a missing file still finishes.
        eraseInATransaction(user, entryId);

        assertThat(rowExists(entryId)).isFalse();
    }

    private void eraseInATransaction(User user, String entryId) {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> erasure.erase(entries.findDeletedByIdAndUserId(UUID.fromString(entryId), user.getId())
                                .orElseThrow()));
    }

    // An upload that fails after its file is stored

    @Test
    void anEntrySealedWhileTheFileIsInFlightIs409AndTheFileIsTakenOut() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        AtomicReference<String> key = new AtomicReference<>();
        doAnswer(invocation -> {
                    Object stored = invocation.callRealMethod();
                    key.set(invocation.getArgument(0));
                    jdbc.sql("UPDATE entries SET unlock_date = :date WHERE id = :id")
                            .param("date", java.sql.Timestamp.from(java.time.Instant.parse(SOME_DAY_AHEAD)))
                            .param("id", UUID.fromString(entryId))
                            .update();
                    return stored;
                })
                .when(storage)
                .put(anyString(), any(InputStream.class), anyLong(), anyString());

        String body = bodyText(upload(user, entryId, bytes(300, 30), "image/jpeg")
                .expectStatus()
                .isEqualTo(409));

        assertThat(problemType(body)).isEqualTo("entry-sealed");
        assertThat(key.get()).isNotNull();
        assertThat(stored(key.get()))
                .as("the file that was stored a moment ago")
                .isFalse();
        assertThat(mediaRowCount(entryId)).isZero();
    }

    @Test
    void anEntryErasedWhileTheFileIsInFlightIs404AndTheFileIsTakenOut() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        AtomicReference<String> key = new AtomicReference<>();
        doAnswer(invocation -> {
                    Object stored = invocation.callRealMethod();
                    key.set(invocation.getArgument(0));
                    jdbc.sql("DELETE FROM entries WHERE id = :id")
                            .param("id", UUID.fromString(entryId))
                            .update();
                    return stored;
                })
                .when(storage)
                .put(anyString(), any(InputStream.class), anyLong(), anyString());

        upload(user, entryId, bytes(300, 31), "image/jpeg").expectStatus().isNotFound();

        assertThat(stored(key.get())).isFalse();
    }

    @Test
    void anEntryFilledWhileTheFileIsInFlightIs400AndTheFileIsTakenOut() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        uploadPhotos(user, entryId, 19);
        AtomicReference<String> key = new AtomicReference<>();
        doAnswer(invocation -> {
                    Object stored = invocation.callRealMethod();
                    key.set(invocation.getArgument(0));
                    // Another upload to the same entry wins the last place while this file is on its way.
                    aPhoto(entryId, "users/x/entries/y/the-twentieth.jpg", 19);
                    return stored;
                })
                .when(storage)
                .put(anyString(), any(InputStream.class), anyLong(), anyString());

        String body = bodyText(upload(user, entryId, bytes(300, 32), "image/jpeg")
                .expectStatus()
                .isBadRequest());

        assertThat(fieldErrors(body)).containsKey("file");
        assertThat(stored(key.get())).isFalse();
        assertThat(mediaRowCount(entryId)).isEqualTo(20);
    }

    @Test
    void aFailedCleanupDoesNotHideTheFailureThatCausedIt() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        AtomicReference<String> key = new AtomicReference<>();
        doAnswer(invocation -> {
                    Object stored = invocation.callRealMethod();
                    key.set(invocation.getArgument(0));
                    jdbc.sql("UPDATE entries SET unlock_date = :date WHERE id = :id")
                            .param("date", java.sql.Timestamp.from(java.time.Instant.parse(SOME_DAY_AHEAD)))
                            .param("id", UUID.fromString(entryId))
                            .update();
                    return stored;
                })
                .when(storage)
                .put(anyString(), any(InputStream.class), anyLong(), anyString());
        doThrow(new StorageUnavailableException("Deleting an object failed", new RuntimeException("storage is down")))
                .when(storage)
                .delete(anyString());

        // The user needs to hear that the entry is sealed, not that cleaning up after that went wrong.
        String body = bodyText(upload(user, entryId, bytes(300, 33), "image/jpeg")
                .expectStatus()
                .isEqualTo(409));

        assertThat(problemType(body)).isEqualTo("entry-sealed");
        verify(storage, times(1)).delete(key.get());
        assertThat(stored(key.get()))
                .as("left behind: wasted space, and nothing points at it")
                .isTrue();
        assertThat(mediaRowCount(entryId)).isZero();
    }

    @Test
    void storageBeingDownIs503WithNoRowAndNothingAboutTheHost() {
        User user = aUser();
        String entryId = createTitledEntry(user);
        doThrow(new StorageUnavailableException(
                        "Storing an object failed", new RuntimeException("connect to http://storage.invalid:8333")))
                .when(storage)
                .put(anyString(), any(InputStream.class), anyLong(), anyString());

        String body = bodyText(upload(user, entryId, bytes(300, 34), "image/jpeg")
                .expectStatus()
                .isEqualTo(503));

        assertThat(problemType(body)).isEqualTo("storage-unavailable");
        assertThat(body).doesNotContain("storage.invalid").doesNotContain("8333");
        assertThat(mediaRowCount(entryId)).isZero();
    }
}

package com.memoirist.entry;

import com.memoirist.media.MediaRepository;
import com.memoirist.storage.StorageService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases an entry for good: the row, its media rows and its stored files. This is the one place that does it
 * (D-30): the empty entry that {@code DELETE} erases at once calls it, and so will {@code POST /entries/{id}/erase}
 * and the daily sweep of entries 30 days in Recently deleted (task 3.11). Deleting an entry only moves it to
 * Recently deleted, and its files stay.
 *
 * <p>What it does, in this order:
 * <ol>
 *   <li>Reads the object keys of the entry's media <em>while the rows still exist</em>, as plain strings.
 *   <li>Deletes the {@code entries} row. The database removes the media rows with it ({@code ON DELETE CASCADE}).
 *   <li>Asks for the files to be deleted <em>after the commit</em> ({@link StorageService#deleteAfterCommit}). A
 *       rollback drops the request, so the files of an entry that wasn't erased are never lost.
 * </ol>
 *
 * <p>It requires a running transaction ({@link Propagation#MANDATORY}) and fails loudly without one. Called outside a
 * transaction, the file deletion would run at once while the row delete committed on its own afterwards, which is the
 * wrong way round: a failed delete would leave rows that point at files that are gone.
 */
@Component
public class EntryErasure {

    private final EntryRepository entries;
    private final MediaRepository media;
    private final StorageService storage;

    public EntryErasure(EntryRepository entries, MediaRepository media, StorageService storage) {
        this.entries = entries;
        this.media = media;
        this.storage = storage;
    }

    /**
     * @param entry an entry the caller has already found through a user-scoped lookup, live or in Recently deleted
     * @throws org.springframework.transaction.IllegalTransactionStateException if no transaction is running
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void erase(Entry entry) {
        // The keys come from a native query for the keys alone, not from entry.getMedia(), for two reasons that both
        // come from @SQLRestriction hiding Recently deleted entries. Loading the Media entities makes Hibernate, at the
        // flush that deletes the entry, check that each one's entry is saved, and for a deleted entry its check asks
        // the database through the restriction and fails with "Media references an unsaved transient instance of
        // Entry". And a JPQL key query joins the entry and so finds no keys, which leaks the files without an error.
        List<String> objectKeys = media.findObjectKeysByEntryId(entry.getId());
        entries.delete(entry);
        storage.deleteAfterCommit(objectKeys);
    }
}

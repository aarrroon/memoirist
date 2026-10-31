package com.memoirist.entry;

import com.memoirist.common.NotFoundException;
import com.memoirist.entry.dto.EntryResponse;
import com.memoirist.user.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the user can do with an entry in Recently deleted (D-30): bring it back, or erase it now. The list is
 * {@link EntryListService#deleted}, and the erase after 30 days is {@link RecentlyDeletedSweeper}. Putting an entry
 * there is {@link EntryService#delete}.
 *
 * <ul>
 *   <li><b>Ownership (non-negotiable 3).</b> The entry is found by id <em>and</em> the current user, among the user's
 *       deleted entries only. A live entry, another user's entry and one that doesn't exist are the same
 *       {@code 404}, so neither call can be used to find out what exists.
 *   <li><b>Turns.</b> The entry's row is locked while it is looked up ({@code FOR NO KEY UPDATE}). Two requests for the
 *       same entry (a double tap, two devices, a restore and an erase) take turns, and the one that comes second finds
 *       the entry gone from Recently deleted and answers {@code 404}.
 *   <li><b>The seal (D-10).</b> A sealed entry can be restored and erased; it stays sealed. The restored entry is
 *       built by {@link EntryMapper}, which hides it while it is sealed.
 * </ul>
 */
@Service
@Transactional
public class RecentlyDeletedService {

    private final EntryRepository entries;
    private final EntryErasure erasure;
    private final CurrentUserProvider currentUser;
    private final EntryMapper mapper;
    private final Clock clock;

    public RecentlyDeletedService(
            EntryRepository entries,
            EntryErasure erasure,
            CurrentUserProvider currentUser,
            EntryMapper mapper,
            Clock clock) {
        this.entries = entries;
        this.erasure = erasure;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Puts the entry back where it was: in its week, with its media, tags and seal exactly as they were. Nothing but
     * {@code deleted_at} changes, so the memory date, the week, the unlock date and the seen mark all stay. Like any
     * change to an entry, it raises {@code version} by one and moves {@code updatedAt}: a copy of the entry that a
     * client holds from before the delete is stale (D-23), and an AI result built from it would be too (D-21).
     *
     * <p>Works for any entry still in Recently deleted, including one whose 30 days ended a few hours ago and that the
     * daily sweep has not reached yet: the sweep is a clean-up, and it is never the clock that decides whether a
     * memory can be saved.
     *
     * @return the entry as the client may see it now: whole, or redacted if it is a sealed Loot Drop
     * @throws NotFoundException if the entry isn't one of the user's entries in Recently deleted
     */
    public EntryResponse restore(UUID entryId) {
        Entry entry = findDeletedForUpdate(entryId);
        entry.setDeletedAt(null);
        // Flushed before the response is built, so that the entry is a live row again when its media and tags are read
        // (they are loaded lazily by the mapper), and because Hibernate fills updatedAt and the version as it writes.
        entries.saveAndFlush(entry);
        return mapper.toResponse(entry, Instant.now(clock));
    }

    /**
     * Erases the entry for good, with its media rows and its stored files ({@link EntryErasure}). It can't be undone.
     * Safe to retry: a second call finds nothing and is {@code 404}.
     *
     * @throws NotFoundException if the entry isn't one of the user's entries in Recently deleted
     */
    public void erase(UUID entryId) {
        erasure.erase(findDeletedForUpdate(entryId));
    }

    private Entry findDeletedForUpdate(UUID entryId) {
        return entries.findDeletedForUpdateByIdAndUserId(entryId, currentUser.currentUserId())
                .orElseThrow(() -> new NotFoundException("Entry"));
    }
}

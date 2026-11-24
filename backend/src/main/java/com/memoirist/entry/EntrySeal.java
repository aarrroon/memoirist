package com.memoirist.entry;

import java.time.Instant;

/**
 * The one definition of "sealed" (D-10): an entry is a sealed Loot Drop while its unlock date is still ahead of
 * {@code now}. The service uses it to refuse edits and {@link EntryMapper} uses it to hide the entry, so the two
 * can't disagree about which entries are sealed.
 *
 * <p>The comparison is strict. At the very instant of the unlock date the entry is open, and that is the moment it
 * starts to count again (see {@code EntryRepository.countOpenEntriesByWeek}: {@code unlock_date <= now}).
 */
public final class EntrySeal {

    private EntrySeal() {}

    public static boolean isSealed(Entry entry, Instant now) {
        return isSealed(entry.getUnlockDate(), now);
    }

    public static boolean isSealed(Instant unlockDate, Instant now) {
        return unlockDate != null && unlockDate.isAfter(now);
    }
}

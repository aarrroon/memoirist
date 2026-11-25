import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo } from 'react';
import { queryKeys } from '../../api/queryKeys';

/** The longest a timer is set for; useNow's minute tick sets it again, so a far-off opening is never missed. */
const LONGEST = 86_400_000;

/**
 * Reads the screen's data again at the moment a sealed Loot Drop on it opens (F-07: "the card refreshes itself and
 * opens"), so its sealed card turns into the entry and the Opened row appears without a reload. The server decides
 * what has opened (its clock, D-10), so this only asks again; a second after the instant, and once a minute after
 * that (each `now` tick) while the server still says sealed, in case this device's clock runs ahead.
 */
export function useUnlockWatch(
  sealed: readonly { id: string; unlockDate: string | null }[],
  now: Date,
) {
  const queryClient = useQueryClient();
  const next = useMemo(() => {
    let soonest: { id: string; at: number } | null = null;
    for (const entry of sealed) {
      if (!entry.unlockDate) continue;
      const at = Date.parse(entry.unlockDate);
      if (!soonest || at < soonest.at) soonest = { id: entry.id, at };
    }
    return soonest;
  }, [sealed]);

  const minute = Math.floor(now.getTime() / 60_000);
  useEffect(() => {
    if (!next) return;
    const delay = Math.min(Math.max(next.at - Date.now(), 0) + 1000, LONGEST);
    const timer = setTimeout(() => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.feed() });
      void queryClient.invalidateQueries({ queryKey: queryKeys.opened() });
      void queryClient.invalidateQueries({ queryKey: queryKeys.entry(next.id) });
    }, delay);
    return () => clearTimeout(timer);
  }, [next, minute, queryClient]);
}

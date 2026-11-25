import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { api } from './client';
import { getEntry, type Entry } from './entries';
import { unwrap } from './problem';
import { queryKeys } from './queryKeys';
import type { components } from './schema';

// Loot Drops (task 4.8, D-10, D-23): sealing, one entry read whole or redacted, the Opened strip and its seen mark.
// The server decides what a sealed entry may show (EntryMapper); the client only ever draws what comes back.

/** A row of the Opened strip: every row is open, so whole (API_CONTRACT §Entries, The Opened strip). */
export type EntrySummary = components['schemas']['EntrySummaryResponse'];

/**
 * POST /entries/{id}/seal with the opening instant (09:00 on the chosen day in the user's zone, time.ts
 * unlockInstant). On a sealed entry it can only move later; an earlier one is 409 entry-sealed.
 */
export async function sealEntry(entryId: string, unlockDate: string): Promise<Entry> {
  return unwrap(
    await api.POST('/api/v1/entries/{entryId}/seal', {
      params: { path: { entryId } },
      body: { unlockDate },
    }),
  );
}

/** POST /entries/{id}/mark-seen: takes an opened Loot Drop out of the Opened strip. Safe to repeat. */
export async function markSeen(entryId: string): Promise<void> {
  unwrap(await api.POST('/api/v1/entries/{entryId}/mark-seen', { params: { path: { entryId } } }));
}

/** One entry: whole, or redacted while it is a sealed Loot Drop (GET /entries/{id}). */
export function useEntry(entryId: string) {
  return useQuery({
    queryKey: queryKeys.entry(entryId),
    queryFn: ({ signal }) => getEntry(entryId, signal),
  });
}

/** The Opened strip's Loot Drops (GET /entries/opened): opened and not yet seen, the newest first, at most 20. */
export function useOpened() {
  return useQuery({
    queryKey: queryKeys.opened(),
    queryFn: async ({ signal }) => unwrap(await api.GET('/api/v1/entries/opened', { signal })),
  });
}

/**
 * Seals an entry or extends its seal. Afterwards the feed, the entry and the Opened strip are read again: the entry
 * is now redacted everywhere, and a Loot Drop sealed again leaves the strip (API_CONTRACT, Coming back).
 */
export function useSeal(entryId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (unlockDate: string) => sealEntry(entryId, unlockDate),
    onSuccess: (sealed) => afterSeal(queryClient, sealed),
  });
}

/**
 * What every seal does to the cache, from the Loot Drop page or from capture (D-10). The server's redacted answer
 * replaces any whole copy of the entry under queryKeys.entry: capture reads the whole entry back when it restores a
 * saved draft or loads the latest version, and with a 30-minute staleTime that copy would otherwise be shown on the
 * Loot Drop page without a refetch. Then the feed and the Opened strip are read again.
 */
export async function afterSeal(queryClient: QueryClient, sealed: Entry): Promise<void> {
  queryClient.setQueryData(queryKeys.entry(sealed.id), sealed);
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: queryKeys.feed() }),
    queryClient.invalidateQueries({ queryKey: queryKeys.opened() }),
  ]);
}

/**
 * Marks an opened Loot Drop seen: its *Opened* row is dismissed, or the user opened it (D-10). The row goes at once,
 * before the server answers, and comes back if the request fails.
 */
export function useMarkSeen() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (entryId: string) => markSeen(entryId),
    // TODO(human) PRACTICE: Make the dismiss optimistic. onMutate runs before the request: stop any refetch of the
    // Opened strip that is under way, remember the list as it is, take this entry out of the cached list, and
    // return what onError needs to put it back. onError restores it; onSettled reads the strip again either way.
    // Hint: cancelQueries first, or a refetch that started before the click can land afterwards and bring the row
    // back. getQueryData / setQueryData work on queryKeys.opened(); the cache may be empty (undefined), so don't
    // write a list where there was none. What onMutate returns arrives as onError's third argument.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    onMutate: async (entryId: string) => {
      await queryClient.cancelQueries({ queryKey: queryKeys.opened() });
      const previous = queryClient.getQueryData<EntrySummary[]>(queryKeys.opened());
      if (previous) {
        queryClient.setQueryData<EntrySummary[]>(
          queryKeys.opened(),
          previous.filter((row) => row.id !== entryId),
        );
      }
      return { previous };
    },
    onError: (_error, _entryId, context) => {
      if (context?.previous) queryClient.setQueryData(queryKeys.opened(), context.previous);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.opened() }),
    // └── END ANSWER ▲ ──
  });
}

/** DELETE /entries/{id}: a Loot Drop can always be deleted, and it stays sealed in Recently deleted (D-10, D-30). */
export function useDeleteEntry(entryId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () =>
      unwrap(await api.DELETE('/api/v1/entries/{entryId}', { params: { path: { entryId } } })),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: queryKeys.entry(entryId) });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.feed() }),
        queryClient.invalidateQueries({ queryKey: queryKeys.opened() }),
      ]);
    },
  });
}

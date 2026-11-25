import {
  infiniteQueryOptions,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { api } from './client';
import type { Entry } from './entries';
import { dropAndRefetch } from './lootDrops';
import { unwrap } from './problem';
import { queryKeys } from './queryKeys';
import type { components } from './schema';

// Recently deleted (task 4.12, D-30, F-18): the list, its count on You, and the two ways out of it, restore and
// erase. Only these three calls can see an entry in Recently deleted; every other one answers 404 for it
// (API_CONTRACT §Entries, Recently deleted). The server redacts a sealed row (D-10); the client draws what comes back.

export type DeletedRow = components['schemas']['EntrySummaryResponse'];
export type DeletedPage = components['schemas']['PageResponseEntrySummaryResponse'];

/** Rows per request. GET /entries/deleted takes 1 to 50 and answers 400 above that, never a clamp (D-42). */
export const DELETED_PAGE_SIZE = 50;

/** The list, the most recently deleted first, as an infinite query (the paging rules are the Timeline's, task 4.11). */
export const deletedQuery = () =>
  infiniteQueryOptions({
    queryKey: queryKeys.deletedList(),
    queryFn: async ({ pageParam, signal }) =>
      unwrap(
        await api.GET('/api/v1/entries/deleted', {
          params: { query: { page: pageParam, size: DELETED_PAGE_SIZE } },
          signal,
        }),
      ),
    initialPageParam: 0,
    getNextPageParam: (lastPage: DeletedPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  });

export function useRecentlyDeleted() {
  return useInfiniteQuery(deletedQuery());
}

/**
 * How many entries wait in Recently deleted, for the row on You (DESIGN.md §6). One row is asked for and only
 * `totalItems` is read, so You never loads the list itself.
 */
export function useDeletedCount() {
  return useQuery({
    queryKey: queryKeys.deletedCount(),
    queryFn: async ({ signal }) =>
      unwrap(
        await api.GET('/api/v1/entries/deleted', {
          params: { query: { page: 0, size: 1 } },
          signal,
        }),
      ).totalItems,
  });
}

/**
 * POST /entries/{id}/restore. The entry goes back to its week with its media, tags and seal as they were, and its
 * version goes up by one (API_CONTRACT). The answer is the entry as the mapper shows it now: whole, or redacted while
 * it is still a sealed Loot Drop.
 */
export function useRestoreEntry() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (entryId: string): Promise<Entry> =>
      unwrap(
        await api.POST('/api/v1/entries/{entryId}/restore', { params: { path: { entryId } } }),
      ),
    // TODO(human) PRACTICE: After a restore, which cached screens are now wrong, and how is each put right?
    // The entry is back in the feed, its week page, the Timeline, its tags' counts, its Era's weeks, maybe the
    // Opened strip (an opened Loot Drop never marked seen), and it has left Recently deleted (list and count).
    // Hint: the server's answer is the entry as it should now be shown (whole, or redacted if still sealed), so it
    // can go straight into queryKeys.entry. A copy a page is not watching must not be drawn before the refetch
    // (dropAndRefetch, the 4.10 review fix); one key family each, never one entry's key by hand per screen.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    onSuccess: async (restored) => {
      queryClient.setQueryData(queryKeys.entry(restored.id), restored);
      await Promise.all([
        dropAndRefetch(queryClient, queryKeys.feed()),
        dropAndRefetch(queryClient, queryKeys.weeks()),
        dropAndRefetch(queryClient, queryKeys.timelines()),
        queryClient.invalidateQueries({ queryKey: queryKeys.opened() }),
        queryClient.invalidateQueries({ queryKey: queryKeys.tags() }),
        queryClient.invalidateQueries({ queryKey: queryKeys.eras() }),
        queryClient.invalidateQueries({ queryKey: queryKeys.deleted() }),
      ]);
    },
    // └── END ANSWER ▲ ──
    // A 404 means another tab or device restored or erased it first: read the list again so the row goes.
    onError: () => queryClient.invalidateQueries({ queryKey: queryKeys.deleted() }),
  });
}

/**
 * POST /entries/{id}/erase: gone at once with its media files, and it can't be undone (D-30). Nothing outside
 * Recently deleted showed it, except that an Era whose cover was one of its photos loses that cover (ON DELETE SET
 * NULL), so the Eras are read again too.
 */
export function useEraseEntry() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (entryId: string): Promise<void> => {
      unwrap(await api.POST('/api/v1/entries/{entryId}/erase', { params: { path: { entryId } } }));
    },
    onSuccess: async (_nothing, entryId) => {
      queryClient.removeQueries({ queryKey: queryKeys.entry(entryId) });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.deleted() }),
        queryClient.invalidateQueries({ queryKey: queryKeys.eras() }),
      ]);
    },
    // A 404 means another tab or device restored or erased it first: read the list again so the row goes.
    onError: () => queryClient.invalidateQueries({ queryKey: queryKeys.deleted() }),
  });
}

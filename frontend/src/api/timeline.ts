import { infiniteQueryOptions, useInfiniteQuery } from '@tanstack/react-query';
import { api } from './client';
import { unwrap } from './problem';
import { queryKeys } from './queryKeys';
import type { components } from './schema';

export type TimelineRow = components['schemas']['EntrySummaryResponse'];
export type TimelinePage = components['schemas']['PageResponseEntrySummaryResponse'];

/**
 * Rows per request. GET /entries allows 1 to 100 and answers 400 above that, never a clamp (D-42). A row is about
 * 70 px, so 50 rows are several phone screens: the next page is asked for long before the reader reaches the end,
 * and the server's default (50) means a request without `size` would look the same.
 */
export const TIMELINE_PAGE_SIZE = 50;

/**
 * The Timeline (task 4.11, D-25, F-16): every entry, newest first, as an infinite query over GET /entries. With a
 * `tagId` the server lists only entries carrying that tag and leaves sealed Loot Drops out of the rows and the
 * totals (D-10, D-26); without one, a sealed entry is a locked row with no title, counts or tags. The client draws
 * exactly what comes back and never filters or counts on its own.
 */
export const timelineQuery = (tagId: string | null) =>
  infiniteQueryOptions({
    queryKey: queryKeys.timeline(tagId),
    queryFn: async ({ pageParam, signal }) =>
      unwrap(
        await api.GET('/api/v1/entries', {
          params: {
            query: { page: pageParam, size: TIMELINE_PAGE_SIZE, ...(tagId ? { tagId } : {}) },
          },
          signal,
        }),
      ),
    initialPageParam: 0,
    // As the feed's (task 4.4): trust the server's own page number; totalPages 0 (no entries) asks for nothing.
    getNextPageParam: (lastPage: TimelinePage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  });

export function useTimeline(tagId: string | null) {
  return useInfiniteQuery(timelineQuery(tagId));
}

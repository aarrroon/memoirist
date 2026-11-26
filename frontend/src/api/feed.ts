import { infiniteQueryOptions, useInfiniteQuery } from '@tanstack/react-query';
import { api } from './client';
import { unwrap } from './problem';
import { queryKeys } from './queryKeys';
import type { components } from './schema';

export type FeedPage = components['schemas']['PageResponseWeeklyLogResponse'];
export type Week = components['schemas']['WeeklyLogResponse'];
export type WeekEra = components['schemas']['WeekEraResponse'];
export type Entry = components['schemas']['EntryResponse'];
export type Media = components['schemas']['MediaResponse'];

/**
 * Weeks per request. GET /feed allows 1 to 50 and answers 400 above that, never a clamp (D-42), so the client
 * stays well inside it. Ten weeks fill several screens on a phone, so the next page is asked for long before
 * the reader reaches the end.
 */
export const FEED_PAGE_SIZE = 10;

// TanStack Query's infinite query (task 4.4): one cache entry holds every page loaded so far, in order, with the
// page number that fetched each. `fetchNextPage()` calls getNextPageParam on the last page to learn what to ask
// for next; `undefined` means there is nothing more, and `hasNextPage` turns false.
export const feedQuery = () =>
  infiniteQueryOptions({
    queryKey: queryKeys.feed(),
    queryFn: async ({ pageParam, signal }) =>
      unwrap(
        await api.GET('/api/v1/feed', {
          params: { query: { page: pageParam, size: FEED_PAGE_SIZE } },
          signal,
        }),
      ),
    initialPageParam: 0,
    // TODO(human) PRACTICE: Write getNextPageParam. Given the last page the server sent (FeedPage: items, page,
    // size, totalItems, totalPages), return the number of the next page to fetch, or undefined when the last
    // page has been loaded.
    // Hint: pages count from 0, so the last one is totalPages - 1. A user with no entries gets totalPages 0, and
    // a page past the end is 200 with empty items (API_CONTRACT, Feed): neither may ask again, or the sentinel
    // at the foot of the feed would fetch forever. Trust the server's own `page`, not the length of allPages.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    getNextPageParam: (lastPage: FeedPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
    // └── END ANSWER ▲ ──
  });

/**
 * The feed: the user's weeks, newest first, each with its Eras and its entries (GET /feed). Sealed Loot Drops
 * come back already redacted by the server's EntryMapper (D-10); the screen still shows them as locked rows only.
 */
export function useFeed() {
  return useInfiniteQuery(feedQuery());
}

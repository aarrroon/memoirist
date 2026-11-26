import { queryOptions, skipToken, useQuery } from '@tanstack/react-query';
import { api } from './client';
import { unwrap } from './problem';
import { queryKeys } from './queryKeys';
import type { components } from './schema';

// Song search (D-35, task 4.15): POST /music/search asks our server, which asks Spotify with the app's own login. The
// browser never calls Spotify, and the words go in the body, never in a URL. Spotify gives a new app a small quota, and
// without credentials the server answers 503 music-search-unavailable; the soundtrack field then offers paste-a-link.

export type MusicResult = components['schemas']['MusicResult'];

// TODO(human) PRACTICE: Write musicSearchQuery(words): the query options for one search. It runs only once the user
// has asked (words is null until then), is cached per set of words for 30 minutes at most, and never retries.
// Hint: TanStack Query v5's skipToken in place of the queryFn means "don't run yet" and keeps the types exact, where
// `enabled: false` would leave the queryFn typed as callable with a null. Why no retry, when the app's default retries
// a 5xx twice? A 503 here is the server saying search isn't set up, or Spotify's quota said no: asking twice more makes
// the user wait for the same answer and spends quota (D-35). It is still a query, not a mutation: a POST that only reads.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export const musicSearchQuery = (words: string | null) =>
  queryOptions({
    queryKey: queryKeys.musicSearch(words ?? ''),
    queryFn:
      words === null
        ? skipToken
        : async ({ signal }) =>
            unwrap(await api.POST('/api/v1/music/search', { body: { query: words }, signal }))
              .items,
    staleTime: 30 * 60 * 1000,
    retry: false,
  });
// └── END ANSWER ▲ ──

/** The results for `words`, or nothing while `words` is null (the user hasn't searched yet). */
export function useMusicSearch(words: string | null) {
  return useQuery(musicSearchQuery(words));
}

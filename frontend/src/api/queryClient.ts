import { QueryClient } from '@tanstack/react-query';
import { isRetryable } from './problem';

/** Presigned media URLs last 60 minutes (API_CONTRACT §Media); cached data must go stale well before. */
export const STALE_TIME_MS = 30 * 60 * 1000;

const MAX_RETRIES = 2;

// One QueryClient for the app (main.tsx); each test makes its own, so no cache leaks between tests.
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // 30 minutes, the limit in CLAUDE.md and frontend.md: a refetch on focus after that brings fresh URLs
        // before the old ones expire. Window-focus refetching is TanStack Query's default and stays on.
        staleTime: STALE_TIME_MS,
        // Retrying a 404 or a 409 gets the same answer three more times and delays the error screen.
        // Retry only when nothing answered or the server failed.
        retry: (failureCount, error) => failureCount < MAX_RETRIES && isRetryable(error),
      },
      // Mutations aren't retried (TanStack Query's default): a create sent twice relies on the client id
      // (D-12), and a PUT with a stale version would fail anyway (D-23).
    },
  });
}

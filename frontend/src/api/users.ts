import { queryOptions, useQuery } from '@tanstack/react-query';
import { api } from './client';
import { unwrap } from './problem';
import { queryKeys } from './queryKeys';
import type { components } from './schema';

export type Me = components['schemas']['UserResponse'];

// queryOptions keeps the key, the function and the result type together, so a loader or a prefetch
// (task 4.3) and the hook can't drift apart.
export const meQuery = () =>
  queryOptions({
    queryKey: queryKeys.me(),
    queryFn: async ({ signal }) => unwrap(await api.GET('/api/v1/users/me', { signal })),
  });

/** The current user: their name, and the timezone every date is shown in (src/lib/time.ts, task 4.3). */
export function useMe() {
  return useQuery(meQuery());
}

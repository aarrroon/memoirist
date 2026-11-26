import { queryOptions, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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

export type MeChange = components['schemas']['UserUpdateRequest'];

/**
 * PATCH /users/me (task 4.9): the display name, the timezone or the birthday (D-33). A missing field is left as it
 * is, and a null birthday removes it. The timezone decides "today", the weeks and every time on screen (D-07), so a
 * new zone refetches everything the app holds; a new name or birthday only replaces the user.
 */
export function useUpdateMe() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (change: MeChange) =>
      unwrap(await api.PATCH('/api/v1/users/me', { body: change })),
    onSuccess: (me, change) => {
      const before = queryClient.getQueryData<Me>(queryKeys.me());
      queryClient.setQueryData(queryKeys.me(), me);
      if (change.timezone !== undefined && before?.timezone !== me.timezone) {
        return queryClient.invalidateQueries({ queryKey: queryKeys.all });
      }
    },
  });
}

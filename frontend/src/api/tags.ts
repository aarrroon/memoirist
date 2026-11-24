import { queryOptions, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import { unwrap } from './problem';
import { queryKeys } from './queryKeys';
import type { components } from './schema';

export type Tag = components['schemas']['TagResponse'];

// The user's tags (D-26), by name. Capture's tag picker reads them (task 4.6); the Tags screen is task 4.11.
// `entryCount` already leaves out sealed entries on the server (D-10); capture never shows it.
export const tagsQuery = () =>
  queryOptions({
    queryKey: queryKeys.tags(),
    queryFn: async ({ signal }) => unwrap(await api.GET('/api/v1/tags', { signal })),
  });

export function useTags() {
  return useQuery(tagsQuery());
}

/**
 * Creates a tag from the picker. POST /tags answers a name that already exists (ignoring case) with that tag and
 * 200, so typing "family" when "Family" exists picks "Family" instead of failing (API_CONTRACT §Tags).
 */
export function useCreateTag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (name: string) => unwrap(await api.POST('/api/v1/tags', { body: { name } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.tags() }),
  });
}

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

/**
 * A tag's name shows wherever an entry shows its tags: the feed, a week page, an entry, the Opened row and every
 * Timeline (F-17, "renaming a tag updates everywhere"). A rename or a delete changes no entry on the server (their
 * version doesn't move, API_CONTRACT §Tags), so nothing here can be patched from the answer: every family that draws
 * tags is read again. Invalidating keeps what is on screen until the new answer lands, then swaps it.
 */
function useAfterTagChange() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all(
      [
        queryKeys.tags(),
        queryKeys.timelines(),
        queryKeys.feed(),
        queryKeys.weeks(),
        queryKeys.entries(),
        queryKeys.opened(),
      ].map((queryKey) => queryClient.invalidateQueries({ queryKey })),
    );
}

/** PUT /tags/{id}: renames the tag and nothing else. A name another tag has, ignoring case, is 409 conflict. */
export function useRenameTag() {
  const after = useAfterTagChange();
  return useMutation({
    mutationFn: async ({ tagId, name }: { tagId: string; name: string }) =>
      unwrap(
        await api.PUT('/api/v1/tags/{tagId}', {
          params: { path: { tagId } },
          body: { name: name.trim() },
        }),
      ),
    onSuccess: after,
  });
}

/** DELETE /tags/{id}: the entries that carried it keep everything else (D-26). */
export function useDeleteTag() {
  const after = useAfterTagChange();
  return useMutation({
    mutationFn: async (tagId: string) =>
      unwrap(await api.DELETE('/api/v1/tags/{tagId}', { params: { path: { tagId } } })),
    onSuccess: after,
  });
}

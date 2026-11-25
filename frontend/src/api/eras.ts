import { queryOptions, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import { unwrap } from './problem';
import { queryKeys } from './queryKeys';
import type { components } from './schema';

export type Era = components['schemas']['EraResponse'];
export type EraDetail = components['schemas']['EraDetailResponse'];
export type EraWeek = components['schemas']['EraWeekResponse'];

/** What the Era form edits (task 4.9). The cover (task 4.14) and the soundtrack (task 4.15) aren't in the form. */
export interface EraFields {
  title: string;
  description: string;
  startDate: string;
  /** null: the Era is still going. */
  endDate: string | null;
  pinnedInFeed: boolean;
}

// The user's Eras, latest start first (GET /eras), and one Era with its weeks (GET /eras/{id}, D-22). An Era is
// only its dates, so the server works out its weeks on every read: nothing here caches which weeks it holds.
export const erasQuery = () =>
  queryOptions({
    queryKey: queryKeys.eras(),
    queryFn: async ({ signal }) => unwrap(await api.GET('/api/v1/eras', { signal })),
  });

export const eraQuery = (eraId: string) =>
  queryOptions({
    queryKey: queryKeys.era(eraId),
    queryFn: async ({ signal }) =>
      unwrap(await api.GET('/api/v1/eras/{eraId}', { params: { path: { eraId } }, signal })),
  });

export function useEras() {
  return useQuery(erasQuery());
}

export function useEra(eraId: string) {
  return useQuery(eraQuery(eraId));
}

/** The body of a POST or PUT from the form's fields. Blank text is left out, which the server reads as none. */
function body(fields: EraFields) {
  const description = fields.description.trim();
  return {
    title: fields.title.trim(),
    ...(description ? { description } : {}),
    startDate: fields.startDate,
    ...(fields.endDate ? { endDate: fields.endDate } : {}),
    pinnedInFeed: fields.pinnedInFeed,
  };
}

/**
 * An Era's dates decide which weeks it holds and its pin decides which Era a shared week sits under (D-22), so
 * every change refetches the Eras and the feed: the feed regroups from the server's answer, never from a guess here.
 */
function useInvalidateEras() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.eras() }),
      queryClient.invalidateQueries({ queryKey: queryKeys.feed() }),
    ]);
}

export function useCreateEra() {
  const invalidate = useInvalidateEras();
  return useMutation({
    // A client id makes a retried create safe: the server answers 200 with the Era it already has (D-12).
    mutationFn: async (fields: EraFields) =>
      unwrap(
        await api.POST('/api/v1/eras', { body: { id: crypto.randomUUID(), ...body(fields) } }),
      ),
    onSuccess: invalidate,
  });
}

export function useUpdateEra(era: Era | EraDetail) {
  const invalidate = useInvalidateEras();
  return useMutation({
    // PUT is a full replace (D-26), so the fields the form doesn't edit go back as they are: the soundtrack, and
    // the cover when it is shown. A hidden cover (its entry sealed or deleted) has no id to send; leaving it out
    // keeps it (D-36, server side in task 4.14).
    mutationFn: async (fields: EraFields) =>
      unwrap(
        await api.PUT('/api/v1/eras/{eraId}', {
          params: { path: { eraId: era.id } },
          body: {
            ...body(fields),
            ...(era.soundtrack ? { soundtrack: era.soundtrack } : {}),
            ...(era.cover ? { coverMediaId: era.cover.id } : {}),
          },
        }),
      ),
    onSuccess: invalidate,
  });
}

export function useDeleteEra() {
  const invalidate = useInvalidateEras();
  return useMutation({
    mutationFn: async (eraId: string) =>
      unwrap(await api.DELETE('/api/v1/eras/{eraId}', { params: { path: { eraId } } })),
    onSuccess: invalidate,
  });
}

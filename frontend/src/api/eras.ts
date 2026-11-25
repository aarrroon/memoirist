import {
  queryOptions,
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import type { Media } from './feed';
import { api } from './client';
import { unwrap } from './problem';
import { queryKeys } from './queryKeys';
import type { components } from './schema';
import { weekQuery } from './weeks';

export type Era = components['schemas']['EraResponse'];
export type EraDetail = components['schemas']['EraDetailResponse'];
export type EraWeek = components['schemas']['EraWeekResponse'];

/** What the Era form edits (task 4.9; the cover since task 4.14). The soundtrack (task 4.15) isn't in the form. */
export interface EraFields {
  title: string;
  description: string;
  startDate: string;
  /** null: the Era is still going. */
  endDate: string | null;
  pinnedInFeed: boolean;
  /**
   * The cover photo's id, or null for none (task 4.14, D-36). Null is sent as a field left out: the server removes a
   * visible cover then, and keeps a hidden one (its entry sealed or in Recently deleted), whose id the client was
   * never told. Only an edit has it; a new Era has no photos of its own to choose from yet.
   */
  coverMediaId?: string | null;
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
    ...(fields.coverMediaId ? { coverMediaId: fields.coverMediaId } : {}),
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
      // A week page names its Eras and links to them (task 4.10).
      queryClient.invalidateQueries({ queryKey: queryKeys.weeks() }),
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
    // PUT is a full replace (D-26), so the field the form doesn't edit goes back as it is: the soundtrack. The cover
    // is the form's own (task 4.14): the picker starts from the cover shown, and a hidden one, which the client can't
    // see, is kept by the server when the field is left out (D-36).
    mutationFn: async (fields: EraFields) =>
      unwrap(
        await api.PUT('/api/v1/eras/{eraId}', {
          params: { path: { eraId: era.id } },
          body: {
            ...body(fields),
            ...(era.soundtrack ? { soundtrack: era.soundtrack } : {}),
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

/** How many of an Era's weeks the cover picker reads at a time (one GET /weekly-logs/{id} each). */
export const COVER_WEEKS_STEP = 6;

/** A photo the cover picker offers: an image from an open entry inside the Era's dates. */
export interface CoverChoice {
  photo: Media;
  entryDate: string;
}

// TODO(human) PRACTICE: Write useEraPhotos(era, weekCount): the photos the cover picker offers (task 4.14, D-36). An
//   Era page knows only its weeks' ids (weeklyLogs, newest first), and each week's entries and photos come from
//   GET /weekly-logs/{id} (weekQuery, task 4.10). Read the first `weekCount` weeks in parallel with useQueries and use
//   its `combine` option to return ONE object: { photos, pending, failed, hasMore }. Offer only images (not voice
//   notes) from entries that aren't sealed and whose memory date is inside the Era's dates; newest week first.
// Hint: useQueries({ queries: ids.map((id) => weekQuery(id)), combine: (results) => ... }). Reusing weekQuery means
//   a week already opened is served from the cache under the same key. A sealed entry arrives with empty media
//   anyway (D-10), but check isLocked: a picker must never depend on that. An Era with no end has no upper date.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function useEraPhotos(era: EraDetail, weekCount: number) {
  const ids = era.weeklyLogs.slice(0, weekCount).map((week) => week.id);
  return useQueries({
    queries: ids.map((id) => weekQuery(id)),
    combine: (results) => ({
      photos: results.flatMap((result): CoverChoice[] =>
        (result.data?.entries ?? [])
          .filter(
            (entry) =>
              !entry.isLocked &&
              entry.entryDate >= era.startDate &&
              (era.endDate === null || entry.entryDate <= era.endDate),
          )
          .flatMap((entry) =>
            entry.media
              .filter((media) => media.mediaType.startsWith('image/'))
              .map((photo) => ({ photo, entryDate: entry.entryDate })),
          ),
      ),
      pending: results.some((result) => result.isPending),
      failed: results.some((result) => result.isError),
      hasMore: era.weeklyLogs.length > ids.length,
    }),
  });
}
// └── END ANSWER ▲ ──

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import { unwrap } from './problem';
import { queryKeys } from './queryKeys';
import type { components } from './schema';

// The entry writes capture makes (task 4.6), as plain async functions. Capture's save is one flow of several
// requests (create, upload each file, reorder: frontend.md, Capture and media), so these are called by
// features/capture/saveEntry.ts in order, inside one mutation, rather than as separate hooks a component strings
// together. Each ends with unwrap(), so a failure is always an ApiProblem.

export type Entry = components['schemas']['EntryResponse'];
export type Media = components['schemas']['MediaResponse'];
export type EntryCreate = components['schemas']['EntryCreateRequest'];
export type EntryUpdate = components['schemas']['EntryUpdateRequest'];
export type MediaPurpose = components['schemas']['MediaUploadForm']['purpose'];

/** POST /entries. `id` is the client's own (D-12): sent again after a lost answer, it returns the same entry. */
export async function createEntry(body: EntryCreate): Promise<Entry> {
  return unwrap(await api.POST('/api/v1/entries', { body }));
}

/** PUT /entries/{id}: a full replace with the `version` last read (D-23, D-26). A stale one is 409 entry-stale. */
export async function updateEntry(id: string, body: EntryUpdate): Promise<Entry> {
  return unwrap(
    await api.PUT('/api/v1/entries/{entryId}', { params: { path: { entryId: id } }, body }),
  );
}

export async function getEntry(id: string, signal?: AbortSignal): Promise<Entry> {
  return unwrap(
    await api.GET('/api/v1/entries/{entryId}', { params: { path: { entryId: id } }, signal }),
  );
}

/** DELETE /entries/{id}. An entry with no title, caption or media is erased at once (D-23, D-30). */
export async function deleteEntry(id: string): Promise<void> {
  unwrap(await api.DELETE('/api/v1/entries/{entryId}', { params: { path: { entryId: id } } }));
}

/**
 * POST /entries/{id}/media as multipart. No `displayOrder`: the server appends, and capture puts the final order
 * with reorderMedia afterwards (API_CONTRACT §Media). A diary page goes up as PAGE_SCAN (D-20).
 */
export async function uploadMedia(
  entryId: string,
  file: Blob,
  purpose: MediaPurpose,
): Promise<Media> {
  return unwrap(
    await api.POST('/api/v1/entries/{entryId}/media', {
      params: { path: { entryId } },
      // The generated type says `file: string` (OpenAPI's "binary"); the real value is the Blob, which the
      // serializer below puts in the form. openapi-fetch sets no Content-Type for FormData, so the browser adds
      // the multipart boundary itself.
      body: { file: file as unknown as string, purpose },
      bodySerializer: () => {
        const form = new FormData();
        form.append('file', file, file instanceof File ? file.name : 'photo');
        form.append('purpose', purpose);
        return form;
      },
    }),
  );
}

/** PUT /entries/{id}/media/order: exactly the entry's current media, in the order the user left them. */
export async function reorderMedia(entryId: string, mediaIds: string[]): Promise<Media[]> {
  return unwrap(
    await api.PUT('/api/v1/entries/{entryId}/media/order', {
      params: { path: { entryId } },
      body: { mediaIds },
    }),
  );
}

/** DELETE /media/{id}: a photo the user removed from capture after it had uploaded. */
export async function deleteMedia(mediaId: string): Promise<void> {
  unwrap(await api.DELETE('/api/v1/media/{mediaId}', { params: { path: { mediaId } } }));
}

/**
 * The *date unknown, fix it* badge (D-34, task 4.13): a new memory date for an entry, as PUT /entries/{id}. A PUT is a
 * full replace (D-26), so every field the entry holds is sent back as it is, with the version last read; the server
 * clears `dateUnknown` because the date changed, and a client never sends that mark on a PUT. A null field is left out
 * of the JSON, as in capture's save, which the API reads as null.
 */
export function useChangeEntryDate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ entry, entryDate }: { entry: Entry; entryDate: string }) =>
      updateEntry(entry.id, {
        version: entry.version,
        entryDate,
        templateId: entry.templateId ?? undefined,
        title: entry.title ?? undefined,
        contentJson: entry.contentJson,
        tagIds: entry.tags.map((t) => t.id),
        soundtrack: entry.soundtrack ?? undefined,
        replyToEntryId: entry.replyToEntryId ?? undefined,
      }),
    onSuccess: async (updated) => {
      queryClient.setQueryData(queryKeys.entry(updated.id), updated);
      // The entry may move to another week, so every list that files it by date is read again.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.feed() }),
        queryClient.invalidateQueries({ queryKey: queryKeys.weeks() }),
        queryClient.invalidateQueries({ queryKey: queryKeys.timelines() }),
        queryClient.invalidateQueries({ queryKey: queryKeys.eras() }),
      ]);
    },
    // A 409 entry-stale means the feed's copy is old: read it again, so the next try sends the current version.
    onError: () => queryClient.invalidateQueries({ queryKey: queryKeys.feed() }),
  });
}

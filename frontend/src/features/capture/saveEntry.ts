import {
  createEntry,
  deleteEntry,
  deleteMedia,
  getEntry,
  reorderMedia,
  updateEntry,
  uploadMedia,
  type Entry,
  type MediaPurpose,
} from '../../api/entries';
import { ApiProblem, problemMessage } from '../../api/problem';
import type { JSONContent } from '@tiptap/react';
import { fileProblem, isImageType, shrinkPhoto } from '../../lib/files';

// Capture's save, one flow of several requests (frontend.md, Capture and media; D-12, D-23):
//   create the entry (with the client's own id) or replace it (with the version last read)
//   → remove the photos the user took out → upload each new file → put the final order
// A failed upload doesn't fail the save: the entry keeps what did upload, and the failed items stay in capture with
// Retry and Remove (G-03). Errors from the create or the replace do fail it, so the form can show them.

/** One picture or file in capture's tray, in the order the user sees. */
export interface TrayItem {
  /** Local key for React and dnd-kit; not sent anywhere. */
  key: string;
  /** The file still to upload, as it was picked, or null once it is on the server. Since task 4.13 a photo is shrunk
   *  when it uploads, once its purpose is known (a diary page more gently, lib/files.ts), not when it is picked. */
  file: File | null;
  /** An object URL for a local file, or the presigned URL of an uploaded one. */
  previewUrl: string;
  mediaType: string;
  /** PAGE_SCAN for a diary page (D-20); chosen before upload, since it can't change afterwards. */
  purpose: MediaPurpose;
  status: 'waiting' | 'uploading' | 'uploaded' | 'failed';
  mediaId: string | null;
  /** Why the last upload failed, in words for the user. */
  error: string | null;
  /** A voice note recorded in this capture (task 4.7): its loudness bars and its length, for its row. Not sent. */
  levels?: number[];
  seconds?: number;
}

/**
 * Everything PUT /entries replaces (D-26). A null field is left out of the JSON, because the generated request type
 * doesn't allow null, and a field left out of a PUT is cleared (API_CONTRACT §Entries): the same effect as sending null.
 */
export interface EntryFields {
  entryDate: string;
  templateId: string | null;
  title: string | null;
  contentJson: JSONContent | null;
  tagIds: string[];
  /** The soundtrack is capture's since task 4.15; the reply isn't edited yet (4.16). A PUT must send both, or they are
   *  cleared. */
  soundtrack: string | null;
  replyToEntryId: string | null;
  /** A photo dump's undated photos (D-34, task 4.13). Sent on create only: the server clears the mark when a PUT
   *  changes the date, and a client never sends it on a PUT. */
  dateUnknown?: boolean;
}

export interface SaveInput {
  /** The id a new entry is created with (crypto.randomUUID(), D-12). */
  clientId: string;
  /** The entry once it exists, and the version last read from it (D-23). */
  saved: { id: string; version: number } | null;
  fields: EntryFields;
  items: TrayItem[];
  /** Uploaded photos the user removed since the last save. */
  removedMediaIds: string[];
}

export interface SaveResult {
  /** The entry as the server has it now, or null when it was empty and has been erased (D-23). */
  entry: Entry | null;
  /** How many files didn't upload. */
  failed: number;
}

/**
 * The tray's items with every picture before every voice note, each group in its own order (a stable split). The
 * swipe shows pictures only, and an entry's first media item names it in lists (D-24), so a voice note recorded before
 * a photo was picked must not go first and turn 'Photo' into 'Voice note' (task 4.7, review fix).
 */
export function picturesFirst(items: TrayItem[]): TrayItem[] {
  return [
    ...items.filter((i) => isImageType(i.mediaType)),
    ...items.filter((i) => !isImageType(i.mediaType)),
  ];
}

// A retry shrinks a photo only once: the copy is kept with the picked file, for the purpose it was shrunk for.
const shrunk = new WeakMap<File, { purpose: MediaPurpose; file: File }>();

/** The picked file as it is sent: a photo shrunk for its purpose (lib/files.ts), anything else as it is. */
async function preparedFile(file: File, purpose: MediaPurpose): Promise<File> {
  const kept = shrunk.get(file);
  if (kept && kept.purpose === purpose) return kept.file;
  const ready = await shrinkPhoto(file, purpose);
  shrunk.set(file, { purpose, file: ready });
  return ready;
}

/** Updates one tray item while the save runs, so each thumbnail shows its own progress. */
export type ItemUpdate = (key: string, patch: Partial<TrayItem>) => void;

// TODO(human) PRACTICE: Write saveEntry(input, onItem). Follow the order in the comment at the top of this file:
// create (POST with `id: input.clientId`) or replace (PUT with the version), delete removed photos, upload every item
// that isn't uploaded yet (one at a time, in tray order, reporting 'uploading', then 'uploaded' with its mediaId or
// 'failed' with problemMessage; since task 4.13 each file is first shrunk for its purpose with preparedFile, and one
// still over 25 MB fails on its item without being sent), then PUT the order of every uploaded item when there are two
// or more. A create also sends `dateUnknown: true` when the fields ask for it (D-34), never a PUT. The order must
// name exactly the entry's media, so start from the media list in the create or PUT answer, not the tray alone.
// If the entry holds no media and has no title, caption or tags, delete it and return entry: null. When an upload
// failed, read the entry again: media changes never touch the version (API_CONTRACT §Media), so the version from the
// create or PUT answer is still current, but the re-read gives the media list as it really is for the next save.
// Hint: the client id is what makes a retry safe (D-12): a POST whose answer was lost, sent again with the same id,
// gets the same entry back (200) instead of a second one. That is the idempotency key of a bank payment API.
// Let errors from the create or the replace escape (the form shows them); catch only upload errors, per item.
// A photo already deleted elsewhere answers 404 on DELETE, which is the result we wanted.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export async function saveEntry(input: SaveInput, onItem: ItemUpdate): Promise<SaveResult> {
  const { fields } = input;
  const body = {
    entryDate: fields.entryDate,
    templateId: fields.templateId ?? undefined,
    title: fields.title ?? undefined,
    contentJson: (fields.contentJson ?? null) as Entry['contentJson'],
    tagIds: fields.tagIds,
    soundtrack: fields.soundtrack ?? undefined,
    replyToEntryId: fields.replyToEntryId ?? undefined,
  };
  let entry = input.saved
    ? await updateEntry(input.saved.id, { ...body, version: input.saved.version })
    : await createEntry({
        ...body,
        id: input.clientId,
        ...(fields.dateUnknown ? { dateUnknown: true } : {}),
      });

  for (const mediaId of input.removedMediaIds) {
    try {
      await deleteMedia(mediaId);
    } catch (error) {
      if (!(error instanceof ApiProblem && error.kind === 'not-found')) throw error;
    }
  }

  // The server's own list, not the tray, says what the entry holds: a draft restored after a reload, or a photo added
  // on another device, can leave photos the tray doesn't show. PUT …/media/order must name exactly the entry's media
  // (API_CONTRACT §Media), and an entry that still holds photos is never erased as empty (review fix, task 4.6).
  const removed = new Set(input.removedMediaIds);
  const onServer = new Set(entry.media.map((m) => m.id).filter((id) => !removed.has(id)));

  const order: string[] = [];
  let failed = 0;
  // Pictures before voice notes whatever order the tray was built in (D-24; task 4.7 review fix).
  for (const item of picturesFirst(input.items)) {
    if (item.status === 'uploaded' && item.mediaId) {
      if (onServer.has(item.mediaId)) order.push(item.mediaId);
      continue;
    }
    if (!item.file) continue;
    onItem(item.key, { status: 'uploading', error: null });
    try {
      // Shrunk now that its purpose is known (task 4.13), and checked as it will be sent (D-17).
      const file = await preparedFile(item.file, item.purpose);
      const tooBig = fileProblem(file);
      if (tooBig) {
        failed++;
        onItem(item.key, { status: 'failed', error: tooBig });
        continue;
      }
      const media = await uploadMedia(entry.id, file, item.purpose);
      onItem(item.key, { status: 'uploaded', mediaId: media.id, error: null });
      order.push(media.id);
    } catch (error) {
      failed++;
      onItem(item.key, { status: 'failed', error: problemMessage(error) });
    }
  }

  // Photos the tray didn't know of keep their place after the ones it shows.
  for (const id of onServer) if (!order.includes(id)) order.push(id);

  if (order.length > 1) await reorderMedia(entry.id, order);

  const nothingElse = !fields.title && !fields.contentJson && fields.tagIds.length === 0;
  if (order.length === 0 && failed > 0 && nothingElse) {
    await deleteEntry(entry.id);
    return { entry: null, failed };
  }
  if (failed > 0) entry = await getEntry(entry.id);
  return { entry, failed };
}
// └── END ANSWER ▲ ──

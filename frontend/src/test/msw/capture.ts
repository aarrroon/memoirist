import { http, HttpResponse } from 'msw';
import type { Entry, Media } from '../../api/entries';
import type { Tag } from '../../api/tags';
import type { Template } from '../../api/templates';
import { weekOf } from '../../lib/time';
import { entry, page, sampleWeeks, week } from './feed';

// A small stand-in for the entry API that capture writes to (task 4.6): it remembers what was created, uploaded and
// ordered, and the feed it serves includes the new entries in their weeks, so a test can save and then look for the
// entry in the feed. The feed keeps the sample's sealed Loot Drop (D-10); Recently deleted and other users' entries
// never reach the client (the backend's own tests prove the server leaves them out).

const at = '2026-11-24T12:47:00Z';

export const sampleTags: Tag[] = [
  { id: 'tag-family', name: 'Family', entryCount: 1 },
  { id: 'tag-trip', name: 'Trip', entryCount: 1 },
];

export const sampleTemplates: Template[] = [
  {
    id: 'template-night',
    name: 'Night out',
    subheadings: ['Who was there', 'The best bit'],
    createdAt: at,
    updatedAt: at,
  },
];

export interface FakeBackend {
  entries: Map<string, Entry>;
  /** Every request body capture sent, in order, for asserting what was (and wasn't) sent. */
  calls: { method: string; path: string; body: unknown }[];
  tags: Tag[];
  /** Upload numbers (1-based, in the order they arrive) that answer 503 storage-unavailable. */
  failUploads: Set<number>;
  /** When true, POST …/seal answers 503, as if the network or the server failed (F-06). */
  failSeal: boolean;
}

const problem = (status: number, suffix: string, fieldErrors?: Record<string, string>) =>
  HttpResponse.json(
    {
      type: `urn:memoirist:problem:${suffix}`,
      title: 'Problem',
      status,
      detail: 'Server detail that must never be shown.',
      timestamp: at,
      ...(fieldErrors ? { fieldErrors } : {}),
    },
    { status },
  );

/** What each upload's form held, in the order the forms were built (uploads go one at a time). */
const sentForms: { name: string; type: string; size: number; purpose: string }[] = [];
let watching = false;

/** Notes the file and purpose put in each FormData, so the upload handler can see them without reading the body. */
function watchForms() {
  sentForms.length = 0;
  if (watching) return;
  watching = true;
  const append = Object.getOwnPropertyDescriptor(FormData.prototype, 'append')?.value as (
    this: FormData,
    ...args: unknown[]
  ) => void;
  Object.defineProperty(FormData.prototype, 'append', {
    configurable: true,
    writable: true,
    value: function watchedAppend(this: FormData, ...args: [string, string | Blob, string?]) {
      const [key, value, filename] = args;
      if (key === 'file' && value instanceof Blob) {
        sentForms.push({ name: filename ?? '', type: value.type, size: value.size, purpose: '' });
      }
      const last = sentForms.at(-1);
      if (key === 'purpose' && typeof value === 'string' && last) last.purpose = value;
      append.apply(this, args);
    },
  });
}

export function fakeBackend() {
  watchForms();
  const state: FakeBackend = {
    entries: new Map(),
    calls: [],
    tags: [...sampleTags],
    failUploads: new Set(),
    failSeal: false,
  };
  let uploads = 0;
  let mediaCount = 0;

  const handlers = [
    http.get('*/api/v1/tags', () => HttpResponse.json(state.tags)),
    http.post('*/api/v1/tags', async ({ request }) => {
      const body = (await request.json()) as { name: string };
      state.calls.push({ method: 'POST', path: '/tags', body });
      const existing = state.tags.find((t) => t.name.toLowerCase() === body.name.toLowerCase());
      if (existing) return HttpResponse.json(existing);
      const tag = { id: `tag-${state.tags.length + 1}`, name: body.name, entryCount: 0 };
      state.tags.push(tag);
      return HttpResponse.json(tag, { status: 201 });
    }),
    http.get('*/api/v1/templates', () => HttpResponse.json(sampleTemplates)),

    http.post('*/api/v1/entries', async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      state.calls.push({ method: 'POST', path: '/entries', body });
      const id = body.id as string;
      const replay = state.entries.get(id);
      if (replay) return HttpResponse.json(replay);
      const entryDate = (body.entryDate as string | undefined) ?? '2026-11-24';
      const tagIds = (body.tagIds as string[] | undefined) ?? [];
      const created = entry(id, entryDate, {
        weeklyLogId: `week-${weekOf(entryDate).start}`,
        title: (body.title as string | undefined) ?? null,
        contentJson: (body.contentJson as Entry['contentJson'] | undefined) ?? null,
        templateId: (body.templateId as string | undefined) ?? null,
        // A photo dump's undated photos (D-34, task 4.13).
        dateUnknown: body.dateUnknown === true,
        tags: state.tags
          .filter((t) => tagIds.includes(t.id))
          .map((t) => ({ id: t.id, name: t.name, source: 'USER' })),
      });
      state.entries.set(id, created);
      return HttpResponse.json(created, { status: 201 });
    }),
    // Before entries/:id, which would otherwise take "opened" for an id (task 4.8).
    http.get('*/api/v1/entries/opened', () => HttpResponse.json([])),
    http.post('*/api/v1/entries/:id/seal', async ({ params, request }) => {
      const body = (await request.json()) as { unlockDate: string };
      state.calls.push({ method: 'POST', path: `/entries/${String(params.id)}/seal`, body });
      if (state.failSeal) return problem(503, 'storage-unavailable');
      const found = state.entries.get(params.id as string);
      if (!found) return problem(404, 'not-found');
      // As the server's EntryMapper does: nothing private comes back while it is sealed (D-10).
      const sealed = {
        ...entry(found.id, found.entryDate, { weeklyLogId: found.weeklyLogId }),
        unlockDate: body.unlockDate,
        isLocked: true,
        version: found.version + 1,
      };
      state.entries.set(found.id, sealed);
      return HttpResponse.json(sealed);
    }),
    http.get('*/api/v1/entries/:id', ({ params }) => {
      const found = state.entries.get(params.id as string);
      return found ? HttpResponse.json(found) : problem(404, 'not-found');
    }),
    http.put('*/api/v1/entries/:id', async ({ params, request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      state.calls.push({ method: 'PUT', path: `/entries/${String(params.id)}`, body });
      const found = state.entries.get(params.id as string);
      if (!found) return problem(404, 'not-found');
      if (body.version !== found.version) return problem(409, 'entry-stale');
      const entryDate = body.entryDate as string;
      const updated = {
        ...found,
        title: (body.title as string | undefined) ?? null,
        entryDate,
        // As the server does (D-34): a new date clears date unknown and moves the entry to that date's week.
        dateUnknown: entryDate === found.entryDate ? found.dateUnknown : false,
        weeklyLogId: `week-${weekOf(entryDate).start}`,
        version: found.version + 1,
      };
      state.entries.set(found.id, updated);
      return HttpResponse.json(updated);
    }),
    http.delete('*/api/v1/entries/:id', ({ params }) => {
      state.calls.push({ method: 'DELETE', path: `/entries/${String(params.id)}`, body: null });
      state.entries.delete(params.id as string);
      return new HttpResponse(null, { status: 204 });
    }),
    http.post('*/api/v1/entries/:id/media', ({ params }) => {
      // The multipart body isn't read here: under jsdom, reading a body that holds a file fails ("Premature close",
      // Vitest's bridge from jsdom's Blob to Node's). The form capture built is read from watchForms() instead.
      const file = sentForms.shift() ?? { name: '', type: '', size: 0, purpose: '' };
      const { purpose } = file;
      state.calls.push({
        method: 'POST',
        path: `/entries/${String(params.id)}/media`,
        body: { purpose, name: file.name, type: file.type },
      });
      uploads++;
      if (state.failUploads.has(uploads)) return problem(503, 'storage-unavailable');
      const found = state.entries.get(params.id as string);
      if (!found) return problem(404, 'not-found');
      mediaCount++;
      const media: Media = {
        id: `media-new-${mediaCount}`,
        entryId: found.id,
        fileUrl: `http://localhost:8333/memoirist/new-${mediaCount}.png?X-Amz-Signature=test`,
        mediaType: file.type,
        purpose,
        displayOrder: found.media.length,
        fileSizeBytes: file.size,
        uploadedAt: at,
        width: 960,
        height: 640,
      };
      state.entries.set(found.id, { ...found, media: [...found.media, media] });
      return HttpResponse.json(media, { status: 201 });
    }),
    http.put('*/api/v1/entries/:id/media/order', async ({ params, request }) => {
      const body = (await request.json()) as { mediaIds: string[] };
      state.calls.push({ method: 'PUT', path: `/entries/${String(params.id)}/media/order`, body });
      const found = state.entries.get(params.id as string);
      if (!found) return problem(404, 'not-found');
      // As the API does (API_CONTRACT §Media): the order names exactly the entry's media, each once, or it is a 400.
      const held = found.media.map((m) => m.id);
      const exact =
        body.mediaIds.length === held.length &&
        new Set(body.mediaIds).size === held.length &&
        body.mediaIds.every((id) => held.includes(id));
      if (!exact) return problem(400, 'validation-failed', { mediaIds: 'must list every media' });
      const media = body.mediaIds.map((id, displayOrder) => ({
        ...(found.media.find((m) => m.id === id) as Media),
        displayOrder,
      }));
      state.entries.set(found.id, { ...found, media });
      return HttpResponse.json(media);
    }),

    // The sample feed with every created entry added to its week (a new week when it has none).
    http.get('*/api/v1/feed', () => {
      const weeks = sampleWeeks.map((w) => ({ ...w, entries: [...w.entries] }));
      for (const created of state.entries.values()) {
        const { start, end } = weekOf(created.entryDate);
        const home = weeks.find((w) => w.startDate === start);
        if (home) home.entries.push({ ...created, weeklyLogId: home.id });
        else weeks.push(week(created.weeklyLogId, start, end, [], [created]));
      }
      weeks.sort((a, b) => b.startDate.localeCompare(a.startDate));
      return HttpResponse.json(page(weeks));
    }),
  ];

  return { state, handlers, problem };
}

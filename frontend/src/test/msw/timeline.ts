import { http, HttpResponse } from 'msw';
import type { Tag } from '../../api/tags';
import type { TimelinePage, TimelineRow } from '../../api/timeline';
import { devUser } from './handlers';

// A small stand-in for the server behind the Timeline and the Tags screen (task 4.11). It holds what the database
// would hold, including what the client must never see: a sealed Loot Drop that carries a tag, an entry in Recently
// deleted, and another user's entry. The handlers apply the server's rules from API_CONTRACT (§Entries "The list",
// §Tags) the way its EntryMapper and queries do, so a test can show the screen draws only what such a server sends:
// sealed rows redacted (no title, counts or tags), sealed entries left out of a tag filter and of the counts, and
// deleted or foreign entries nowhere (D-10, D-26, D-30). The backend's own tests (task 3.9, 3.8) prove the server.

/** "Now" for the stand-in server: the sample's Tuesday 24 November 2026, Melbourne morning. */
const NOW = Date.parse('2026-11-24T00:00:00Z');
const OTHER_USER = '00000000-0000-0000-0000-000000000002';

export interface StoredEntry {
  id: string;
  userId: string;
  entryDate: string;
  createdAt: string;
  title: string | null;
  previewLine: string | null;
  photoCount: number;
  voiceNoteCount: number;
  firstMediaKind: TimelineRow['firstMediaKind'];
  tagIds: string[];
  unlockDate: string | null;
  deletedAt: string | null;
}

export interface StoredTag {
  id: string;
  userId: string;
  name: string;
}

function stored(id: string, entryDate: string, fields: Partial<StoredEntry> = {}): StoredEntry {
  return {
    id,
    userId: devUser.id,
    entryDate,
    createdAt: `${entryDate}T03:00:00Z`,
    title: null,
    previewLine: null,
    photoCount: 0,
    voiceNoteCount: 0,
    firstMediaKind: null,
    tagIds: [],
    unlockDate: null,
    deletedAt: null,
    ...fields,
  };
}

/** A fresh copy of the journal, so one test's rename or delete never reaches the next. */
export function timelineJournal() {
  const tags: StoredTag[] = [
    { id: 'tag-work', userId: devUser.id, name: 'Work' },
    { id: 'tag-walks', userId: devUser.id, name: 'Walks' },
    { id: 'tag-trip', userId: devUser.id, name: 'Grampians' },
    // Only a sealed Loot Drop carries this one: it is listed, with 0 (API_CONTRACT §Tags, entryCount).
    { id: 'tag-secret', userId: devUser.id, name: 'Surprise' },
    { id: 'tag-bob', userId: OTHER_USER, name: 'Bob’s tag' },
  ];
  const entries: StoredEntry[] = [
    stored('entry-blues', '2026-11-20', { title: 'Post-trip blues', tagIds: ['tag-work'] }),
    stored('entry-walk', '2026-11-03', {
      previewLine: 'Walked home the long way through Princes Park.',
      photoCount: 1,
      voiceNoteCount: 1,
      firstMediaKind: 'PHOTO',
      tagIds: ['tag-walks', 'tag-work'],
    }),
    // A sealed Loot Drop with a title and a tag the server must never send while it is sealed.
    stored('entry-sealed', '2026-10-25', {
      title: 'The sealed secret title',
      previewLine: 'The sealed secret line',
      photoCount: 2,
      firstMediaKind: 'PHOTO',
      tagIds: ['tag-work', 'tag-secret'],
      unlockDate: '2027-10-24T22:00:00Z',
    }),
    stored('entry-page', '2026-10-24', { photoCount: 1, firstMediaKind: 'PAGE_SCAN' }),
    stored('entry-falls', '2026-10-24', {
      title: 'Mackenzie Falls on the way out',
      photoCount: 1,
      firstMediaKind: 'PHOTO',
      tagIds: ['tag-trip'],
    }),
    // In Recently deleted: left out of every list and count (D-30).
    stored('entry-deleted', '2026-10-20', {
      title: 'The deleted entry',
      tagIds: ['tag-work'],
      deletedAt: '2026-11-20T00:00:00Z',
    }),
    // Another user's entry, with a tag of the dev user's name: never listed or counted.
    stored('entry-bob', '2026-10-19', {
      userId: OTHER_USER,
      title: 'Bob’s entry',
      tagIds: ['tag-work', 'tag-bob'],
    }),
    stored('entry-voice', '2025-10-30', {
      voiceNoteCount: 1,
      firstMediaKind: 'VOICE_NOTE',
      tagIds: ['tag-walks'],
    }),
  ];
  return { tags, entries };
}

export type TimelineJournal = ReturnType<typeof timelineJournal>;

const isSealed = (entry: StoredEntry) =>
  entry.unlockDate !== null && Date.parse(entry.unlockDate) > NOW;

/** The rows EntryMapper.toSummary would build: a sealed entry keeps only its ids and dates (D-10). */
function toRow(journal: TimelineJournal, entry: StoredEntry): TimelineRow {
  const sealed = isSealed(entry);
  const base = {
    id: entry.id,
    weeklyLogId: `week-of-${entry.entryDate}`,
    entryDate: entry.entryDate,
    createdAt: entry.createdAt,
    unlockDate: entry.unlockDate,
    isLocked: sealed,
    dateUnknown: false,
    deletedAt: null,
  };
  if (sealed) {
    return {
      ...base,
      title: null,
      previewLine: null,
      photoCount: null,
      voiceNoteCount: null,
      firstMediaKind: null,
      tags: [],
    };
  }
  return {
    ...base,
    title: entry.title,
    previewLine: entry.previewLine,
    photoCount: entry.photoCount,
    voiceNoteCount: entry.voiceNoteCount,
    firstMediaKind: entry.firstMediaKind,
    tags: journal.tags
      .filter((tag) => entry.tagIds.includes(tag.id))
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((tag) => ({ id: tag.id, name: tag.name, source: 'USER' })),
  };
}

const mine = (journal: TimelineJournal) =>
  journal.entries.filter((entry) => entry.userId === devUser.id && entry.deletedAt === null);

function problem(status: number, suffix: string) {
  return HttpResponse.json(
    {
      type: `urn:memoirist:problem:${suffix}`,
      title: suffix,
      status,
      detail: 'A sentence for the caller.',
      instance: '/api/v1/test',
      timestamp: '2026-11-24T00:00:00Z',
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

/** Every request the handlers saw, so a test can check the page size and the filter the client asked for. */
export type SeenRequest = { method: string; url: URL };

export function timelineHandlers(journal: TimelineJournal, seen: SeenRequest[] = []) {
  return [
    http.get('*/api/v1/entries', ({ request }) => {
      const url = new URL(request.url);
      seen.push({ method: 'GET', url });
      const page = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 50);
      if (size < 1 || size > 100 || page < 0) return problem(400, 'validation-failed');
      const tagId = url.searchParams.get('tagId');
      let rows = mine(journal);
      if (tagId) {
        if (!journal.tags.some((tag) => tag.id === tagId && tag.userId === devUser.id)) {
          return problem(404, 'not-found');
        }
        // Sealed entries are not listed and not counted while filtering (D-10).
        rows = rows.filter((entry) => entry.tagIds.includes(tagId) && !isSealed(entry));
      }
      rows.sort(
        (a, b) =>
          b.entryDate.localeCompare(a.entryDate) ||
          b.createdAt.localeCompare(a.createdAt) ||
          b.id.localeCompare(a.id),
      );
      const items = rows.slice(page * size, page * size + size).map((e) => toRow(journal, e));
      const body: TimelinePage = {
        items,
        page,
        size,
        totalItems: rows.length,
        totalPages: Math.ceil(rows.length / size),
      };
      return HttpResponse.json(body);
    }),
    http.get('*/api/v1/tags', () => HttpResponse.json(tagList(journal))),
    http.put('*/api/v1/tags/:tagId', async ({ params, request }) => {
      const body = (await request.json()) as { name: string };
      seen.push({ method: 'PUT', url: new URL(request.url) });
      const tag = journal.tags.find((t) => t.id === params.tagId && t.userId === devUser.id);
      if (!tag) return problem(404, 'not-found');
      const name = body.name.trim();
      const clash = journal.tags.some(
        (t) =>
          t.userId === devUser.id && t.id !== tag.id && t.name.toLowerCase() === name.toLowerCase(),
      );
      if (clash) return problem(409, 'conflict');
      tag.name = name;
      return HttpResponse.json(tagList(journal).find((t) => t.id === tag.id));
    }),
    http.delete('*/api/v1/tags/:tagId', ({ params, request }) => {
      seen.push({ method: 'DELETE', url: new URL(request.url) });
      const index = journal.tags.findIndex((t) => t.id === params.tagId && t.userId === devUser.id);
      if (index < 0) return problem(404, 'not-found');
      const [gone] = journal.tags.splice(index, 1);
      // ON DELETE CASCADE on entry_tags: every entry loses the tag and keeps the rest.
      for (const entry of journal.entries) {
        entry.tagIds = entry.tagIds.filter((id) => id !== gone!.id);
      }
      return new HttpResponse(null, { status: 204 });
    }),
  ];
}

/** GET /tags: the user's tags by name, each counting only open, live entries (D-10, D-30). */
function tagList(journal: TimelineJournal): Tag[] {
  return journal.tags
    .filter((tag) => tag.userId === devUser.id)
    .sort((a, b) => a.name.localeCompare(b.name))
    .map((tag) => ({
      id: tag.id,
      name: tag.name,
      entryCount: mine(journal).filter((e) => e.tagIds.includes(tag.id) && !isSealed(e)).length,
    }));
}

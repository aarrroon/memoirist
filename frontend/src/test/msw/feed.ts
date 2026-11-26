import type { Entry, FeedPage, Media, Week, WeekEra } from '../../api/feed';

// Feed data for tests (task 4.4), shaped like the local sample content (backend dev/SampleContentSeeder) as
// GET /feed returns it on Tuesday 24 November 2026: weeks newest first, three overlapping Eras, a photo-only
// entry, and a sealed Loot Drop the server has already redacted (D-10). Entries in Recently deleted and other
// users' entries never reach the client: the server leaves them out (its own tests prove that, task 3.7), so
// the client can only be tested with what the server sends, and a sealed entry is the case it must handle.

const at = '2026-11-24T00:00:00Z';

export function era(
  id: string,
  title: string,
  startDate: string,
  endDate: string | null,
  pinnedInFeed = false,
): WeekEra {
  return { id, title, startDate, endDate, pinnedInFeed };
}

/**
 * A photo. The sample pictures are 960 × 640 (backend dev/PlaceholderImages); pass null for one with no size. It has
 * no smaller copies, as a photo from before task 4.18 (D-43); `withCopies` gives it both.
 */
export function photo(
  id: string,
  entryId: string,
  displayOrder = 0,
  size: { width: number; height: number } | null = { width: 960, height: 640 },
): Media {
  return {
    id,
    entryId,
    fileUrl: `http://localhost:8333/memoirist/${id}.png?X-Amz-Signature=test`,
    mediaType: 'image/png',
    purpose: 'PHOTO',
    displayOrder,
    fileSizeBytes: 48_000,
    uploadedAt: at,
    width: size?.width ?? null,
    height: size?.height ?? null,
    thumbnailUrl: null,
    screenUrl: null,
  };
}

/** The photo with a thumbnail and a screen copy, each at its own signed URL (D-43), or only the ones named. */
export function withCopies(
  media: Media,
  copies: ('thumbnail' | 'screen')[] = ['thumbnail', 'screen'],
): Media {
  return {
    ...media,
    thumbnailUrl: copies.includes('thumbnail')
      ? `http://localhost:8333/memoirist/${media.id}-thumbnail.jpg?X-Amz-Signature=test`
      : null,
    screenUrl: copies.includes('screen')
      ? `http://localhost:8333/memoirist/${media.id}-screen.jpg?X-Amz-Signature=test`
      : null,
  };
}

/** A voice note: no size (D-17). */
export function voiceNote(id: string, entryId: string, displayOrder = 0): Media {
  return {
    ...photo(id, entryId, displayOrder, null),
    fileUrl: `http://localhost:8333/memoirist/${id}.webm?X-Amz-Signature=test`,
    mediaType: 'audio/webm',
  };
}

/** A caption as the server stores it: a TipTap document of one paragraph per line (D-05). */
export function doc(...paragraphs: string[]): Entry['contentJson'] {
  return {
    type: 'doc',
    content: paragraphs.map((text) => ({ type: 'paragraph', content: [{ type: 'text', text }] })),
  } as unknown as Entry['contentJson'];
}

export function entry(id: string, entryDate: string, overrides: Partial<Entry> = {}): Entry {
  return {
    id,
    weeklyLogId: 'week',
    entryDate,
    templateId: null,
    title: null,
    contentJson: null,
    contentText: null,
    tags: [],
    soundtrack: null,
    replyToEntryId: null,
    replyIds: [],
    createdAt: at,
    updatedAt: at,
    unlockDate: null,
    isLocked: false,
    dateUnknown: false,
    version: 0,
    media: [],
    ...overrides,
  };
}

/** A Loot Drop as the server's EntryMapper sends it while sealed: nothing private, no media, no title. */
export function sealedEntry(id: string, entryDate: string, unlockDate: string): Entry {
  return entry(id, entryDate, { unlockDate, isLocked: true });
}

export function week(
  id: string,
  startDate: string,
  endDate: string,
  eras: WeekEra[],
  entries: Entry[],
  title: string | null = null,
): Week {
  return {
    id,
    title,
    startDate,
    endDate,
    isClosed: false,
    soundtrack: null,
    eras,
    entries: entries.map((e) => ({ ...e, weeklyLogId: id })),
  };
}

export function page(items: Week[], pageNumber = 0, totalPages = 1, size = 10): FeedPage {
  return { items, page: pageNumber, size, totalItems: items.length, totalPages };
}

export const uniDays = era('era-uni', 'Uni Days', '2025-10-17', '2026-11-18');
export const tasmania = era('era-tas', 'Tasmania', '2026-11-03', '2026-11-07');
export const newJob = era('era-job', 'New job', '2026-11-09', null);

const trip = { id: 'tag-trip', name: 'Trip', source: 'USER' };
const family = { id: 'tag-family', name: 'Family', source: 'USER' };

/** The sample journal's feed, one page. */
export const sampleWeeks: Week[] = [
  week(
    'week-this',
    '2026-11-23',
    '2026-11-29',
    [newJob],
    [
      entry('entry-today', '2026-11-24', {
        title: 'Quiet day',
        contentJson: doc('Nothing happened, and that was lovely.'),
        contentText: 'Nothing happened, and that was lovely.',
        tags: [family],
      }),
    ],
  ),
  week(
    'week-last',
    '2026-11-16',
    '2026-11-22',
    [newJob, uniDays],
    [
      entry('entry-photos', '2026-11-17', {
        media: [photo('media-1', 'entry-photos'), photo('media-2', 'entry-photos', 1)],
      }),
      sealedEntry('entry-sealed', '2026-11-19', '2027-01-24T22:00:00Z'),
      entry('entry-sunday', '2026-11-22', { title: 'Sunday reset' }),
    ],
  ),
  week(
    'week-trip',
    '2026-11-02',
    '2026-11-08',
    [tasmania, uniDays],
    [
      entry('entry-hobart', '2026-11-03', {
        title: 'Day one in Hobart',
        contentJson: doc('Landed at nine, wandered the waterfront.'),
        contentText: 'Landed at nine, wandered the waterfront.',
        tags: [trip],
        media: [photo('media-hobart', 'entry-hobart')],
      }),
    ],
    'The trip',
  ),
  week(
    'week-old',
    '2026-10-12',
    '2026-10-18',
    [uniDays],
    [entry('entry-letter', '2026-10-14', { title: 'Letter to future me' })],
  ),
];

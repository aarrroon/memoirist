import { MAX_FILES } from '../../lib/files';
import type { IsoDate } from '../../lib/time';

// How a photo dump becomes entries (D-34, F-19, task 4.13): one entry per day the photos were taken, a day with more
// than 20 photos split into several entries of up to 20 (D-17), and the photos with no date in their own entry dated
// today and marked date unknown. Pure, so the rule is tested without a browser.

/** One picked photo, as the plan sees it: its key and the day it was taken (null when it has no readable date). */
export interface DumpPhoto {
  key: string;
  day: IsoDate | null;
}

/** One entry the dump will create. */
export interface PlannedEntry<P extends DumpPhoto = DumpPhoto> {
  /** Stable while the photos don't change ("2026-10-23#0", "unknown#1"): the key its client id is kept under. */
  key: string;
  entryDate: IsoDate;
  dateUnknown: boolean;
  photos: P[];
}

/** The photos of one day, as the dump shows them: one heading, every photo, and how many entries it makes. */
export interface DumpDay<P extends DumpPhoto = DumpPhoto> {
  /** null for the photos with no date. */
  day: IsoDate | null;
  photos: P[];
  entries: PlannedEntry<P>[];
}

// TODO(human) PRACTICE: Write planDump(photos, today): the days of a photo dump, oldest first with the undated photos
// last, each with the entries it becomes. Keep the photos of a day in the order they were picked. Cut a day into
// entries of at most MAX_FILES (20) photos, so 25 photos make one entry of 20 and one of 5. Undated photos are dated
// `today` with dateUnknown: true (D-34). Give each entry a key from its day and its place in the day
// ("2026-10-23#1"; "unknown#0" for the undated), because the client id that makes a retry safe (D-12) is kept under
// that key between attempts.
// Hint: a Map keeps insertion order, so group first, then sort the keys; ISO days sort correctly as strings. Think
// about why the undated photos can't simply be grouped under `today`: photos taken today are a separate entry, which
// is not marked date unknown. The same shape splits a bank's batch of payments into files under a size limit.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function planDump<P extends DumpPhoto>(photos: P[], today: IsoDate): DumpDay<P>[] {
  const byDay = new Map<IsoDate | null, P[]>();
  for (const photo of photos) {
    const list = byDay.get(photo.day);
    if (list) list.push(photo);
    else byDay.set(photo.day, [photo]);
  }
  const days = [...byDay.keys()].sort((a, b) => {
    if (a === b) return 0;
    if (a === null) return 1;
    if (b === null) return -1;
    return a < b ? -1 : 1;
  });
  return days.map((day) => {
    const list = byDay.get(day) ?? [];
    const entries: PlannedEntry<P>[] = [];
    for (let start = 0; start < list.length; start += MAX_FILES) {
      entries.push({
        key: `${day ?? 'unknown'}#${start / MAX_FILES}`,
        entryDate: day ?? today,
        dateUnknown: day === null,
        photos: list.slice(start, start + MAX_FILES),
      });
    }
    return { day, photos: list, entries };
  });
}
// └── END ANSWER ▲ ──

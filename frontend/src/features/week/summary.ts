import type { Entry, Media, Week } from '../../api/feed';
import { isPicture } from '../../lib/media';

// The figures at the top of a week's summary (task 4.10, D-25, F-15): how many entries, photos and voice notes, a
// few thumbnails, and the tags used that week with how often. A pure function of the week the server sent, so it is
// tested on its own (summary.test.ts) and the page only draws what it returns.

/** Thumbnails at most: two rows of five (DESIGN.md §6, "equal thumbnails, five across"; the round 3 mockup). */
export const MAX_THUMBNAILS = 10;

export interface Thumbnail {
  media: Media;
  entry: Entry;
}

export interface TagCount {
  id: string;
  name: string;
  count: number;
}

export interface WeekSummary {
  entries: number;
  photos: number;
  voiceNotes: number;
  thumbnails: Thumbnail[];
  /** Most used first, then by name, so the chips don't shuffle between reads. */
  tags: TagCount[];
}

// TODO(human) PRACTICE: Write summarise(week): count the week's readable entries, its photos (diary pages count as
// photos) and its voice notes; collect up to MAX_THUMBNAILS pictures in the week's order; and count each tag, most
// used first, ties by name.
// Hint: a sealed Loot Drop never shows up in a count or a filter (D-10, CLAUDE.md non-negotiable 4). The server
// already sends it empty, but don't lean on that: skip `isLocked` entries first, then work on what is left. A Map
// keyed by tag id counts in one pass; sort a copy, and use localeCompare for the names.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function summarise(week: Week): WeekSummary {
  const readable = week.entries.filter((entry) => !entry.isLocked);
  const summary: WeekSummary = {
    entries: readable.length,
    photos: 0,
    voiceNotes: 0,
    thumbnails: [],
    tags: [],
  };
  const tags = new Map<string, TagCount>();

  for (const entry of readable) {
    const media = [...entry.media].sort((a, b) => a.displayOrder - b.displayOrder);
    for (const item of media) {
      if (isPicture(item)) {
        summary.photos += 1;
        if (summary.thumbnails.length < MAX_THUMBNAILS)
          summary.thumbnails.push({ media: item, entry });
      } else {
        summary.voiceNotes += 1;
      }
    }
    for (const tag of entry.tags) {
      const seen = tags.get(tag.id);
      if (seen) seen.count += 1;
      else tags.set(tag.id, { id: tag.id, name: tag.name, count: 1 });
    }
  }

  summary.tags = [...tags.values()].sort(
    (a, b) => b.count - a.count || a.name.localeCompare(b.name),
  );
  return summary;
}
// └── END ANSWER ▲ ──

/** The media counts on one entry's bar: pictures (photos and diary pages) and voice notes. */
export function mediaCounts(entry: Entry): { pictures: number; voiceNotes: number } {
  const pictures = entry.media.filter(isPicture).length;
  return { pictures, voiceNotes: entry.media.length - pictures };
}

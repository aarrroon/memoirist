import { addMinutes } from 'date-fns';
import { dayIn, EARLIEST_DAY, type IsoDate } from './time';

// A photo's own date (D-34, task 4.13): the day it was taken, read from its EXIF before it is shrunk, because
// shrinking re-encodes the picture and drops its metadata (lib/files.ts). Capture offers that day as the memory date,
// and a photo dump files each photo under it. Only the date is read; nothing else in the EXIF (GPS included, Q19)
// is looked at or kept.

/** The EXIF tags a photo's date is read from, as exifr gives them with `reviveValues: false`: raw strings. */
export interface PhotoDateTags {
  /** When the shutter fired: "2026:10:23 21:30:00", the camera's own wall clock, with no zone. */
  DateTimeOriginal?: unknown;
  /** The camera's offset from UTC at that moment, "+11:00" (EXIF 2.31). Many cameras and older phones leave it out. */
  OffsetTimeOriginal?: unknown;
  /** When the picture was stored (DateTimeDigitized; exifr names it CreateDate), for a photo with no original time. */
  CreateDate?: unknown;
  OffsetTimeDigitized?: unknown;
}

const WALL_CLOCK = /^(\d{4}):(\d{2}):(\d{2})[ T](\d{2}):(\d{2}):(\d{2})/;
const OFFSET = /^([+-])(\d{2}):(\d{2})$/;

// TODO(human) PRACTICE: Write photoDay(tags, zone): the calendar day a photo was taken, in the user's zone, or null.
// Read DateTimeOriginal (else CreateDate) with WALL_CLOCK, and refuse anything that isn't a real date: cameras write
// "0000:00:00 00:00:00" when their clock was never set, and a broken file can say month 13. If the matching offset
// tag (OffsetTimeOriginal, else OffsetTimeDigitized) is there, the time is an instant: work out the UTC moment and
// give the day it falls on in `zone` (dayIn). Without an offset, the time is only a reading of the camera's clock,
// so give the day it shows as it is.
// Hint: never hand "2026-10-23 21:30:00" to new Date(): it would be read in the browser's zone (non-negotiable 2).
// Build the instant with Date.UTC from the parts, then move it by the offset (UTC = local time − offset). Check
// photoDate.test.ts: 21:30 on 23 Oct at +11:00 in Melbourne stays the 23rd, the same photo at -07:00 is the 24th in
// Melbourne, and 30 February must be refused (Date.UTC quietly rolls it over to 2 March).
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function photoDay(tags: PhotoDateTags, zone: string): IsoDate | null {
  const original = typeof tags.DateTimeOriginal === 'string';
  const raw = original ? tags.DateTimeOriginal : tags.CreateDate;
  const offsetRaw = original ? tags.OffsetTimeOriginal : tags.OffsetTimeDigitized;
  if (typeof raw !== 'string') return null;
  const parts = WALL_CLOCK.exec(raw.trim());
  if (!parts) return null;
  const [year, month, day, hour, minute, second] = parts.slice(1).map(Number) as [
    number,
    number,
    number,
    number,
    number,
    number,
  ];
  const wall = new Date(Date.UTC(year, month - 1, day, hour, minute, second));
  // Date.UTC rolls 30 February over to 2 March; a real date reads back the same parts.
  if (
    wall.getUTCFullYear() !== year ||
    wall.getUTCMonth() !== month - 1 ||
    wall.getUTCDate() !== day ||
    hour > 23
  ) {
    return null;
  }
  // Not through lib/time.ts on purpose: `wall` was built with Date.UTC, so its UTC parts are the camera's own day.
  const iso = wall.toISOString().slice(0, 10);
  const offset = typeof offsetRaw === 'string' ? OFFSET.exec(offsetRaw.trim()) : null;
  if (!offset) return iso;
  const sign = offset[1] === '-' ? -1 : 1;
  const minutes = sign * (Number(offset[2]) * 60 + Number(offset[3]));
  return dayIn(addMinutes(wall, -minutes), zone);
}
// └── END ANSWER ▲ ──

/** Whether a photo's day can be a memory date: from 1900 (D-09) up to today in the user's zone, never after it.
 *  A camera whose clock was wrong can say 2041; such a photo counts as undated. */
export function usableDay(day: IsoDate | null, today: IsoDate): IsoDate | null {
  if (day === null || day < EARLIEST_DAY || day > today) return null;
  return day;
}

// JPEG keeps its EXIF in an APP1 segment near the start (at most 64 KB), so only the head of a big file is read.
// PNG and WebP can keep theirs anywhere, so those are read whole.
const JPEG_HEAD = 256 * 1024;

/**
 * The day a picked photo was taken, in the user's zone, or null when it has no readable date (a screenshot, a WebP,
 * a broken file). Never throws: a photo without a date is filed as *date unknown* (D-34), not refused. exifr is
 * imported when first needed, so it loads with capture, not with the feed (design.md §6).
 */
export async function readPhotoDay(file: Blob, zone: string): Promise<IsoDate | null> {
  if (!file.type.startsWith('image/')) return null;
  try {
    const { default: exifr } = await import('exifr');
    const head = file.type === 'image/jpeg' ? file.slice(0, JPEG_HEAD) : file;
    // Bytes rather than the Blob itself: exifr reads a Blob with FileReader, which some test and worker
    // environments lack; an ArrayBuffer works everywhere.
    const bytes = new Uint8Array(await head.arrayBuffer());
    const tags = (await exifr.parse(bytes, {
      pick: ['DateTimeOriginal', 'OffsetTimeOriginal', 'CreateDate', 'OffsetTimeDigitized'],
      reviveValues: false,
    })) as PhotoDateTags | undefined;
    return tags ? photoDay(tags, zone) : null;
  } catch {
    return null;
  }
}

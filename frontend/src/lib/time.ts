import { TZDate } from '@date-fns/tz';
import { format } from 'date-fns';

// The one place dates are worked out and written for the screen (CLAUDE.md, .claude/rules/frontend.md, task 4.3).
// Every function that depends on "now" takes the user's IANA timezone from GET /users/me (users.timezone, default
// Australia/Melbourne) and an explicit instant. Nothing here reads the browser's zone: a Melbourne user on a laptop
// set to London still sees Melbourne's today, exactly as the server files their entries (non-negotiable 2).
//
// TZDate (from @date-fns/tz) is a Date whose getters and date-fns' format() answer in its own zone, so the zone
// travels with the value and daylight saving is the IANA database's job, not ours.

/** An ISO calendar day, "2026-10-04": what the API sends for entryDate, weekStart and the like. */
export type IsoDate = string;

/** The part of the day the moon line names: "Wednesday morning", "Wednesday at dusk" (DESIGN.md §4 Shell). */
export type PartOfDay = 'morning' | 'afternoon' | 'at dusk' | 'at night';

/** "Today" in the user's zone, as an ISO day. The memory-date picker's default and its upper limit (D-09). */
export function todayIn(zone: string, now: Date = new Date()): IsoDate {
  return format(new TZDate(now, zone), 'yyyy-MM-dd');
}

// TODO(human) PRACTICE: Write partOfDay(now, zone): which part of the day it is for the user, in their zone.
// DESIGN.md §4 says: morning to 12:00, afternoon to 17:00, at dusk to 21:00, then at night until midnight.
// Hint: the hour must come from the user's zone, not from now.getHours() (the browser's zone). Build a TZDate
// from the instant and the zone; time.test.ts has a Melbourne daylight-saving case where a fixed +10 offset
// would give the wrong answer. Check each boundary is half-open: 12:00 itself is afternoon.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function partOfDay(now: Date, zone: string): PartOfDay {
  const hour = new TZDate(now, zone).getHours();
  if (hour < 12) return 'morning';
  if (hour < 17) return 'afternoon';
  if (hour < 21) return 'at dusk';
  return 'at night';
}
// └── END ANSWER ▲ ──

/** The moon line's greeting: "Wednesday at dusk", "Thursday morning" (DESIGN.md §4 Shell). */
export function greeting(now: Date, zone: string): string {
  const part = partOfDay(now, zone);
  const weekday = format(new TZDate(now, zone), 'EEEE');
  // "Wednesday morning" and "Wednesday afternoon" read without a joining word; the other two carry their own.
  return `${weekday} ${part}`;
}

/** Parses "2026-10-04" as that calendar day. A day has no zone, so it is held at midnight UTC and formatted there. */
function calendarDay(day: IsoDate): TZDate {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day);
  if (!match) throw new RangeError('Not an ISO calendar day');
  const [, year, month, date] = match;
  return new TZDate(Number(year), Number(month) - 1, Number(date), 'UTC');
}

/**
 * A calendar day in words: "Monday 26 October", or "Monday 26 October 2026" with the year. Calendar days (entryDate,
 * a week's start) are the same everywhere, so no zone is needed: only "today" depends on one.
 */
export function formatDay(day: IsoDate, options: { year?: boolean } = {}): string {
  return format(calendarDay(day), options.year ? 'EEEE d MMMM yyyy' : 'EEEE d MMMM');
}

/** The feed's day line: "Today", "Yesterday", or the day in words (DESIGN.md §4 Feed). */
export function dayLabel(day: IsoDate, zone: string, now: Date = new Date()): string {
  const today = todayIn(zone, now);
  if (day === today) return 'Today';
  const yesterday = format(
    new TZDate(calendarDay(today).getTime() - 86_400_000, 'UTC'),
    'yyyy-MM-dd',
  );
  if (day === yesterday) return 'Yesterday';
  return formatDay(day);
}

/**
 * A moment in the user's zone, in words: "Thursday 28 October 2027 at 9:00 am" (a Loot Drop's opening moment,
 * DESIGN.md §4). The time and "am" are joined by a no-break space so a line never splits "9:00 / am".
 */
export function formatMoment(instant: string | Date, zone: string): string {
  return format(new TZDate(new Date(instant), zone), "EEEE d MMMM yyyy 'at' h:mm aaa");
}

/** Whether `day` falls inside the week from `start` to `end` (both ISO days, both included). */
export function isWithin(day: IsoDate, start: IsoDate, end: IsoDate): boolean {
  // ISO days compare correctly as strings: "2026-11-23" < "2026-11-29".
  return start <= day && day <= end;
}

/**
 * A week's dates, as short as they can be read (task 4.4, the week line): "16–22 November", "30 November – 6 December",
 * "28 December 2026 – 3 January 2027". The year shows only when the week crosses one.
 */
export function formatWeekRange(start: IsoDate, end: IsoDate): string {
  const from = calendarDay(start);
  const to = calendarDay(end);
  if (from.getFullYear() !== to.getFullYear()) {
    return `${format(from, 'd MMMM yyyy')} – ${format(to, 'd MMMM yyyy')}`;
  }
  if (from.getMonth() !== to.getMonth()) {
    return `${format(from, 'd MMMM')} – ${format(to, 'd MMMM')}`;
  }
  return `${format(from, 'd')}–${format(to, 'd MMMM')}`;
}

/** The laptop feed's date margin (DESIGN.md §4 Feed): the weekday over "26 Oct". */
export function dayParts(day: IsoDate): { weekday: string; short: string } {
  const date = calendarDay(day);
  return { weekday: format(date, 'EEEE'), short: format(date, 'd MMM') };
}

/** A Loot Drop's opening day in the user's zone, as the sealed card writes it: "Mon 25 Oct 2027" (DESIGN.md §4). */
export function formatOpens(instant: string | Date, zone: string): string {
  return format(new TZDate(new Date(instant), zone), 'EEE d MMM yyyy');
}

/**
 * The countdown on a sealed card: "in 362 days", "tomorrow", or "today" while the opening day has come but its
 * 9:00 am hasn't (D-23). Days are counted between calendar days in the user's zone, so a Loot Drop opening at
 * 9:00 am tomorrow is "tomorrow" at 11 pm tonight, and a daylight-saving change (a 23 or 25 hour day) can't make it
 * one off.
 */
export function countdown(instant: string | Date, zone: string, now: Date = new Date()): string {
  const days = Math.round(
    (calendarDay(format(new TZDate(new Date(instant), zone), 'yyyy-MM-dd')).getTime() -
      calendarDay(todayIn(zone, now)).getTime()) /
      86_400_000,
  );
  if (days <= 0) return 'today';
  if (days === 1) return 'tomorrow';
  return `in ${days} days`;
}

/** A moment's time of day in the user's zone, as capture's "Draft saved 7:41 pm" writes it (DESIGN.md §2, meta). */
export function formatClock(instant: string | Date, zone: string): string {
  return format(new TZDate(new Date(instant), zone), 'h:mm aaa');
}

/** The Monday-to-Sunday week holding a calendar day (D-08: every week starts on Monday, for every user). */
export function weekOf(day: IsoDate): { start: IsoDate; end: IsoDate } {
  const date = calendarDay(day);
  // getDay() is 0 for Sunday; Monday is 1. Days back to Monday: Sunday 6, Monday 0, Tuesday 1, ...
  const back = (date.getDay() + 6) % 7;
  const start = new TZDate(date.getTime() - back * 86_400_000, 'UTC');
  const end = new TZDate(start.getTime() + 6 * 86_400_000, 'UTC');
  return { start: format(start, 'yyyy-MM-dd'), end: format(end, 'yyyy-MM-dd') };
}

/**
 * The memory-date chip in capture (DESIGN.md §4 Write): "Today, Wed 28 Oct", "Mon 26 Oct", or "Fri 14 Mar 2003"
 * when the day is in another year than today.
 */
export function chipDay(day: IsoDate, zone: string, now: Date = new Date()): string {
  const today = todayIn(zone, now);
  const date = calendarDay(day);
  if (day === today) return `Today, ${format(date, 'EEE d MMM')}`;
  return format(date, day.slice(0, 4) === today.slice(0, 4) ? 'EEE d MMM' : 'EEE d MMM yyyy');
}

/**
 * A calendar day as the date picker's Date (react-day-picker works in the browser's own zone): midnight of that day
 * on the browser's clock, so the picker shows the same day whatever zone the browser is in. Only the picker uses it.
 */
export function toPickerDate(day: IsoDate): Date {
  const date = calendarDay(day);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

/** The day the picker chose, read back on the browser's clock it was made on (toPickerDate's inverse). */
export function fromPickerDate(date: Date): IsoDate {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** The earliest memory date the API accepts (D-09). */
export const EARLIEST_DAY: IsoDate = '1900-01-01';

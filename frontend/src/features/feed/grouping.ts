import type { Week, WeekEra } from '../../api/feed';

// How the feed files weeks under Eras (D-22, task 4.4). The server lists each week once with every Era whose
// dates overlap it, pinned Eras first, then the latest start (WeekEras.of). Grouping is the client's job:
//  - a week goes under eras[0], its "home";
//  - when the home sits wholly inside another of the week's Eras, the week is shown in a section nested inside
//    that bigger Era, one level deep (a trip inside "Uni Days");
//  - every other Era of the week is a chip on it (partial overlaps).
// Pure functions over the API's own types, so the rules are unit-tested without rendering anything.

/** A week with the Eras it is filed under, worked out from its `eras` list. */
export interface PlacedWeek {
  week: Week;
  /** The Era the week is grouped under: eras[0], or null for a week in no Era. */
  home: WeekEra | null;
  /** The bigger Era the home sits inside, when there is one: the week is nested one level deep under it. */
  parent: WeekEra | null;
  /** The week's other Eras, shown as chips. */
  chips: WeekEra[];
}

/** Weeks next to each other that share a nested Era (the trip's weeks), or plain weeks with no nesting. */
export interface FeedSection {
  /** The nested Era, or null when these weeks sit directly under the group's Era. */
  nested: WeekEra | null;
  weeks: PlacedWeek[];
}

/** A run of weeks, next to each other in the feed, under one top-level Era (or under none). */
export interface FeedGroup {
  /** A stable React key: the Era's id, or "none", with the position of the run (an Era can come back later). */
  key: string;
  era: WeekEra | null;
  sections: FeedSection[];
}

// TODO(human) PRACTICE: Write isInside(inner, outer): whether the Era `inner` sits wholly inside `outer` (D-22:
// "it starts on or after it, and ends on or before it; an Era with no end date runs on").
// Hint: dates are ISO days ("2026-11-03"), which compare correctly as strings. Think through every null endDate:
// an ongoing outer Era holds any inner one that starts in time; an ongoing inner Era fits only inside an ongoing
// outer one. Equal ends count as inside. grouping.test.ts has a case for each.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function isInside(inner: WeekEra, outer: WeekEra): boolean {
  if (inner.startDate < outer.startDate) return false;
  if (outer.endDate === null) return true;
  if (inner.endDate === null) return false;
  return inner.endDate <= outer.endDate;
}
// └── END ANSWER ▲ ──

/** Where one week goes: its home Era, the bigger Era it nests in (if any), and its chips. */
export function placeWeek(week: Week): PlacedWeek {
  const [home = null, ...others] = week.eras;
  // The first of the week's other Eras that holds the home, in the server's order (pinned, then latest start).
  const parent = home ? (others.find((era) => isInside(home, era)) ?? null) : null;
  const chips = others.filter((era) => era !== parent);
  return { week, home, parent, chips };
}

/**
 * Turns the feed's weeks (newest first, as loaded so far) into runs under their top-level Era, each split into
 * sections where weeks nest inside it. A week the server repeats on a later page (the feed moved while paging)
 * is shown once, where it first appeared.
 */
export function groupFeed(weeks: readonly Week[]): FeedGroup[] {
  const groups: FeedGroup[] = [];
  const seen = new Set<string>();

  for (const week of weeks) {
    if (seen.has(week.id)) continue;
    seen.add(week.id);

    const placed = placeWeek(week);
    const top = placed.parent ?? placed.home;
    const nested = placed.parent ? placed.home : null;

    let group = groups.at(-1);
    if (!group || group.era?.id !== top?.id) {
      group = { key: `${top?.id ?? 'none'}:${groups.length}`, era: top, sections: [] };
      groups.push(group);
    }

    const section = group.sections.at(-1);
    if (section && section.nested?.id === nested?.id) {
      section.weeks.push(placed);
    } else {
      group.sections.push({ nested, weeks: [placed] });
    }
  }
  return groups;
}

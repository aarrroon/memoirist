import type { Era } from '../../api/eras';
import type { WeekEra } from '../../api/feed';
import { isInside } from '../feed/grouping';

/** One row of the Eras list: the Era, and whether it sits inside a bigger one (indented 24 px, DESIGN.md §6). */
export interface EraRow {
  era: Era;
  nested: boolean;
}

/** Whether `outer` holds `inner` and is bigger: two Eras with the same dates hold each other, so neither nests. */
function holds(outer: WeekEra, inner: WeekEra): boolean {
  return isInside(inner, outer) && !isInside(outer, inner);
}

/**
 * The Eras list (task 4.9): every Era that no bigger Era holds, in the server's order (latest start first), each
 * followed by the Eras inside it, one level deep. An Era inside several goes under the first top-level Era that
 * holds it, so every Era is listed exactly once. Nesting is D-22's rule, the same isInside the feed uses.
 */
export function eraRows(eras: readonly Era[]): EraRow[] {
  const tops = eras.filter((era) => !eras.some((other) => other !== era && holds(other, era)));
  const rows: EraRow[] = [];
  for (const top of tops) {
    rows.push({ era: top, nested: false });
    for (const era of eras) {
      if (tops.includes(era)) continue;
      const home = tops.find((candidate) => holds(candidate, era));
      if (home === top) rows.push({ era, nested: true });
    }
  }
  return rows;
}

/** The Eras inside this one, for "Eras inside this one" on an Era page (DESIGN.md §6, §9). */
export function erasInside(era: WeekEra, eras: readonly Era[]): Era[] {
  return eras.filter((other) => other.id !== era.id && holds(era, other));
}

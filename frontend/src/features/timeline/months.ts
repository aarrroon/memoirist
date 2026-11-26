import type { TimelineRow } from '../../api/timeline';
import { formatMonth, monthKey } from '../../lib/time';

/** One month of the Timeline: its sticky heading and its rows, newest first (DESIGN.md §6). */
export interface MonthGroup {
  /** "2026-10": stable across pages, so React keeps the section when the next page adds rows to it. */
  key: string;
  /** "October 2026". */
  label: string;
  rows: TimelineRow[];
}

// TODO(human) PRACTICE: Write groupByMonth(rows): split the Timeline's rows (already newest first, every page loaded
// so far joined in order) into one group per calendar month of `entryDate`, keeping the server's order inside each
// group and between groups.
// Hint: this is a bank statement's "group transactions by month". The list is already sorted, so one pass that
// starts a new group whenever the month changes is enough: no Map, no sort (sorting again could disagree with the
// server's tie-breaks on createdAt and id). A month split across two pages must stay one group, which is why the
// caller joins the pages before grouping. A sealed row has its entryDate too (D-10), so it groups like any other.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function groupByMonth(rows: readonly TimelineRow[]): MonthGroup[] {
  const groups: MonthGroup[] = [];
  for (const row of rows) {
    const key = monthKey(row.entryDate);
    const last = groups.at(-1);
    if (last?.key === key) last.rows.push(row);
    else groups.push({ key, label: formatMonth(row.entryDate), rows: [row] });
  }
  return groups;
}
// └── END ANSWER ▲ ──

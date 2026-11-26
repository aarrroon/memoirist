import { describe, expect, test } from 'vitest';
import type { TimelineRow } from '../../api/timeline';
import { groupByMonth } from './months';

// Task 4.11: the Timeline's month headings (DESIGN.md §6).

function row(id: string, entryDate: string, isLocked = false): TimelineRow {
  return {
    id,
    weeklyLogId: 'week',
    entryDate,
    title: isLocked ? null : id,
    previewLine: null,
    photoCount: isLocked ? null : 0,
    voiceNoteCount: isLocked ? null : 0,
    firstMediaKind: null,
    tags: [],
    createdAt: `${entryDate}T00:00:00Z`,
    unlockDate: isLocked ? '2027-10-24T22:00:00Z' : null,
    isLocked,
    dateUnknown: false,
    deletedAt: null,
  };
}

describe('groupByMonth', () => {
  test('an empty list has no months', () => {
    expect(groupByMonth([])).toEqual([]);
  });

  test('splits rows by calendar month, keeping the server order inside and between months', () => {
    const rows = [
      row('a', '2026-11-01'),
      row('b', '2026-10-31'),
      row('c', '2026-10-31'),
      row('d', '2026-10-01'),
      row('e', '2025-10-30'),
    ];
    const groups = groupByMonth(rows);
    expect(groups.map((g) => [g.key, g.label, g.rows.map((r) => r.id)])).toEqual([
      ['2026-11', 'November 2026', ['a']],
      ['2026-10', 'October 2026', ['b', 'c', 'd']],
      // The same month a year earlier is its own group.
      ['2025-10', 'October 2025', ['e']],
    ]);
  });

  test('a sealed row groups by its memory date like any other (D-10)', () => {
    const groups = groupByMonth([row('open', '2026-10-28'), row('sealed', '2026-10-25', true)]);
    expect(groups).toHaveLength(1);
    expect(groups[0]!.rows.map((r) => r.id)).toEqual(['open', 'sealed']);
  });

  test('a month split across two pages is one group once the pages are joined', () => {
    const page1 = [row('a', '2026-10-28'), row('b', '2026-10-20')];
    const page2 = [row('c', '2026-10-02'), row('d', '2026-09-30')];
    expect(groupByMonth([...page1, ...page2]).map((g) => g.rows.length)).toEqual([3, 1]);
  });
});

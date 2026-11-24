import { describe, expect, test } from 'vitest';
import { era, newJob, sampleWeeks, tasmania, uniDays, week } from '../../test/msw/feed';
import { groupFeed, isInside, placeWeek } from './grouping';

// Plain unit tests of the D-22 filing rules (frontend.md: plain Vitest for pure logic).

describe('isInside: one Era wholly inside another (D-22)', () => {
  const year = era('y', 'Year', '2026-01-01', '2026-12-31');
  const ongoing = era('o', 'Ongoing', '2026-01-01', null);

  test('a trip inside a year is inside it; the year is not inside the trip', () => {
    expect(isInside(tasmania, uniDays)).toBe(true);
    expect(isInside(uniDays, tasmania)).toBe(false);
  });

  test('equal start and end days count as inside', () => {
    expect(isInside(era('a', 'A', '2026-01-01', '2026-12-31'), year)).toBe(true);
  });

  test('starting a day early, or ending a day late, is not inside', () => {
    expect(isInside(era('a', 'A', '2025-12-31', '2026-06-01'), year)).toBe(false);
    expect(isInside(era('a', 'A', '2026-06-01', '2027-01-01'), year)).toBe(false);
  });

  test('an Era with no end date runs on: it holds anything that starts in time', () => {
    expect(isInside(tasmania, ongoing)).toBe(true);
    expect(isInside(era('a', 'A', '2026-03-01', null), ongoing)).toBe(true);
    expect(isInside(era('a', 'A', '2025-03-01', null), ongoing)).toBe(false);
  });

  test('an ongoing Era never fits inside one that ends', () => {
    expect(isInside(newJob, uniDays)).toBe(false);
    expect(isInside(ongoing, year)).toBe(false);
  });
});

describe('placeWeek', () => {
  test('a week in no Era has no home, no parent and no chips', () => {
    expect(placeWeek(week('w', '2026-11-23', '2026-11-29', [], []))).toMatchObject({
      home: null,
      parent: null,
      chips: [],
    });
  });

  test('partial overlap: the week goes under eras[0] and the other is a chip', () => {
    const placed = placeWeek(week('w', '2026-11-16', '2026-11-22', [newJob, uniDays], []));
    expect(placed.home).toBe(newJob);
    expect(placed.parent).toBeNull();
    expect(placed.chips).toEqual([uniDays]);
  });

  test('a trip inside a bigger Era nests in it, and the bigger Era is not also a chip', () => {
    const placed = placeWeek(week('w', '2026-11-02', '2026-11-08', [tasmania, uniDays], []));
    expect(placed.home).toBe(tasmania);
    expect(placed.parent).toBe(uniDays);
    expect(placed.chips).toEqual([]);
  });

  test('a pinned Era comes first from the server and wins, even when it is the bigger one', () => {
    const pinnedUni = { ...uniDays, pinnedInFeed: true };
    const placed = placeWeek(week('w', '2026-11-02', '2026-11-08', [pinnedUni, tasmania], []));
    expect(placed.home).toBe(pinnedUni);
    expect(placed.parent).toBeNull();
    expect(placed.chips).toEqual([tasmania]);
  });

  test('nesting is one level deep: the first bigger Era holds it, the rest are chips', () => {
    const decade = era('d', 'Twenties', '2020-01-01', null);
    const placed = placeWeek(
      week('w', '2026-11-02', '2026-11-08', [tasmania, uniDays, decade, newJob], []),
    );
    expect(placed.parent).toBe(uniDays);
    expect(placed.chips).toEqual([decade, newJob]);
  });
});

describe('groupFeed', () => {
  test('the sample journal: New job over three weeks, then Uni Days with the trip nested in it', () => {
    const groups = groupFeed(sampleWeeks);

    expect(groups.map((g) => g.era?.title)).toEqual(['New job', 'Uni Days']);
    expect(groups[0]!.sections).toHaveLength(1);
    expect(groups[0]!.sections[0]!.weeks.map((w) => w.week.id)).toEqual(['week-this', 'week-last']);

    const uni = groups[1]!.sections;
    expect(uni.map((s) => s.nested?.title ?? null)).toEqual(['Tasmania', null]);
    expect(uni[0]!.weeks.map((w) => w.week.id)).toEqual(['week-trip']);
    expect(uni[1]!.weeks.map((w) => w.week.id)).toEqual(['week-old']);
  });

  test('an Era that comes back after another starts a new run with its own key', () => {
    const groups = groupFeed([
      week('a', '2026-11-23', '2026-11-29', [uniDays], []),
      week('b', '2026-11-16', '2026-11-22', [newJob], []),
      week('c', '2026-11-09', '2026-11-15', [uniDays], []),
    ]);
    expect(groups.map((g) => g.era?.title)).toEqual(['Uni Days', 'New job', 'Uni Days']);
    expect(new Set(groups.map((g) => g.key)).size).toBe(3);
  });

  test('weeks in no Era share one group with no Era', () => {
    const groups = groupFeed([
      week('a', '2026-11-23', '2026-11-29', [], []),
      week('b', '2026-11-16', '2026-11-22', [], []),
    ]);
    expect(groups).toHaveLength(1);
    expect(groups[0]!.era).toBeNull();
    expect(groups[0]!.sections[0]!.weeks).toHaveLength(2);
  });

  test('a week repeated on the next page is shown once', () => {
    const repeated = sampleWeeks[1]!;
    const groups = groupFeed([...sampleWeeks, repeated]);
    const ids = groups.flatMap((g) => g.sections.flatMap((s) => s.weeks.map((w) => w.week.id)));
    expect(ids).toEqual(['week-this', 'week-last', 'week-trip', 'week-old']);
  });
});

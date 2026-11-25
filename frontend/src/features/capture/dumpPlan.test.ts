import { describe, expect, test } from 'vitest';
import { planDump, type DumpPhoto } from './dumpPlan';

// The photo dump's plan (D-34, F-19, task 4.13): one entry per day, at most 20 photos each, undated photos last.

const TODAY = '2026-11-24';
let n = 0;
const photos = (day: string | null, count: number): DumpPhoto[] =>
  Array.from({ length: count }, () => ({ key: `p${++n}`, day }));

describe('planDump', () => {
  test('photos from three days become three entries, oldest day first, in the order picked', () => {
    const picked = [
      ...photos('2026-10-24', 2),
      ...photos('2026-10-23', 3),
      ...photos('2026-10-25', 1),
      ...photos('2026-10-23', 1),
    ];
    const days = planDump(picked, TODAY);
    expect(days.map((d) => d.day)).toEqual(['2026-10-23', '2026-10-24', '2026-10-25']);
    expect(days.flatMap((d) => d.entries).map((e) => [e.key, e.photos.length])).toEqual([
      ['2026-10-23#0', 4],
      ['2026-10-24#0', 2],
      ['2026-10-25#0', 1],
    ]);
    // The late pick of the 23rd goes last in its day.
    expect(days[0]?.photos.at(-1)).toBe(picked.at(-1));
  });

  test('a 25-photo day becomes an entry of 20 and one of 5 (D-17)', () => {
    const [day] = planDump(photos('2026-10-23', 25), TODAY);
    expect(day?.entries.map((e) => e.photos.length)).toEqual([20, 5]);
    expect(day?.entries.map((e) => e.key)).toEqual(['2026-10-23#0', '2026-10-23#1']);
    expect(day?.entries.every((e) => e.entryDate === '2026-10-23' && !e.dateUnknown)).toBe(true);
  });

  test('undated photos come last, dated today and marked date unknown, apart from photos taken today', () => {
    const days = planDump(
      [...photos(null, 2), ...photos(TODAY, 1), ...photos('2026-10-23', 1)],
      TODAY,
    );
    expect(days.map((d) => d.day)).toEqual(['2026-10-23', TODAY, null]);
    expect(days.at(-1)?.entries).toEqual([
      expect.objectContaining({ key: 'unknown#0', entryDate: TODAY, dateUnknown: true }),
    ]);
    expect(days[1]?.entries[0]?.dateUnknown).toBe(false);
  });

  test('nothing picked is no entries', () => {
    expect(planDump([], TODAY)).toEqual([]);
  });
});

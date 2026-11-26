import { describe, expect, test } from 'vitest';
import { brunswick, eraOf, grampians, japan, sampleEras, uni } from '../../test/msw/eras';
import { eraRows, erasInside } from './eraTree';

describe('eraRows (task 4.9, D-22)', () => {
  test('puts each Era inside a bigger one right under it, one level deep', () => {
    expect(eraRows(sampleEras).map(({ era, nested }) => [era.title, nested])).toEqual([
      ['The Brunswick Years', false],
      ['Grampians Long Weekend', true],
      ['Japan, finally', true],
      ['Uni Days', false],
    ]);
  });

  test('two Eras with the same dates hold each other, so neither nests and both are listed', () => {
    const twin = eraOf('era-twin', 'Uni again', uni.startDate, uni.endDate);
    expect(eraRows([uni, twin]).map(({ nested }) => nested)).toEqual([false, false]);
  });

  test('an Era inside an Era that is itself inside another goes under the top one, once', () => {
    const weekend = eraOf('era-wk', 'Halls Gap', '2026-10-24', '2026-10-25');
    const rows = eraRows([weekend, grampians, brunswick]);
    expect(rows.map(({ era }) => era.id)).toEqual(['era-bru', 'era-wk', 'era-gra']);
  });

  test('erasInside lists only the Eras wholly inside this one', () => {
    expect(erasInside(brunswick, sampleEras).map((era) => era.id)).toEqual([
      grampians.id,
      japan.id,
    ]);
    expect(erasInside(uni, sampleEras)).toEqual([]);
  });
});

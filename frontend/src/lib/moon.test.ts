import { describe, expect, test } from 'vitest';
import { litPath, moonName, moonPhase } from './moon';

describe('the phase', () => {
  // Published moments (UTC) from the 2024 lunar calendar.
  test.each([
    ['2024-01-11T11:57:00Z', 'New moon'],
    ['2024-01-18T03:52:00Z', 'First quarter moon'],
    ['2024-01-25T17:54:00Z', 'Full moon'],
    ['2024-02-02T23:18:00Z', 'Last quarter moon'],
  ])('%s is a %s', (instant, name) => {
    expect(moonName(moonPhase(new Date(instant)))).toBe(name);
  });

  test('the phase stays between 0 and 1 before the reference moon too', () => {
    const phase = moonPhase(new Date('1990-06-01T00:00:00Z'));
    expect(phase).toBeGreaterThanOrEqual(0);
    expect(phase).toBeLessThan(1);
  });
});

describe('the lit part, as seen from Melbourne', () => {
  test('a new moon has nothing lit, and a full moon is the whole disc', () => {
    expect(litPath(0, 12)).toBeNull();
    expect(litPath(0.5, 12)).toBe('M 12 0 A 12 12 0 1 1 12 24 A 12 12 0 1 1 12 0 Z');
  });

  test('a waxing crescent is lit on the left, with the terminator curving the same way', () => {
    // Lit fraction 0.25 at phase 1/6, so the terminator ellipse is half as wide as the moon.
    expect(litPath(1 / 6, 12)).toBe('M 12 0 A 12 12 0 0 0 12 24 A 6 12 0 0 1 12 0 Z');
  });

  test('a waning gibbous moon is lit on the right, with the terminator bulging into the dark side', () => {
    expect(litPath(4 / 6, 12)).toBe('M 12 0 A 12 12 0 0 1 12 24 A 6 12 0 0 1 12 0 Z');
  });

  test('at a quarter the terminator is a straight line down the middle', () => {
    expect(litPath(0.75, 12)).toBe('M 12 0 A 12 12 0 0 1 12 24 A 0 12 0 0 1 12 0 Z');
  });
});

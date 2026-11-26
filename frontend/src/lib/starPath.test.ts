import { describe, expect, test } from 'vitest';
import starSvg from '../assets/travelling-star.svg?raw';
import { frontAt, TRAIL, tAtFraction, travelledPath } from './starPath';

// The trail's numbers live twice: in the drawing (src/assets) and here, where its lit part is worked out. These
// tests keep them the same, and check that "half the way" is half the curve's length, not half its parameter.

describe('the travelling star’s trail (task 4.8, DESIGN.md §5)', () => {
  test('starPath.ts uses the same curve as the drawing', () => {
    const svg = new DOMParser().parseFromString(starSvg, 'image/svg+xml');
    const { start, control1, control2, end } = TRAIL;
    expect(svg.getElementById('trail')?.getAttribute('d')).toBe(
      `M${start.join(' ')}C${control1.join(' ')} ${control2.join(' ')} ${end.join(' ')}`,
    );
  });

  test('the drawing has every part the screens use, and no text', () => {
    const svg = new DOMParser().parseFromString(starSvg, 'image/svg+xml');
    for (const id of ['star', 'star-shape', 'trail', 'front', 'you', 'specks']) {
      expect(svg.getElementById(id), id).not.toBeNull();
    }
    expect(svg.querySelectorAll('text')).toHaveLength(0);
    expect(svg.getElementById('specks')?.children).toHaveLength(14);
    // Token colours only: everything is drawn in currentColor, never a colour of its own.
    expect(starSvg).not.toMatch(/#[0-9a-f]{3,6}\b|rgb\(/i);
  });

  test('the light front starts at the star and ends at you', () => {
    expect(frontAt(0)).toEqual({ x: 44, y: 30 });
    expect(frontAt(1)).toEqual({ x: 212, y: 74 });
    expect(travelledPath(0)).toBeNull();
    expect(travelledPath(1)).toBe('M44 30C104 30 150 74 212 74');
  });

  test('half the way is half the curve’s length, not half its parameter', () => {
    const t = tAtFraction(0.5);
    // Measure both halves with many small steps.
    const point = (u: number) => {
      const { start: p0, control1: p1, control2: p2, end: p3 } = TRAIL;
      const v = 1 - u;
      const mix = (i: 0 | 1) =>
        v ** 3 * p0[i] + 3 * v * v * u * p1[i] + 3 * v * u * u * p2[i] + u ** 3 * p3[i];
      return [mix(0), mix(1)] as const;
    };
    const length = (from: number, to: number) => {
      let sum = 0;
      let prev = point(from);
      for (let i = 1; i <= 2000; i++) {
        const next = point(from + ((to - from) * i) / 2000);
        sum += Math.hypot(next[0] - prev[0], next[1] - prev[1]);
        prev = next;
      }
      return sum;
    };
    expect(Math.abs(length(0, t) - length(t, 1))).toBeLessThan(0.1);
  });

  test('the lit part ends exactly at the light front', () => {
    for (const fraction of [0.01, 0.25, 0.5, 0.9]) {
      const { x, y } = frontAt(fraction);
      expect(travelledPath(fraction)?.endsWith(` ${x} ${y}`)).toBe(true);
    }
  });
});

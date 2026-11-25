// The travelling star's trail, worked out along its length (task 4.8, DESIGN.md §5). The drawing itself lives in
// src/assets/travelling-star.svg; these are the same four points as its `trail` path (starPath.test.ts reads the file
// and fails if they drift apart). Two things depend on the dates and can't be in the file: how much of the trail is
// lit, and where the light front is. Both are measured along the curve's length, not its parameter t: a cubic
// Bézier's t runs faster where the curve is straight, so half of t is not half of the way.

type Point = readonly [number, number];

export const TRAIL = {
  start: [44, 30],
  control1: [104, 30],
  control2: [150, 74],
  end: [212, 74],
} as const satisfies Record<string, Point>;

const lerp = (a: Point, b: Point, t: number): [number, number] => [
  a[0] + (b[0] - a[0]) * t,
  a[1] + (b[1] - a[1]) * t,
];

/** The curve's point at parameter t, by de Casteljau's construction (three rounds of lerp). */
function pointAt(t: number): [number, number] {
  const { start, control1, control2, end } = TRAIL;
  const a = lerp(start, control1, t);
  const b = lerp(control1, control2, t);
  const c = lerp(control2, end, t);
  return lerp(lerp(a, b, t), lerp(b, c, t), t);
}

// The curve's length so far at STEPS + 1 evenly spaced values of t. 256 straight pieces are within a hundredth of
// a unit of the real length on a curve this gentle, far below one pixel at the page's width.
const STEPS = 256;
const lengths: number[] = [0];
{
  let previous = pointAt(0);
  for (let i = 1; i <= STEPS; i++) {
    const next = pointAt(i / STEPS);
    lengths.push((lengths[i - 1] ?? 0) + Math.hypot(next[0] - previous[0], next[1] - previous[1]));
    previous = next;
  }
}

/** The parameter t at which `fraction` (0 to 1) of the curve's length has been travelled. */
export function tAtFraction(fraction: number): number {
  const total = lengths[STEPS] ?? 0;
  const want = Math.min(Math.max(fraction, 0), 1) * total;
  let i = 1;
  while (i < STEPS && (lengths[i] ?? 0) < want) i++;
  const before = lengths[i - 1] ?? 0;
  const after = lengths[i] ?? total;
  const within = after === before ? 0 : (want - before) / (after - before);
  return (i - 1 + within) / STEPS;
}

const n = (value: number) => Number(value.toFixed(2));

/** Where the light front is once `fraction` of the way has been travelled. */
export function frontAt(fraction: number): { x: number; y: number } {
  const [x, y] = pointAt(tAtFraction(fraction));
  return { x: n(x), y: n(y) };
}

/**
 * The lit part of the trail as an SVG path: the same curve from its start to the light front, cut with de
 * Casteljau's construction, so it is itself one cubic curve that lies exactly on the trail. `null` before the light
 * has moved.
 */
export function travelledPath(fraction: number): string | null {
  if (fraction <= 0) return null;
  const t = tAtFraction(fraction);
  const { start, control1, control2, end } = TRAIL;
  const a = lerp(start, control1, t);
  const b = lerp(control1, control2, t);
  const c = lerp(control2, end, t);
  const ab = lerp(a, b, t);
  const tip = lerp(ab, lerp(b, c, t), t);
  return `M${start[0]} ${start[1]}C${n(a[0])} ${n(a[1])} ${n(ab[0])} ${n(ab[1])} ${n(tip[0])} ${n(tip[1])}`;
}

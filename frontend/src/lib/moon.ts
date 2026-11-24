// The moon in the date line (DESIGN.md §4 Shell): a 24 px moon in today's phase, drawn as seen from Melbourne,
// so it is lit on the left while waxing and on the right while waning. Pure geometry; components/Moon.tsx draws it.
//
// The phase is the moon's age in its 29.53-day cycle, counted from a known new moon. That is accurate to within
// about half a day, plenty for a 24 px picture; it is never used to decide anything.

/** A new moon: 6 January 2000 at 18:14 UTC. */
const REFERENCE_NEW_MOON_MS = Date.UTC(2000, 0, 6, 18, 14);
/** The mean synodic month, new moon to new moon. */
export const SYNODIC_DAYS = 29.530588853;
const DAY_MS = 86_400_000;

/** Where the moon is in its cycle: 0 is new, 0.5 full, just under 1 new again. */
export function moonPhase(now: Date): number {
  const days = (now.getTime() - REFERENCE_NEW_MOON_MS) / DAY_MS;
  const phase = (days / SYNODIC_DAYS) % 1;
  return phase < 0 ? phase + 1 : phase;
}

const NAMES = [
  'New moon',
  'Waxing crescent moon',
  'First quarter moon',
  'Waxing gibbous moon',
  'Full moon',
  'Waning gibbous moon',
  'Last quarter moon',
  'Waning crescent moon',
] as const;

/** The moon's accessible name, "Waning gibbous moon" (DESIGN.md §8): eight phases, each centred on its moment. */
export function moonName(phase: number): (typeof NAMES)[number] {
  return NAMES[Math.round(phase * 8) % 8] ?? 'New moon';
}

/**
 * The lit part of a moon of radius r centred on (r, r), as an SVG path, or null when nothing is lit.
 * The outline runs from the top to the bottom round the lit edge, then back up along the terminator, an ellipse
 * whose half-width is r·|cos(2π·phase)|: it bulges towards the lit edge for a crescent and away from it past
 * the quarters. A full moon is the whole circle.
 */
export function litPath(phase: number, r: number): string | null {
  const lit = (1 - Math.cos(2 * Math.PI * phase)) / 2; // the lit fraction of the disc's width
  if (lit < 0.01) return null;
  const top = `${r} 0`;
  const bottom = `${r} ${2 * r}`;
  if (lit > 0.99) return `M ${top} A ${r} ${r} 0 1 1 ${bottom} A ${r} ${r} 0 1 1 ${top} Z`;

  const waxing = phase < 0.5;
  const litOnRight = !waxing; // as seen from the southern hemisphere
  const crescent = lit < 0.5;
  const terminator = round(r * Math.abs(1 - 2 * lit));
  // In SVG, y grows downwards: top to bottom round the right is clockwise (sweep 1), round the left is sweep 0.
  const edgeSweep = litOnRight ? 1 : 0;
  // Back up from the bottom: a crescent's terminator curves round the lit side, a gibbous moon's round the dark.
  const terminatorSweep = crescent === litOnRight ? 0 : 1;
  return `M ${top} A ${r} ${r} 0 0 ${edgeSweep} ${bottom} A ${terminator} ${r} 0 0 ${terminatorSweep} ${top} Z`;
}

function round(n: number): number {
  return Math.round(n * 100) / 100;
}

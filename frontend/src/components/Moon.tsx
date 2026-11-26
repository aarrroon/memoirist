import { litPath, moonName, moonPhase } from '../lib/moon';

// The 24 px moon in the date line (DESIGN.md §4 Shell). Its shape changes with the phase, so unlike the Loot Drop's
// star (a fixed SVG file in src/assets, design.md §5) it is computed: lib/moon.ts gives the lit outline, and the two
// fills are the moon tokens, so dark mode gets its own moon. It is an image with a name, "Waning gibbous moon"
// (DESIGN.md §8).
export function Moon({ now }: { now: Date }) {
  const phase = moonPhase(now);
  const lit = litPath(phase, 12);
  return (
    <svg
      width="24"
      height="24"
      viewBox="0 0 24 24"
      role="img"
      aria-label={moonName(phase)}
      className="shrink-0"
    >
      <circle cx="12" cy="12" r="12" className="fill-moon-dark" />
      {lit && <path d={lit} className="fill-moon-lit" />}
    </svg>
  );
}

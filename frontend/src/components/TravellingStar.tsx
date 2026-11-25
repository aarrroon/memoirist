import starUrl from '../assets/travelling-star.svg?no-inline';
import { cn } from '../lib/cn';
import { frontAt, travelledPath } from '../lib/starPath';

// The travelling star (D-41, DESIGN.md §5, task 4.8). The drawing is the one SVG file in src/assets; each of its parts
// is placed here with <use>, which copies the part and lets it inherit `color` and `fill` from the <use>, so the
// token classes below colour it and it follows light and dark mode (design.md §5: token colours only).
//
// `?no-inline` keeps the file a real URL: Vite would otherwise turn a file this small into a data: URL, and Chrome
// refuses <use> on data: URLs. The file is fetched once and cached; the parts are referenced by their ids.
//
// Two things change with the dates and so can't be in the file: how much of the trail is lit and where the light
// front is. Both come from lib/starPath.ts, measured along the file's own curve.

const part = (id: string) => `${starUrl}#${id}`;

export function TravellingStar({
  fraction,
  opened = false,
  specks = false,
  label,
  className,
}: {
  /** Days travelled ÷ days of the whole wait, 0 to 1 (time.ts, sealProgress). */
  fraction: number;
  /** Opened: the whole trail lit and "You" filled in the accent, with no light front (DESIGN.md §4). */
  opened?: boolean;
  /** The specks of dust, on the Loot Drop page only. */
  specks?: boolean;
  /** A sentence for screen readers (DESIGN.md §8). Without one the drawing is decoration, and hidden. */
  label?: string;
  className?: string;
}) {
  const lit = travelledPath(opened ? 1 : fraction);
  const front = frontAt(fraction);
  return (
    <svg
      viewBox="0 0 240 96"
      className={cn('block h-auto', className)}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      focusable="false"
    >
      {specks && <use href={part('specks')} className="text-ink-2 opacity-50" />}
      <use href={part('trail')} className="text-control" />
      {lit && (
        <path
          d={lit}
          className="fill-none stroke-accent"
          strokeWidth={2}
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />
      )}
      {!opened && <use href={part('front')} x={front.x} y={front.y} className="text-accent" />}
      {/* The star twinkles (100% to 86% and back every 3.2 s); reduced motion stops it (index.css). */}
      <use href={part('star')} className="twinkle text-accent" />
      <use
        href={part('you')}
        className={opened ? 'fill-accent text-accent' : 'fill-page text-ink'}
      />
    </svg>
  );
}

/** The 16 to 24 px star mark: the star alone, in currentColor (DESIGN.md §5), for the Opened row, Seal and rows. */
export function StarMark({ size = 20, className }: { size?: 16 | 20 | 24; className?: string }) {
  return (
    <svg
      viewBox="10 10 28 28"
      width={size}
      height={size}
      aria-hidden
      focusable="false"
      className={cn('shrink-0', className)}
    >
      <use href={part('star-shape')} />
    </svg>
  );
}

import type { Entry } from '../../api/feed';
import { countdown, formatOpens } from '../../lib/time';

/**
 * A sealed Loot Drop in the feed (D-10, DESIGN.md §4 Loot Drops): one fixed size, 112 px tall, `--surface` with
 * `lg` corners, showing only the opening date and the countdown. The day line above it already names the memory
 * date, so the card leaves it out and no fact is repeated (D-41). The server has already removed everything else
 * (EntryMapper); this card reads nothing but `unlockDate`, so even a mistake there could not show more.
 *
 * Task 4.8 adds the travelling star in a 128 px column on the left and makes the card a link to the Loot Drop
 * page; until then it is text only, because the drawing must never be improvised (design.md §5).
 */
export function SealedCard({ entry, zone, now }: { entry: Entry; zone: string; now: Date }) {
  // A locked entry always has an unlock date; without one it would not be sealed, so there is nothing to count.
  const opens = entry.unlockDate;
  return (
    <div className="flex h-28 flex-col justify-center gap-1 rounded-lg bg-surface px-4">
      <p className="sr-only">A sealed Loot Drop.</p>
      {opens && (
        <>
          <p className="text-small text-ink-2">Opens {formatOpens(opens, zone)}</p>
          <p className="font-display text-title text-accent">{countdown(opens, zone, now)}</p>
        </>
      )}
    </div>
  );
}

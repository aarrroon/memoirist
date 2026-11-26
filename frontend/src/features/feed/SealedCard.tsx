import { Link } from 'react-router';
import type { Entry } from '../../api/feed';
import { TravellingStar } from '../../components/TravellingStar';
import { countdown, formatDay, formatOpens, sealProgress } from '../../lib/time';

/**
 * A sealed Loot Drop (D-10, DESIGN.md §4 Loot Drops, task 4.8): one fixed size, 112 px tall, `--surface` with `lg`
 * corners. A 128 px column holds the travelling star at the card's progress; beside it the opening date over the
 * countdown. Under a day heading (the feed) the memory date is already on screen, so the card leaves it out and no
 * fact is repeated (D-41); anywhere else it comes first. The whole card is one link to the Loot Drop page, named
 * with everything it shows.
 *
 * It reads only `id`, `entryDate` and `unlockDate`. The server has already removed everything else (EntryMapper),
 * and this card never looks, so even a mistake there could not show more (SealedCard.test.tsx holds one).
 */
export function SealedCard({
  entry,
  zone,
  now,
  showMemoryDate = false,
}: {
  entry: Pick<Entry, 'id' | 'entryDate' | 'unlockDate'>;
  zone: string;
  now: Date;
  /** False under a day heading (the feed); true anywhere else (D-10). */
  showMemoryDate?: boolean;
}) {
  // A locked entry always has an unlock date; without one it would not be sealed, so there is nothing to count.
  const opens = entry.unlockDate;
  if (!opens) return null;
  const memory = formatDay(entry.entryDate);
  const opensOn = `Opens ${formatOpens(opens, zone)}`;
  const left = countdown(opens, zone, now);
  const { fraction } = sealProgress(entry.entryDate, opens, zone, now);
  const label = [
    'Sealed Loot Drop',
    showMemoryDate ? `, ${memory}` : '',
    `. ${opensOn}, ${left}.`,
  ].join('');

  return (
    <Link
      to={`/entries/${entry.id}`}
      aria-label={label}
      className="flex h-28 items-center gap-4 rounded-lg bg-surface px-4"
    >
      <TravellingStar fraction={fraction} className="w-32 shrink-0" />
      <span className="flex min-w-0 flex-col" aria-hidden>
        {showMemoryDate && <span className="text-small font-strong">{memory}</span>}
        <span className="text-small text-ink-2">{opensOn}</span>
        <span className="font-display text-title text-accent">{left}</span>
      </span>
    </Link>
  );
}

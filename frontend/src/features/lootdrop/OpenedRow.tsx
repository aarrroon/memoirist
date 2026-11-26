import { X } from 'lucide-react';
import { Link } from 'react-router';
import { useMarkSeen, type EntrySummary } from '../../api/lootDrops';
import { problemMessage } from '../../api/problem';
import { StarMark } from '../../components/TravellingStar';
import { displayTitle } from '../../lib/displayTitle';

/**
 * The *Opened* row (D-10, D-41, DESIGN.md §4 Feed, task 4.8): while a Loot Drop has just opened, it takes On this
 * day's place at the top of the feed. The star mark in the accent, "A Loot Drop opened" over its display title, and a
 * 44 px dismiss button. Opening the Loot Drop (its page marks it seen) or dismissing it takes it away; the next one,
 * if any, takes its place. The strip holds at most 20 (API_CONTRACT), so the others are counted, not listed.
 */
export function OpenedRow({ rows }: { rows: readonly EntrySummary[] }) {
  const markSeen = useMarkSeen();
  const first = rows[0];
  if (!first) return null;
  const name = displayTitle(first) ?? 'A Loot Drop';
  const more = rows.length - 1;

  return (
    <section aria-label="Opened Loot Drops" className="flex flex-col gap-2">
      <div className="flex min-h-16 items-center gap-4">
        <Link
          to={`/entries/${first.id}`}
          className="flex min-h-11 min-w-0 flex-1 items-center gap-4"
        >
          <span className="flex size-10 shrink-0 items-center justify-center text-accent">
            <StarMark size={24} />
          </span>
          <span className="flex min-w-0 flex-col">
            <span className="text-small font-strong">A Loot Drop opened</span>
            <span className="line-clamp-1 text-small text-ink-2">
              {name}
              {more > 0 && `, and ${more} more`}
            </span>
          </span>
        </Link>
        <button
          type="button"
          onClick={() => markSeen.mutate(first.id)}
          aria-label={`Dismiss: ${name}`}
          className="flex size-11 shrink-0 items-center justify-center rounded-full text-ink-2"
        >
          <X size={20} aria-hidden />
        </button>
      </div>
      {markSeen.isError && (
        <p role="alert" className="text-small text-ink-2">
          {problemMessage(markSeen.error)}
        </p>
      )}
    </section>
  );
}

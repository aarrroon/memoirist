import { BookOpen, ChevronRight, FileText, Image, Mic } from 'lucide-react';
import { useId, useState } from 'react';
import { Link } from 'react-router';
import type { EntrySummary } from '../../api/lootDrops';
import { displayTitle } from '../../lib/displayTitle';
import { yearsAgo, type IsoDate } from '../../lib/time';

/**
 * *On this day* (task 4.14, D-32, DESIGN.md §4 Feed): one 64 px row at the top of the feed, or below the first entry
 * while an Opened row has its place (D-41). It names the newest memory and counts the rest; pressing it opens the list
 * in place and the chevron turns down. With nothing from this day it draws nothing at all, so an empty strip never
 * hints that something sealed or deleted is waiting (D-10).
 *
 * The tile shows what the first memory holds as an icon, not its photo: a summary carries no file URL (D-24), and a
 * 40 px tile of the original photo would be far over the size it is drawn at (Q29).
 */
export function OnThisDay({ rows, today }: { rows: readonly EntrySummary[]; today: IsoDate }) {
  const [open, setOpen] = useState(false);
  const listId = useId();
  const first = rows[0];
  if (!first) return null;
  const more = rows.length - 1;

  return (
    <section aria-label="On this day" className="flex flex-col gap-2">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={listId}
        onClick={() => setOpen((now) => !now)}
        className="flex min-h-16 w-full items-center gap-4 text-left"
      >
        <KindTile summary={first} />
        <span className="flex min-w-0 flex-1 flex-col">
          <span className="text-small font-strong">On this day</span>
          <span className="line-clamp-1 text-small text-ink-2">
            {nameOf(first)}
            {more > 0 && `, and ${more} more`}
          </span>
        </span>
        <ChevronRight
          size={20}
          aria-hidden
          className={`shrink-0 text-ink-2 transition-transform motion-reduce:transition-none ${open ? 'rotate-90' : ''}`}
        />
      </button>
      {open && (
        <ul id={listId} className="flex flex-col">
          {rows.map((row) => (
            <li key={row.id}>
              <Link to={`/entries/${row.id}`} className="flex min-h-11 items-center gap-4 py-2">
                <KindTile summary={row} />
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="line-clamp-2 text-body">{nameOf(row)}</span>
                  <span className="text-small text-ink-2">{yearsAgo(row.entryDate, today)}</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function nameOf(summary: EntrySummary): string {
  return displayTitle(summary) ?? 'Entry';
}

/** A 40 px tile on `--surface` naming what the entry holds first (as Recently deleted's rows do, task 4.12). */
function KindTile({ summary }: { summary: EntrySummary }) {
  const Icon =
    summary.firstMediaKind === 'PHOTO'
      ? Image
      : summary.firstMediaKind === 'PAGE_SCAN'
        ? BookOpen
        : summary.firstMediaKind === 'VOICE_NOTE'
          ? Mic
          : FileText;
  return (
    <span
      aria-hidden
      className="flex size-10 shrink-0 items-center justify-center rounded-sm bg-surface text-ink-2"
    >
      <Icon size={20} />
    </span>
  );
}

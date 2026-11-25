import { ChevronRight } from 'lucide-react';
import { Fragment, type ReactNode } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { Link } from 'react-router';
import type { Media, WeekEra } from '../../api/feed';
import { formatWeekRange, isWithin, todayIn } from '../../lib/time';
import { FeedEntry } from './FeedEntry';
import type { PlacedWeek } from './grouping';

// A week in the feed (task 4.4): its Era and week line, sticky under the top bar (at the top of the column on a
// laptop), then its entries in day order (DESIGN.md §4 Feed, D-22).

/** "3 entries", counting only entries the user can read: a sealed Loot Drop never counts (D-10). */
function entryCount(placed: PlacedWeek): string | null {
  const open = placed.week.entries.filter((entry) => !entry.isLocked).length;
  if (open === 0) return null;
  return open === 1 ? '1 entry' : `${open} entries`;
}

/** The week's name on its line: its own title, "This week", or its dates. */
function weekName(placed: PlacedWeek, zone: string, now: Date): string {
  const { week } = placed;
  if (week.title) return week.title;
  if (isWithin(todayIn(zone, now), week.startDate, week.endDate)) return 'This week';
  return formatWeekRange(week.startDate, week.endDate);
}

export function WeekSection({
  placed,
  eagerPhotoId,
  cover = null,
  afterFirstEntry = null,
  zone,
  now,
}: {
  placed: PlacedWeek;
  eagerPhotoId: string | null;
  /** The home Era's cover, from GET /eras (task 4.14); null when it has none or it is hidden (D-36). */
  cover?: Media | null;
  /** Drawn after the first entry: On this day, while the Opened row has its place at the top (D-41). */
  afterFirstEntry?: ReactNode;
  zone: string;
  now: Date;
}) {
  const { week, home, parent, chips } = placed;
  const count = entryCount(placed);
  const name = weekName(placed, zone, now);

  return (
    <section aria-labelledby={`week-${week.id}`} className="flex flex-col">
      {/* The Era and week line: 48 px, `--page` with a hairline below, edge to edge on a phone. It is a row of two
          links, not a box (design.md §4). z-1 keeps the scrolling photos underneath it. */}
      <div className="sticky top-14 z-1 -mx-4 flex min-h-12 items-center justify-between gap-4 border-b border-line bg-page px-4 md:mx-0 md:px-0 lg:top-0">
        {home ? <EraLink era={home} parent={parent} cover={cover} /> : <span />}
        <h2 id={`week-${week.id}`} className="max-w-3/5 min-w-0 shrink-0">
          <Link
            to={`/weeks/${week.id}`}
            className="flex min-h-11 items-center justify-end gap-1 text-right text-small text-ink-2"
          >
            <span className="line-clamp-2">
              {name}
              {/* The count never breaks inside itself: "2 / entries" read badly on a phone. */}
              {count && (
                <>
                  {' · '}
                  <span className="whitespace-nowrap">{count}</span>
                </>
              )}
            </span>
            <ChevronRight size={16} aria-hidden className="shrink-0" />
          </Link>
        </h2>
      </div>

      <div className="flex flex-col gap-6 pt-6">
        {chips.length > 0 && (
          <div className="flex flex-wrap items-center gap-2">
            <span className="sr-only">Also in</span>
            {chips.map((era) => (
              // A 32 px chip inside a 44 px link, so the hit area is big enough on a phone (design.md §9).
              <Link key={era.id} to={`/eras/${era.id}`} className="flex min-h-11 items-center">
                <span className="flex h-8 items-center rounded-full border border-control px-3 text-small">
                  {era.title}
                </span>
              </Link>
            ))}
          </div>
        )}

        {week.entries.map((entry, index) => {
          const previous = week.entries[index - 1];
          return (
            <Fragment key={entry.id}>
              {/* 24 px, a full-width hairline, 24 px between entries (DESIGN.md §4 Feed). */}
              {index > 0 && <hr className="-mx-4 border-line md:mx-0" />}
              {/* One entry that can't be drawn shows a small error; the rest of the feed carries on (F-08). */}
              <ErrorBoundary
                fallback={
                  <p role="alert" className="text-small text-ink-2">
                    This entry couldn’t be shown.
                  </p>
                }
              >
                <FeedEntry
                  entry={entry}
                  showDay={previous?.entryDate !== entry.entryDate}
                  eagerPhotoId={eagerPhotoId}
                  zone={zone}
                  now={now}
                />
              </ErrorBoundary>
              {index === 0 && afterFirstEntry && (
                <>
                  <hr className="-mx-4 border-line md:mx-0" />
                  {afterFirstEntry}
                </>
              )}
            </Fragment>
          );
        })}
      </div>
    </section>
  );
}

/**
 * The left of the line: the Era's 32 px cover, its title page in small (D-36, task 4.14), or its initial on
 * `--raised` at the same size when it has none or the cover is hidden (design.md §10), and its name. It opens the
 * Era's page. A week nested inside a bigger Era (D-22) names that one under it.
 */
function EraLink({
  era,
  parent,
  cover,
}: {
  era: WeekEra;
  parent: WeekEra | null;
  cover: Media | null;
}) {
  return (
    <Link to={`/eras/${era.id}`} className="flex min-h-11 min-w-0 items-center gap-3">
      {cover ? (
        // Decoration beside the Era's name, so alt is empty. Lazy like every photo but the first (design.md §6):
        // it is the original photo while Q29 stands, so the audit reports it as oversize.
        <img
          src={cover.fileUrl}
          alt=""
          width={32}
          height={32}
          loading="lazy"
          decoding="async"
          className="size-8 shrink-0 rounded-sm object-cover"
        />
      ) : (
        <span
          aria-hidden
          className="flex size-8 shrink-0 items-center justify-center rounded-sm bg-raised text-small font-strong"
        >
          {era.title.charAt(0).toUpperCase()}
        </span>
      )}
      <span className="flex min-w-0 flex-col">
        <span
          className={
            parent ? 'line-clamp-1 text-small font-strong' : 'line-clamp-2 text-small font-strong'
          }
        >
          {era.title}
        </span>
        {parent && <span className="line-clamp-1 text-small text-ink-2">in {parent.title}</span>}
      </span>
    </Link>
  );
}

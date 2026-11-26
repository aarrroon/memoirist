import { ChevronRight, Image, Mic, Plus, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ApiProblem, problemMessage } from '../../api/problem';
import { useTags, type Tag } from '../../api/tags';
import { useTimeline, type TimelineRow } from '../../api/timeline';
import { useMe } from '../../api/users';
import { ListLoading, LoadError } from '../../components/LoadState';
import { Page } from '../../components/Page';
import { StarMark } from '../../components/TravellingStar';
import { Button } from '../../components/ui/button';
import { Chip } from '../../components/ui/chip';
import { displayTitle } from '../../lib/displayTitle';
import { dayIn, formatDay, formatShortDay, timelineDay } from '../../lib/time';
import { useNow } from '../../lib/useNow';
import { useUiStore } from '../../stores/ui';
import { useUnlockWatch } from '../lootdrop/useUnlockWatch';
import { groupByMonth } from './months';

// The Timeline, at /entries (task 4.11, D-25, F-16, DESIGN.md §6 `#timeline`): every entry, newest first, one row
// each, under sticky month headings, with tag chips above that filter it (F-17). The filter lives in the address
// (?tag=<id>), so a tag on a feed card or a week page links straight to it (task 4.4), Back undoes a link, and a
// reload keeps it. Everything drawn comes from GET /entries: the server leaves sealed Loot Drops out of a filtered
// list and sends them redacted otherwise (D-10); the client never filters or counts rows itself.

/** The address parameter that holds the tag filter. Task 4.4's tag links already use it. */
const TAG_PARAM = 'tag';

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

export function TimelinePage() {
  const [params, setParams] = useSearchParams();
  const tagId = params.get(TAG_PARAM) || null;
  // A chip swaps the filter in place (replace), so Back leaves the Timeline instead of stepping through every chip
  // tapped; a tag link from another screen is a real navigation and Back returns there.
  const choose = useCallback(
    (next: string | null) =>
      setParams(
        (now) => {
          const copy = new URLSearchParams(now);
          if (next) copy.set(TAG_PARAM, next);
          else copy.delete(TAG_PARAM);
          return copy;
        },
        { replace: true },
      ),
    [setParams],
  );

  return (
    <Page title="Timeline">
      <TagFilter selected={tagId} onChoose={choose} />
      {/* Keyed by the filter, so the foot's observer and the scroll-to-chip start again for each list. */}
      <TimelineList key={tagId ?? 'all'} tagId={tagId} onClear={() => choose(null)} />
      {/* Task 4.12 (F-18): Recently deleted is reached from You and from here, a row with a chevron at the foot. */}
      <Link
        to="/recently-deleted"
        className="flex min-h-14 items-center gap-4 border-b border-line text-body"
      >
        <Trash2 size={20} aria-hidden className="shrink-0 text-ink-2" />
        <span className="flex-1">Recently deleted</span>
        <ChevronRight size={20} aria-hidden className="shrink-0 text-ink-2" />
      </Link>
    </Page>
  );
}

/**
 * The filter chips (DESIGN.md §6): *All entries*, then the user's tags by name, in one row that scrolls sideways on
 * a phone and runs to the screen's edge; the page itself never scrolls sideways (design.md §1). The row's height is
 * held while the tags load, so the list below doesn't jump. A failed tag list leaves *All entries* alone: the list
 * still works.
 */
function TagFilter({
  selected,
  onChoose,
}: {
  selected: string | null;
  onChoose: (tagId: string | null) => void;
}) {
  const tags = useTags();
  const selectedRef = useRef<HTMLButtonElement>(null);
  const tagCount = tags.data?.length ?? 0;
  // Arriving from a tag link, the chosen chip may sit past the edge of a phone: bring it into view once.
  useEffect(() => {
    selectedRef.current?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }, [tagCount]);

  return (
    <div
      role="group"
      aria-label="Filter by tag"
      className="-mx-4 flex min-h-11 gap-2 overflow-x-auto px-4 md:mx-0 md:px-0 lg:flex-wrap"
    >
      <Chip
        selected={selected === null}
        aria-pressed={selected === null}
        onClick={() => onChoose(null)}
      >
        All entries
      </Chip>
      {tags.data?.map((tag: Tag) => (
        <Chip
          key={tag.id}
          ref={tag.id === selected ? selectedRef : undefined}
          selected={tag.id === selected}
          aria-pressed={tag.id === selected}
          onClick={() => onChoose(tag.id)}
        >
          {tag.name}
        </Chip>
      ))}
    </div>
  );
}

function TimelineList({ tagId, onClear }: { tagId: string | null; onClear: () => void }) {
  const me = useMe();
  const timeline = useTimeline(tagId);
  const now = useNow();
  const openCapture = useUiStore((s) => s.openCapture);

  const rows = useMemo(
    () => timeline.data?.pages.flatMap((page) => page.items) ?? [],
    [timeline.data],
  );
  // One month split across two pages stays one section: the pages are joined before grouping.
  const months = useMemo(() => groupByMonth(rows), [rows]);
  const sealed = useMemo(() => rows.filter((row) => row.isLocked), [rows]);
  // A locked row turns into its entry's row when the Loot Drop opens (F-07), as the feed's sealed card does.
  useUnlockWatch(sealed, now);
  const { fetchNextPage } = timeline;
  const loadMore = useCallback(() => void fetchNextPage(), [fetchNextPage]);

  if (timeline.isError && !timeline.data) {
    // A tag that isn't the user's, or was deleted since the link was made, is 404 (API_CONTRACT §Entries).
    if (timeline.error instanceof ApiProblem && timeline.error.kind === 'not-found') {
      return (
        <Empty
          line="This tag isn’t in your journal any more."
          action={<Button onClick={onClear}>Clear the filter</Button>}
        />
      );
    }
    return <LoadError error={timeline.error} onRetry={() => void timeline.refetch()} />;
  }
  if (me.isError) return <LoadError error={me.error} onRetry={() => void me.refetch()} />;
  if (!timeline.data || !me.data) return <ListLoading label="Opening your timeline…" rows={6} />;

  if (rows.length === 0) {
    return tagId ? (
      // DESIGN.md §7: an invitation, with the action that fills it.
      <Empty
        line="No entries with this tag yet."
        action={<Button onClick={onClear}>Clear the filter</Button>}
      />
    ) : (
      <Empty
        line="Every entry you write is listed here, newest first."
        action={
          <Button variant="primary" onClick={() => openCapture('entry')}>
            <Plus size={20} aria-hidden />
            Write your first entry
          </Button>
        }
      />
    );
  }

  const zone = me.data.timezone;
  const total = timeline.data.pages[0]?.totalItems ?? rows.length;
  return (
    <div className="flex flex-col gap-4">
      <p role="status" className="sr-only">
        {plural(total, 'entry', 'entries')}
      </p>
      {months.map((month) => (
        <section key={month.key} aria-labelledby={`month-${month.key}`} className="flex flex-col">
          {/* Sticky under the phone's top bar, at the top of the column on a laptop (DESIGN.md §6). */}
          <h2
            id={`month-${month.key}`}
            className="sticky top-14 z-1 flex min-h-11 items-center bg-page text-small font-strong text-ink-2 lg:top-0"
          >
            {month.label}
          </h2>
          <ul className="flex flex-col divide-y divide-line border-b border-line">
            {month.rows.map((row) => (
              <li key={row.id}>
                {row.isLocked ? <SealedRow row={row} zone={zone} /> : <EntryRow row={row} />}
              </li>
            ))}
          </ul>
        </section>
      ))}
      <ListFoot
        hasNextPage={timeline.hasNextPage}
        isFetchingNextPage={timeline.isFetchingNextPage}
        failed={timeline.isFetchNextPageError}
        error={timeline.error}
        fetchNextPage={loadMore}
      />
    </div>
  );
}

function Empty({ line, action }: { line: string; action: ReactNode }) {
  return (
    <div className="flex flex-col items-start gap-4">
      <p className="text-body text-ink-2">{line}</p>
      {action}
    </div>
  );
}

/** The row's date column (DESIGN.md §4 Rows): "28 Oct" in `title` over "Wed" in `meta`. */
function RowDate({ day }: { day: string }) {
  const { short, weekday } = timelineDay(day);
  return (
    <span aria-hidden className="flex w-16 shrink-0 flex-col">
      <span className="font-display text-title">{short}</span>
      <span className="text-meta text-ink-2">{weekday}</span>
    </span>
  );
}

/**
 * A Timeline row (DESIGN.md §4 Rows, D-24): the date, then the display title (title, else the preview line, else
 * *Photo* / *Diary page* / *Voice note*) over a line of media counts and tags. The whole row opens the entry, so the
 * tags are text here, not links (a link can't hold another); the chips above filter by them.
 */
function EntryRow({ row }: { row: TimelineRow }) {
  const title = displayTitle(row) ?? 'Entry';
  const photos = row.photoCount ?? 0;
  const voiceNotes = row.voiceNoteCount ?? 0;
  const tags = row.tags.map((tag) => tag.name).join(', ');
  const label = [
    title,
    formatDay(row.entryDate, { year: true }),
    photos > 0 && plural(photos, 'photo', 'photos'),
    voiceNotes > 0 && plural(voiceNotes, 'voice note', 'voice notes'),
    tags && `tagged ${tags}`,
  ]
    .filter(Boolean)
    .join(', ');
  const hasSecondLine = photos > 0 || voiceNotes > 0 || tags !== '';
  return (
    <Link to={`/entries/${row.id}`} aria-label={label} className="flex min-h-14 gap-4 py-3">
      <RowDate day={row.entryDate} />
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="line-clamp-2 text-body">{title}</span>
        {hasSecondLine && (
          <span className="flex min-w-0 items-center gap-3 text-small text-ink-2">
            {photos > 0 && (
              <span className="flex shrink-0 items-center gap-1">
                <Image size={16} aria-hidden />
                {photos}
              </span>
            )}
            {voiceNotes > 0 && (
              <span className="flex shrink-0 items-center gap-1">
                <Mic size={16} aria-hidden />
                {voiceNotes}
              </span>
            )}
            {tags && <span className="truncate">{tags}</span>}
          </span>
        )}
      </span>
      <ChevronRight size={16} aria-hidden className="mt-1 shrink-0 text-ink-2" />
    </Link>
  );
}

/**
 * A sealed Loot Drop's row (DESIGN.md §4 Sealed row, F-16): the memory date, the star mark and *Loot Drop*, and the
 * day it opens. It reads only the row's ids and dates: never a title, counts, tags or a padlock (D-10, D-41).
 */
function SealedRow({
  row,
  zone,
}: {
  row: Pick<TimelineRow, 'id' | 'entryDate' | 'unlockDate'>;
  zone: string;
}) {
  const opens = row.unlockDate ? `Opens ${formatShortDay(dayIn(row.unlockDate, zone))}` : null;
  return (
    <Link
      to={`/entries/${row.id}`}
      aria-label={['Loot Drop', formatDay(row.entryDate, { year: true }), opens]
        .filter(Boolean)
        .join(', ')}
      className="flex min-h-14 gap-4 py-3"
    >
      <RowDate day={row.entryDate} />
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="flex items-center gap-2 text-body font-strong text-accent">
          <StarMark size={16} />
          Loot Drop
        </span>
        {opens && <span className="text-small text-ink-2">{opens}</span>}
      </span>
      <ChevronRight size={16} aria-hidden className="mt-1 shrink-0 text-ink-2" />
    </Link>
  );
}

/**
 * The foot of the list (F-16 step 2), as the feed's (task 4.4): the next page is asked for when the foot comes within
 * a screen of view; a keyboard, or a browser without IntersectionObserver, uses the button. A failed page keeps the
 * rows already shown and offers *Try again*.
 */
function ListFoot({
  hasNextPage,
  isFetchingNextPage,
  failed,
  error,
  fetchNextPage,
}: {
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  failed: boolean;
  error: Error | null;
  fetchNextPage: () => void;
}) {
  const foot = useRef<HTMLDivElement>(null);
  const wanted = hasNextPage && !isFetchingNextPage && !failed;

  useEffect(() => {
    const node = foot.current;
    if (!wanted || !node || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(
      (records) => {
        if (records.some((record) => record.isIntersecting)) fetchNextPage();
      },
      { rootMargin: '100% 0px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [wanted, fetchNextPage]);

  if (failed) {
    return (
      <div role="alert" className="flex flex-col items-start gap-4">
        <p className="text-body text-ink-2">{problemMessage(error)}</p>
        <Button onClick={fetchNextPage}>Try again</Button>
      </div>
    );
  }
  if (isFetchingNextPage) {
    return (
      <p role="status" className="flex min-h-11 items-center text-small text-ink-2">
        Loading earlier entries…
      </p>
    );
  }
  if (!hasNextPage) return null;
  return (
    <div ref={foot}>
      <Button variant="quiet" onClick={fetchNextPage}>
        Show earlier entries
      </Button>
    </div>
  );
}

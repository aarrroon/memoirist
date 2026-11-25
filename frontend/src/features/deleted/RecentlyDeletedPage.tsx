import { BookOpen, FileText, Image, Mic } from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type RefObject } from 'react';
import { Link } from 'react-router';
import {
  useEraseEntry,
  useRecentlyDeleted,
  useRestoreEntry,
  type DeletedRow,
} from '../../api/deleted';
import type { Entry } from '../../api/entries';
import { problemMessage } from '../../api/problem';
import { useMe } from '../../api/users';
import { ListLoading, LoadError } from '../../components/LoadState';
import { Page } from '../../components/Page';
import { StarMark } from '../../components/TravellingStar';
import { Button } from '../../components/ui/button';
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogTitle,
} from '../../components/ui/dialog';
import { cn } from '../../lib/cn';
import { displayTitle } from '../../lib/displayTitle';
import {
  dayIn,
  erasureDay,
  formatDayMonth,
  formatShortDay,
  formatWeekRange,
  weekOf,
} from '../../lib/time';

// Recently deleted, at /recently-deleted (task 4.12, D-30, F-18, DESIGN.md §6 `#deleted`): the entries the user
// deleted, the most recent first, each with its memory date and the day the daily sweep erases it, and *Restore* and
// *Erase now*. A sealed Loot Drop is a locked row the server has redacted (D-10): its memory date, the day it opens
// and *Restore, still sealed*. Reached from You and from the foot of the Timeline. Restoring needs no question, as it
// undoes nothing; erasing asks first, because it can't be undone.

/** What the screen says after a restore or an erase, where the row was. */
type Notice =
  { kind: 'restored'; entry: Entry } | { kind: 'erased' } | { kind: 'failed'; message: string };

export function RecentlyDeletedPage() {
  const me = useMe();
  const deleted = useRecentlyDeleted();
  const restore = useRestoreEntry();
  const erase = useEraseEntry();
  const [notice, setNotice] = useState<Notice | null>(null);
  // The row being asked about, and its Erase now button, which gets focus back if the user keeps the entry.
  const [erasing, setErasing] = useState<DeletedRow | null>(null);
  const eraseButton = useRef<HTMLButtonElement | null>(null);
  // Restoring or erasing takes the row, and the button that was pressed, off the page; focus goes to the notice
  // that says what happened instead of falling back to the top of the document (DESIGN.md §8).
  const noticeRef = useRef<HTMLParagraphElement>(null);
  // Once the notice is drawn (after the list has been read again without the row).
  useEffect(() => {
    if (notice) noticeRef.current?.focus();
  }, [notice]);

  const rows = useMemo(
    () => deleted.data?.pages.flatMap((page) => page.items) ?? [],
    [deleted.data],
  );

  const onRestore = (row: DeletedRow) =>
    restore.mutate(row.id, {
      onSuccess: (entry) => {
        setNotice({ kind: 'restored', entry });
      },
      onError: (error) => {
        setNotice({ kind: 'failed', message: problemMessage(error) });
      },
    });

  const onErase = (row: DeletedRow) =>
    erase.mutate(row.id, {
      onSuccess: () => {
        setErasing(null);
        setNotice({ kind: 'erased' });
      },
      onError: (error) => {
        setErasing(null);
        setNotice({ kind: 'failed', message: problemMessage(error) });
      },
    });

  let body;
  if (deleted.isError && !deleted.data) {
    body = <LoadError error={deleted.error} onRetry={() => void deleted.refetch()} />;
  } else if (me.isError) {
    body = <LoadError error={me.error} onRetry={() => void me.refetch()} />;
  } else if (!deleted.data || !me.data) {
    body = <ListLoading label="Opening Recently deleted…" rows={3} />;
  } else if (rows.length === 0) {
    body = <p className="text-body text-ink-2">Nothing is waiting here.</p>;
  } else {
    const zone = me.data.timezone;
    body = (
      <div className="flex flex-col gap-4">
        <ul className="flex flex-col divide-y divide-line border-y border-line">
          {rows.map((row) => (
            <li key={row.id}>
              <DeletedEntry
                row={row}
                zone={zone}
                busy={restore.isPending || erase.isPending}
                onRestore={() => onRestore(row)}
                onErase={(button) => {
                  eraseButton.current = button;
                  setErasing(row);
                }}
              />
            </li>
          ))}
        </ul>
        {deleted.hasNextPage && (
          <Button
            variant="quiet"
            className="self-start"
            disabled={deleted.isFetchingNextPage}
            onClick={() => void deleted.fetchNextPage()}
          >
            Show more
          </Button>
        )}
      </div>
    );
  }

  return (
    <Page title="Recently deleted">
      <p className="text-body text-ink-2">
        Entries wait here for 30 days, then they are erased with their photos.
      </p>
      {/* One live region for both outcomes, so a screen reader hears what happened; empty until something does. */}
      <div role="status" className="empty:hidden">
        {notice && <NoticeLine notice={notice} noticeRef={noticeRef} />}
      </div>
      {body}

      <AlertDialog
        open={erasing !== null}
        onOpenChange={(open) => {
          if (!open) setErasing(null);
        }}
      >
        <AlertDialogContent
          onCloseAutoFocus={(event) => {
            // Kept: back to the row's Erase now. Erased: the effect above moves focus to the notice.
            event.preventDefault();
            if (eraseButton.current?.isConnected) eraseButton.current.focus();
          }}
        >
          <AlertDialogTitle className="font-display text-title">
            {erasing?.isLocked ? 'Erase this Loot Drop now?' : 'Erase this entry now?'}
          </AlertDialogTitle>
          <AlertDialogDescription className="text-body text-ink-2">
            {erasing?.isLocked
              ? 'It is erased unopened, at once. This can’t be undone.'
              : 'It is erased with its photos and voice notes, at once. This can’t be undone.'}
          </AlertDialogDescription>
          <div className="flex flex-wrap justify-end gap-2">
            <AlertDialogCancel asChild>
              <Button variant="secondary">Keep it</Button>
            </AlertDialogCancel>
            <Button
              className="border-danger text-danger"
              disabled={erase.isPending}
              onClick={() => erasing && onErase(erasing)}
            >
              Erase
            </Button>
          </div>
        </AlertDialogContent>
      </AlertDialog>
    </Page>
  );
}

function NoticeLine({
  notice,
  noticeRef,
}: {
  notice: Notice;
  noticeRef: RefObject<HTMLParagraphElement | null>;
}) {
  if (notice.kind === 'failed') {
    return (
      <p ref={noticeRef} tabIndex={-1} className="text-body text-danger outline-none">
        {notice.message}
      </p>
    );
  }
  if (notice.kind === 'erased') {
    return (
      <p ref={noticeRef} tabIndex={-1} className="text-body outline-none">
        Erased.
      </p>
    );
  }
  const { entry } = notice;
  const { start, end } = weekOf(entry.entryDate);
  return (
    <p
      ref={noticeRef}
      tabIndex={-1}
      className="flex flex-wrap items-center gap-x-4 text-body outline-none"
    >
      <span>
        {entry.isLocked ? 'Restored, still sealed, to' : 'Restored to'} the week of{' '}
        {formatWeekRange(start, end)}.
      </span>
      <Link
        to={`/weeks/${entry.weeklyLogId}`}
        className="flex min-h-11 items-center font-strong text-accent"
      >
        Go there
      </Link>
    </p>
  );
}

/**
 * One row (DESIGN.md §6 `#deleted`, round 3 mockup): a 48 px tile naming what the entry holds, the display title over
 * "13 Oct 2026. Erased on 27 Nov", then *Restore* (secondary) and *Erase now* (quiet). The tile is an icon, not a
 * photo: a row carries no file URL (API_CONTRACT §Entries, The row). A sealed row reads only ids and dates (D-10).
 */
function DeletedEntry({
  row,
  zone,
  busy,
  onRestore,
  onErase,
}: {
  row: DeletedRow;
  zone: string;
  busy: boolean;
  onRestore: () => void;
  onErase: (button: HTMLButtonElement) => void;
}) {
  const erased = row.deletedAt ? formatDayMonth(erasureDay(row.deletedAt, zone)) : null;
  const written = formatShortDay(row.entryDate);
  const name = row.isLocked ? `Loot Drop, ${written}` : (displayTitle(row) ?? 'Entry');
  const opens = row.unlockDate ? formatShortDay(dayIn(row.unlockDate, zone)) : null;
  // Each date is kept on one line ("27 Dec", never "27 / Dec"); the sentence may wrap between them.
  const day = (text: string) => <span className="whitespace-nowrap">{text}</span>;
  const line = row.isLocked ? (
    <>
      {opens && <>Opens {day(opens)}. </>}
      {erased && <>Erased unopened on {day(erased)}</>}
    </>
  ) : (
    <>
      {day(written)}.{erased && <> Erased on {day(erased)}</>}
    </>
  );
  const titleId = `deleted-${row.id}`;

  return (
    <article aria-labelledby={titleId} className="flex gap-4 py-4">
      <Tile row={row} />
      <div className="flex min-w-0 flex-1 flex-col gap-3">
        <div className="flex flex-col">
          <h2 id={titleId} className="line-clamp-2 text-body font-strong">
            {name}
          </h2>
          <p className="text-small text-ink-2">{line}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            disabled={busy}
            aria-label={`${row.isLocked ? 'Restore, still sealed,' : 'Restore'} ${name}`}
            onClick={onRestore}
          >
            {row.isLocked ? 'Restore, still sealed' : 'Restore'}
          </Button>
          <Button
            variant="quiet"
            disabled={busy}
            aria-label={`Erase now ${name}`}
            onClick={(event) => onErase(event.currentTarget)}
          >
            Erase now
          </Button>
        </div>
      </div>
    </article>
  );
}

function Tile({ row }: { row: DeletedRow }) {
  const icon = row.isLocked ? (
    <StarMark size={20} />
  ) : row.firstMediaKind === 'PHOTO' ? (
    <Image size={20} aria-hidden />
  ) : row.firstMediaKind === 'PAGE_SCAN' ? (
    <BookOpen size={20} aria-hidden />
  ) : row.firstMediaKind === 'VOICE_NOTE' ? (
    <Mic size={20} aria-hidden />
  ) : (
    <FileText size={20} aria-hidden />
  );
  return (
    <span
      aria-hidden
      className={cn(
        'flex size-12 shrink-0 items-center justify-center rounded-sm bg-surface',
        row.isLocked ? 'text-accent' : 'text-ink-2',
      )}
    >
      {icon}
    </span>
  );
}

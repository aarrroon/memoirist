import { useEffect, useMemo, useRef } from 'react';
import { Link, useParams } from 'react-router';
import type { Entry } from '../../api/entries';
import { useEntry, useMarkSeen } from '../../api/lootDrops';
import { ApiProblem, problemMessage } from '../../api/problem';
import { useMe } from '../../api/users';
import { Page } from '../../components/Page';
import { RichText } from '../../components/RichText';
import { Swipe } from '../../components/Swipe';
import { TravellingStar } from '../../components/TravellingStar';
import { Button } from '../../components/ui/button';
import { VoiceNote } from '../../components/VoiceNote';
import { isPicture } from '../../lib/media';
import { hasText } from '../../lib/tiptap';
import { formatDay, formatShortDay, openedLabel } from '../../lib/time';
import { useNow } from '../../lib/useNow';
import { LootDropPage } from './LootDropPage';
import { useUnlockWatch } from './useUnlockWatch';

// /entries/:entryId (task 4.8, DESIGN.md §6). While the entry is a sealed Loot Drop this is the Loot Drop page; once
// it has opened it is the opened Loot Drop (DESIGN.md §4), and opening it here marks it seen, which takes it out of
// the feed's Opened row (D-10). An entry that was never sealed is shown plainly with the same parts; the full entry
// view (photos shown whole, Edit) is not a Sprint 4 task yet. Reply to past you is task 4.16.
export function EntryPage() {
  const { entryId = '' } = useParams();
  const entry = useEntry(entryId);
  const me = useMe();
  const now = useNow();
  // A Loot Drop on screen opens itself when its moment comes (F-07).
  // Memoised, so the watch's own memo and timer re-run only when the entry changes, not on every render.
  const watched = useMemo(() => (entry.data?.isLocked ? [entry.data] : []), [entry.data]);
  useUnlockWatch(watched, now);

  if (entry.isError || me.isError) {
    const error = entry.error ?? me.error;
    const missing = error instanceof ApiProblem && error.kind === 'not-found';
    return (
      <Page title={missing ? 'Not here' : 'Can’t reach memoirist'}>
        <p className="text-body text-ink-2">{problemMessage(error)}</p>
        {missing ? (
          <Link to="/" className="flex min-h-11 items-center text-body font-strong text-accent">
            Go to your journal
          </Link>
        ) : (
          <Button
            className="self-start"
            onClick={() => {
              void entry.refetch();
              void me.refetch();
            }}
          >
            Try again
          </Button>
        )}
      </Page>
    );
  }
  if (!entry.data || !me.data) {
    return (
      <p role="status" className="text-body text-ink-2">
        Opening…
      </p>
    );
  }
  const zone = me.data.timezone;
  if (entry.data.isLocked) return <LootDropPage entry={entry.data} zone={zone} now={now} />;
  return <OpenEntry entry={entry.data} zone={zone} now={now} />;
}

/** An entry that can be read: an opened Loot Drop with its whole trail lit, or an ordinary entry. */
function OpenEntry({ entry, zone, now }: { entry: Entry; zone: string; now: Date }) {
  const opened = entry.unlockDate !== null;
  const markSeen = useMarkSeen();
  const marked = useRef<string | null>(null);
  const { mutate } = markSeen;
  useEffect(() => {
    // Opening it counts as seeing it (D-10): once per entry, and safe to repeat on the server anyway.
    if (!opened || marked.current === entry.id) return;
    marked.current = entry.id;
    mutate(entry.id);
  }, [opened, entry.id, mutate]);

  const photos = entry.media.filter(isPicture);
  const notes = entry.media.filter((media) => !isPicture(media));
  const title = entry.title ?? formatDay(entry.entryDate, { year: true });

  return (
    <Page title={title}>
      <div className="order-first flex flex-col gap-2">
        {opened && entry.unlockDate && (
          <>
            <TravellingStar
              fraction={1}
              opened
              specks
              label={`A star whose light set out on ${formatDay(entry.entryDate, { year: true })}. It has reached you.`}
              className="w-full"
            />
            <p className="flex justify-between gap-4 text-meta text-ink-2">
              <span>Written {formatShortDay(entry.entryDate)}</span>
              <span>{openedLabel(entry.unlockDate, zone, now)}</span>
            </p>
          </>
        )}
        {!opened && (
          <p className="text-small font-strong">{formatDay(entry.entryDate, { year: true })}</p>
        )}
      </div>
      {photos.length > 0 && <Swipe photos={photos} eagerPhotoId={photos[0]?.id ?? null} />}
      {hasText(entry.contentJson) && (
        <RichText document={entry.contentJson} className="text-read" />
      )}
      {notes.map((note, index) => (
        <VoiceNote
          key={note.id}
          src={note.fileUrl}
          name={notes.length > 1 ? `voice note ${index + 1} of ${notes.length}` : 'voice note'}
        />
      ))}
      {entry.tags.length > 0 && (
        <ul aria-label="Tags" className="flex flex-wrap gap-x-4">
          {entry.tags.map((tag) => (
            <li key={tag.id} className="text-small text-ink-2">
              <Link
                to={`/entries?tag=${encodeURIComponent(tag.id)}`}
                className="inline-flex min-h-11 min-w-11 items-center"
              >
                {tag.name}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Page>
  );
}

import { useEffect, useMemo, useRef, useState } from 'react';
import { ChevronRight, CornerUpLeft, PenLine } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router';
import type { Entry } from '../../api/entries';
import { useEntry, useMarkSeen, useReplies } from '../../api/lootDrops';
import { ApiProblem, problemMessage } from '../../api/problem';
import { useMe } from '../../api/users';
import { Page } from '../../components/Page';
import { RichText } from '../../components/RichText';
import { Swipe } from '../../components/Swipe';
import { TravellingStar } from '../../components/TravellingStar';
import { Button } from '../../components/ui/button';
import { VoiceNote } from '../../components/VoiceNote';
import { entryDisplayTitle } from '../../lib/displayTitle';
import { isPicture } from '../../lib/media';
import { hasText } from '../../lib/tiptap';
import { formatDay, formatShortDay, openedLabel } from '../../lib/time';
import { useNow } from '../../lib/useNow';
import { useUiStore } from '../../stores/ui';
import { DeleteEntryDialog } from '../deleted/DeleteEntryDialog';
import { SoundtrackPlayer } from '../music/SoundtrackPlayer';
import { LootDropPage } from './LootDropPage';
import { useUnlockWatch } from './useUnlockWatch';

// /entries/:entryId (task 4.8, DESIGN.md §6). While the entry is a sealed Loot Drop this is the Loot Drop page; once
// it has opened it is the opened Loot Drop (DESIGN.md §4), and opening it here marks it seen, which takes it out of
// the feed's Opened row (D-10). An entry that was never sealed is shown plainly with the same parts; the full entry
// view (photos shown whole, Edit) is not a Sprint 4 task yet. Task 4.16 (D-33): an opened Loot Drop offers *Reply to
// past you* as the page's one primary button and lists its replies; a reply says which Loot Drop it answers.
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
  if (entry.data.isLocked) {
    return <LootDropPage entry={entry.data} zone={zone} birthday={me.data.birthday} now={now} />;
  }
  return <OpenEntry entry={entry.data} zone={zone} now={now} />;
}

/** An entry that can be read: an opened Loot Drop with its whole trail lit, or an ordinary entry. */
function OpenEntry({ entry, zone, now }: { entry: Entry; zone: string; now: Date }) {
  const opened = entry.unlockDate !== null;
  const navigate = useNavigate();
  const openCapture = useUiStore((s) => s.openCapture);
  const [deleting, setDeleting] = useState(false);
  const deleteButton = useRef<HTMLButtonElement>(null);
  // Capture opens from state, not from a Trigger, so when the reply sheet closes (saved, discarded or Escape) focus
  // is handed back to Reply to past you by hand, as the Write menu gets it back (DESIGN.md §8, task 4.16).
  const capture = useUiStore((s) => s.capture);
  const replyButton = useRef<HTMLButtonElement>(null);
  const replying = useRef(false);
  useEffect(() => {
    if (capture !== null || !replying.current) return;
    replying.current = false;
    replyButton.current?.focus();
  }, [capture]);
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
        {entry.replyToEntryId && <AnswersLink entryId={entry.replyToEntryId} />}
      </div>
      {photos.length > 0 && (
        <Swipe photos={photos} eagerPhotoId={photos[0]?.id ?? null} size="original" />
      )}
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
      {/* Task 4.15 (D-35): Spotify loads only when Play is tapped. */}
      {entry.soundtrack && <SoundtrackPlayer uri={entry.soundtrack} />}
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
      {opened && entry.replyIds.length > 0 && <Replies ids={entry.replyIds} />}
      {/* Task 4.12 (F-09): Delete asks first, then the entry waits in Recently deleted for 30 days (D-30). Quiet, as
          on the Loot Drop page; Edit joins it when a task builds the edit view. Task 4.16 (D-33, DESIGN.md §4 Opened
          Loot Drop): an opened Loot Drop leads with Reply to past you, the page's one primary button. */}
      <div className="flex flex-wrap items-center gap-4">
        {opened && (
          <Button
            variant="primary"
            ref={replyButton}
            onClick={() => {
              replying.current = true;
              openCapture('entry', { replyTo: entry.id });
            }}
          >
            <PenLine size={20} aria-hidden />
            Reply to past you
          </Button>
        )}
        <Button
          ref={deleteButton}
          variant="quiet"
          className={opened ? undefined : '-ml-3'}
          onClick={() => setDeleting(true)}
        >
          Delete
        </Button>
      </div>
      <DeleteEntryDialog
        entryId={entry.id}
        sealed={false}
        open={deleting}
        onOpenChange={setDeleting}
        returnFocus={deleteButton}
        onDeleted={() => void navigate('/', { replace: true })}
      />
    </Page>
  );
}

/**
 * On a reply: the Loot Drop it answers (task 4.16, D-33, F-07 "each shows a link to the other"). Its own line keeps
 * 44 px while the Loot Drop is read, so nothing moves. A Loot Drop sealed again since shows no title, as the server
 * sends none (D-10); one deleted or erased since is a 404, and the line goes: the reply stands on its own.
 */
function AnswersLink({ entryId }: { entryId: string }) {
  const target = useEntry(entryId);
  if (target.isError) return null;
  const answered = target.data;
  const name = answered
    ? answered.isLocked
      ? 'a Loot Drop, sealed again'
      : (entryDisplayTitle(answered) ?? `your Loot Drop of ${formatShortDay(answered.entryDate)}`)
    : null;
  return (
    <p className="flex min-h-11 items-center gap-2 text-small text-ink-2">
      <CornerUpLeft size={16} aria-hidden className="shrink-0" />
      {name && (
        <span>
          In reply to{' '}
          <Link to={`/entries/${entryId}`} className="font-strong text-accent">
            {name}
          </Link>
        </span>
      )}
    </p>
  );
}

/**
 * On an opened Loot Drop: the entries that answer it, oldest first (task 4.16, D-33). Each row is a link with a
 * chevron (design.md §8), its display title over "Written 26 Nov 2026". Rows still loading keep their 44 px.
 */
function Replies({ ids }: { ids: string[] }) {
  const { replies, pending } = useReplies(ids);
  if (replies.length === 0 && pending === 0) return null;
  return (
    <section aria-labelledby="replies-heading" className="flex flex-col gap-2">
      <h2 id="replies-heading" className="font-display text-title">
        {replies.length + pending === 1 ? 'Your reply' : 'Your replies'}
      </h2>
      <ul className="flex flex-col">
        {replies.map((reply) => (
          <li key={reply.id} className="border-b border-line first:border-t">
            <Link
              to={`/entries/${reply.id}`}
              className="flex min-h-11 items-center justify-between gap-4 py-3"
            >
              <span className="flex min-w-0 flex-col">
                <span className="truncate text-body font-strong">
                  {entryDisplayTitle(reply) ?? 'Reply'}
                </span>
                <span className="text-meta text-ink-2">
                  Written {formatShortDay(reply.entryDate)}
                </span>
              </span>
              <ChevronRight size={20} aria-hidden className="shrink-0 text-ink-2" />
            </Link>
          </li>
        ))}
        {Array.from({ length: pending }, (_, index) => (
          <li key={`pending-${index}`} aria-hidden className="h-11 border-b border-line" />
        ))}
      </ul>
    </section>
  );
}

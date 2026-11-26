import { ChevronRight, Image, Mic } from 'lucide-react';
import { Fragment, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router';
import type { Entry, Week, WeekEra } from '../../api/feed';
import { ApiProblem, problemMessage } from '../../api/problem';
import { useMe } from '../../api/users';
import { useEditWeek, useSetWeekClosed, useWeek } from '../../api/weeks';
import { Field, FormDialog } from '../../components/FormDialog';
import { ListLoading, LoadError } from '../../components/LoadState';
import { StarMark } from '../../components/TravellingStar';
import { Button } from '../../components/ui/button';
import { entryDisplayTitle } from '../../lib/displayTitle';
import { pictureUrl } from '../../lib/media';
import {
  barDay,
  dayIn,
  formatDay,
  formatEraDates,
  formatShortDay,
  isWithin,
  todayIn,
} from '../../lib/time';
import { SoundtrackField } from '../music/SoundtrackField';
import { SoundtrackPlayer } from '../music/SoundtrackPlayer';
import { mediaCounts, summarise, type Thumbnail } from './summary';

// A week's summary, at /weeks/:weekId (task 4.10, D-25, F-15, DESIGN.md §6 `#week`): its name, dates and Eras, a
// stats row, a few thumbnails, the tags used that week, then one bar per entry in day order. Edit week and Close
// week (F-12) sit at the foot. Everything comes from GET /weekly-logs/{id}; sealed Loot Drops arrive redacted
// (D-10) and are drawn as a star, the day and the opening date only, never counted.

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

export function WeekSummaryPage() {
  const { weekId = '' } = useParams();
  const week = useWeek(weekId);
  const me = useMe();

  if (week.isError || me.isError) {
    const error = week.error ?? me.error;
    return (
      <section className="flex flex-col gap-4">
        <title>Week · memoirist</title>
        <h1 className="font-display text-heading">Week</h1>
        {error instanceof ApiProblem && error.kind === 'not-found' ? (
          <>
            <p className="text-body text-ink-2">This week isn’t in your journal.</p>
            <Link to="/" className="flex min-h-11 items-center text-body font-strong text-accent">
              Go to your journal
            </Link>
          </>
        ) : (
          <LoadError
            error={error}
            onRetry={() => {
              void week.refetch();
              void me.refetch();
            }}
          />
        )}
      </section>
    );
  }
  if (!week.data || !me.data) return <ListLoading label="Opening this week…" rows={5} />;
  // Every entry of the week is in Recently deleted (or erased): the week has left the feed, so the user goes back
  // there (F-15 edge case, D-30). It comes back when one is restored.
  if (week.data.entries.length === 0) return <Navigate to="/" replace />;
  return <WeekView week={week.data} zone={me.data.timezone} />;
}

function WeekView({ week, zone }: { week: Week; zone: string }) {
  const [renaming, setRenaming] = useState(false);
  const setClosed = useSetWeekClosed(week.id);
  const summary = summarise(week);
  const thisWeek = isWithin(todayIn(zone), week.startDate, week.endDate);
  // The heading is the week's own name; without one it is "This week" or its dates. The dates are said once
  // (CLAUDE.md, design rules: no fact repeated on a screen), so the line under a heading of dates leaves them out.
  const name =
    week.title ?? (thisWeek ? 'This week' : formatEraDates(week.startDate, week.endDate));
  const datesInHeading = !week.title && !thisWeek;

  return (
    <section className="flex flex-col gap-8">
      <title>{`${name} · memoirist`}</title>
      <header className="flex flex-col gap-2">
        <h1 className="font-display text-heading">{name}</h1>
        <p className="text-small text-ink-2">
          {!datesInHeading && formatEraDates(week.startDate, week.endDate)}
          {week.eras.length > 0 && (
            <>
              {datesInHeading ? 'In ' : ', in '}
              <EraList eras={week.eras} />
            </>
          )}
          {week.isClosed && (datesInHeading && week.eras.length === 0 ? 'Closed' : ' · Closed')}
        </p>
        {/* The week's own soundtrack (D-35, task 4.15). It belongs to the week, not to an entry, so it shows even when
            every entry is sealed (API_CONTRACT §Weekly logs). */}
        {week.soundtrack && <SoundtrackPlayer uri={week.soundtrack} />}
      </header>

      {/* Phase 4 (F-22, D-28): the AI-written summary of a closed week goes here, at the top of the view (D-25).
          Phase 1 draws nothing in its place. */}

      {summary.entries > 0 && (
        <div className="flex flex-col gap-4">
          <dl className="flex divide-x divide-line">
            <Stat figure={summary.entries} label={summary.entries === 1 ? 'entry' : 'entries'} />
            {summary.photos > 0 && (
              <Stat figure={summary.photos} label={summary.photos === 1 ? 'photo' : 'photos'} />
            )}
            {summary.voiceNotes > 0 && (
              <Stat
                figure={summary.voiceNotes}
                label={summary.voiceNotes === 1 ? 'voice note' : 'voice notes'}
              />
            )}
          </dl>
          {summary.thumbnails.length > 0 && <Thumbnails thumbnails={summary.thumbnails} />}
          {summary.tags.length > 0 && (
            <ul aria-label="Tags this week" className="flex flex-wrap gap-x-2">
              {summary.tags.map((tag) => (
                <li key={tag.id}>
                  <Link
                    to={`/entries?tag=${encodeURIComponent(tag.id)}`}
                    aria-label={`${tag.name}, ${plural(tag.count, 'entry', 'entries')}`}
                    className="flex min-h-11 items-center"
                  >
                    <span className="flex h-8 items-center gap-1 rounded-full border border-control px-3 text-small">
                      {tag.name} <span className="text-ink-2">{tag.count}</span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      <section aria-labelledby="week-entries" className="flex flex-col gap-4">
        <h2 id="week-entries" className="font-display text-title">
          Entries
        </h2>
        <ul className="flex flex-col divide-y divide-line border-y border-line">
          {week.entries.map((entry) => (
            <li key={entry.id}>
              {entry.isLocked ? (
                <SealedBar entry={entry} zone={zone} />
              ) : (
                <EntryBar entry={entry} />
              )}
            </li>
          ))}
        </ul>
      </section>

      <div className="flex flex-col items-start gap-2">
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => setRenaming(true)}>Edit week</Button>
          <Button
            variant="quiet"
            disabled={setClosed.isPending}
            aria-busy={setClosed.isPending}
            onClick={() => setClosed.mutate(!week.isClosed)}
          >
            {week.isClosed ? 'Reopen week' : 'Close week'}
          </Button>
        </div>
        {setClosed.isError && (
          <p role="alert" className="text-small text-danger">
            {problemMessage(setClosed.error)}
          </p>
        )}
      </div>
      {renaming && <EditWeek week={week} onClose={() => setRenaming(false)} />}
    </section>
  );
}

/** "Grampians Long Weekend and The Brunswick Years", each a link to its Era (D-22: the week's Eras, pinned first). */
function EraList({ eras }: { eras: WeekEra[] }) {
  return eras.map((era, index) => (
    <Fragment key={era.id}>
      {index > 0 && (index === eras.length - 1 ? ' and ' : ', ')}
      <Link to={`/eras/${era.id}`} className="text-ink underline underline-offset-4">
        {era.title}
      </Link>
    </Fragment>
  ));
}

/** One figure of the stats row (DESIGN.md §4 Controls): the figure in `title` over its label in `meta`. */
function Stat({ figure, label }: { figure: number; label: string }) {
  return (
    <div className="flex flex-1 flex-col-reverse px-4 first:pl-0">
      <dt className="text-meta text-ink-2">{label}</dt>
      <dd className="font-display text-title">{new Intl.NumberFormat('en-AU').format(figure)}</dd>
    </div>
  );
}

/**
 * Up to ten pictures, five across in equal square tiles (DESIGN.md §6). They are a picture of the week, not a way in:
 * the bars below open the entries, so the tiles add no Tab stops. The first one is the screen's first photo, so it
 * loads at once; the rest are lazy (design.md §6). Each tile draws the photo's thumbnail (D-43, task 4.18).
 */
function Thumbnails({ thumbnails }: { thumbnails: Thumbnail[] }) {
  return (
    <ul aria-label="Photos this week" className="grid grid-cols-5 gap-1">
      {thumbnails.map(({ media, entry }, index) => (
        <li key={media.id}>
          <img
            src={pictureUrl(media, 'thumbnail')}
            alt={`${media.purpose === 'PAGE_SCAN' ? 'Diary page' : 'Photo'} from ${entryDisplayTitle(entry) ?? formatDay(entry.entryDate)}`}
            width={media.width ?? 96}
            height={media.height ?? 96}
            decoding="async"
            loading={index === 0 ? 'eager' : 'lazy'}
            fetchPriority={index === 0 ? 'high' : undefined}
            className="aspect-square w-full rounded-sm bg-surface object-cover"
          />
        </li>
      ))}
    </ul>
  );
}

/** A week bar (DESIGN.md §4 Rows): the day, the display title (D-24), the media counts, and a chevron. */
function EntryBar({ entry }: { entry: Entry }) {
  const title = entryDisplayTitle(entry) ?? 'Entry';
  const { pictures, voiceNotes } = mediaCounts(entry);
  const counts = [
    pictures > 0 && plural(pictures, 'photo', 'photos'),
    voiceNotes > 0 && plural(voiceNotes, 'voice note', 'voice notes'),
  ].filter(Boolean);
  return (
    <Link
      to={`/entries/${entry.id}`}
      aria-label={[title, formatDay(entry.entryDate), ...counts].join(', ')}
      className="flex min-h-14 items-center gap-4 py-2"
    >
      <span className="w-14 shrink-0 text-small text-ink-2">{barDay(entry.entryDate)}</span>
      <span className="line-clamp-2 min-w-0 flex-1 text-body">{title}</span>
      <span className="flex shrink-0 items-center gap-3 text-small text-ink-2">
        {pictures > 0 && (
          <span className="flex items-center gap-1">
            <Image size={16} aria-hidden />
            {pictures}
          </span>
        )}
        {voiceNotes > 0 && (
          <span className="flex items-center gap-1">
            <Mic size={16} aria-hidden />
            {voiceNotes}
          </span>
        )}
        <ChevronRight size={16} aria-hidden />
      </span>
    </Link>
  );
}

/**
 * A sealed Loot Drop's bar (DESIGN.md §4 Sealed row, F-15): the day, the star mark and "Loot Drop", and the day it
 * opens. It reads only the entry's date and unlock date; never a title, tags, media or a padlock (D-10, D-41).
 */
function SealedBar({
  entry,
  zone,
}: {
  entry: Pick<Entry, 'id' | 'entryDate' | 'unlockDate'>;
  zone: string;
}) {
  const opens = entry.unlockDate ? `Opens ${formatShortDay(dayIn(entry.unlockDate, zone))}` : null;
  return (
    <Link
      to={`/entries/${entry.id}`}
      aria-label={['Loot Drop', formatDay(entry.entryDate), opens].filter(Boolean).join(', ')}
      className="flex min-h-14 items-center gap-4 py-2"
    >
      <span className="w-14 shrink-0 text-small text-ink-2">{barDay(entry.entryDate)}</span>
      <span className="flex min-w-0 flex-1 items-center gap-2 text-body">
        <StarMark size={16} className="text-accent" />
        Loot Drop
      </span>
      <span className="flex shrink-0 items-center gap-3 text-small text-ink-2">
        {opens}
        <ChevronRight size={16} aria-hidden />
      </span>
    </Link>
  );
}

const TITLE_ERROR = 'A week’s name is at most 200 characters.';

/**
 * Edit week (F-12): its name and its soundtrack (D-35, task 4.15). A name left empty calls the week by its dates
 * again. Both go on every save, since the PUT is a full replace (D-26).
 */
function EditWeek({ week, onClose }: { week: Week; onClose: () => void }) {
  const [title, setTitle] = useState(week.title ?? '');
  const [soundtrack, setSoundtrack] = useState(week.soundtrack);
  const rename = useEditWeek(week);
  const fieldError =
    title.trim().length > 200 ||
    (rename.error instanceof ApiProblem && rename.error.fieldErrors?.title)
      ? TITLE_ERROR
      : undefined;
  return (
    <FormDialog
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
      title="Edit week"
      pending={rename.isPending}
      error={rename.isError && !fieldError ? problemMessage(rename.error) : null}
      onSubmit={() => {
        if (title.trim().length > 200) return;
        rename.mutate({ title, soundtrack }, { onSuccess: onClose });
      }}
    >
      <Field
        label="Name"
        hint="Leave it empty to call the week by its dates."
        value={title}
        onChange={setTitle}
        maxLength={200}
        autoComplete="off"
        error={fieldError}
      />
      <SoundtrackField value={soundtrack} onChange={setSoundtrack} />
    </FormDialog>
  );
}

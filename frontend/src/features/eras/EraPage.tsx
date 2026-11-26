import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { useEra, useEras, type EraDetail, type EraWeek } from '../../api/eras';
import { useMe } from '../../api/users';
import { ListLoading, LoadError } from '../../components/LoadState';
import { Button } from '../../components/ui/button';
import { pictureUrl } from '../../lib/media';
import { eraLength, formatEraDates, formatWeekRange, todayIn } from '../../lib/time';
import { SoundtrackPlayer } from '../music/SoundtrackPlayer';
import { EraForm } from './EraForm';
import { EraRow } from './ErasPage';
import { erasInside } from './eraTree';

/** Weeks listed before "Show N earlier weeks" (DESIGN.md §6 `#era`). */
export const FIRST_WEEKS = 20;

// One Era, at /eras/:eraId (DESIGN.md §4 Title band and §6 `#era`, task 4.9): the cover, the band with the name in
// `display`, its dates, description and stats, then its weeks as a contents list, then the Eras inside it. The weeks
// come from the server already filtered (D-22): only those with an entry to show, each counting only its entries
// inside the Era's dates, with sealed entries and Recently deleted left out (D-10, D-30). Nothing here counts entries.
export function EraPage() {
  const { eraId = '' } = useParams();
  const era = useEra(eraId);
  if (era.isPending) return <ListLoading label="Opening this Era…" />;
  if (era.isError) {
    return (
      <section className="flex flex-col gap-4">
        <title>Era · memoirist</title>
        <h1 className="font-display text-heading">Era</h1>
        <LoadError error={era.error} onRetry={() => void era.refetch()} />
      </section>
    );
  }
  return <EraView era={era.data} />;
}

function EraView({ era }: { era: EraDetail }) {
  const me = useMe();
  const eras = useEras();
  const [editing, setEditing] = useState(false);
  const [showAll, setShowAll] = useState(false);
  const today = me.data ? todayIn(me.data.timezone) : null;
  const end = era.endDate ?? today;
  const length = end ? eraLength(era.startDate, end) : null;
  const entries = era.weeklyLogs.reduce((sum, week) => sum + week.entryCount, 0);
  const shown = showAll ? era.weeklyLogs : era.weeklyLogs.slice(0, FIRST_WEEKS);
  const hidden = era.weeklyLogs.length - shown.length;
  const inside = eras.data ? erasInside(era, eras.data) : [];

  return (
    <section className="flex flex-col gap-8">
      <title>{`${era.title} · memoirist`}</title>
      {/* The cover is the photo's screen copy (D-43, task 4.18). DESIGN.md §4 puts it beside the band on a laptop, but
          at half the column (about 320 px) even the 1600 px screen copy is 5x the width it is drawn at, over
          design.md §6's 2x limit, so the cover stays above the band at every width (unattended 4.9 fix, kept in
          4.18; the user to confirm). */}
      <div className="-mx-4 flex flex-col md:mx-0">
        {era.cover && (
          <img
            src={pictureUrl(era.cover, 'screen')}
            alt=""
            width={era.cover.width ?? 1500}
            height={era.cover.height ?? 1000}
            decoding="async"
            fetchPriority="high"
            className="aspect-3/2 w-full object-cover md:rounded-t-lg"
          />
        )}
        <div
          className={`flex flex-col gap-4 bg-band px-4 py-8 text-on-band md:px-6 ${era.cover ? 'md:rounded-b-lg' : 'md:rounded-lg'}`}
        >
          <div className="flex items-start justify-between gap-4">
            <h1 className="font-display text-display">{era.title}</h1>
            <Button variant="quiet" className="text-on-band" onClick={() => setEditing(true)}>
              Edit
            </Button>
          </div>
          <p className="text-small text-on-band-2">
            {formatEraDates(era.startDate, era.endDate)}
            {length && ` · ${length}`}
            {era.pinnedInFeed && ' · Shown first in the feed'}
          </p>
          {era.description && <p className="text-body whitespace-pre-line">{era.description}</p>}
          {/* Its soundtrack (DESIGN.md §4 Title band, D-35, task 4.15), in the band's own ink. */}
          {era.soundtrack && <SoundtrackPlayer uri={era.soundtrack} onBand />}
          <div className="flex divide-x divide-on-band-2">
            <Stat
              figure={era.weeklyLogs.length}
              label={era.weeklyLogs.length === 1 ? 'week' : 'weeks'}
            />
            <Stat figure={entries} label={entries === 1 ? 'entry' : 'entries'} />
          </div>
        </div>
      </div>

      <section aria-labelledby="era-weeks" className="flex flex-col gap-4">
        <h2 id="era-weeks" className="font-display text-title">
          Its weeks
        </h2>
        {era.weeklyLogs.length === 0 ? (
          <p className="text-body text-ink-2">
            No entries in these dates yet. Anything you write with a memory date inside them files
            itself here.
          </p>
        ) : (
          <ul className="flex flex-col divide-y divide-line border-y border-line">
            {shown.map((week) => (
              <li key={week.id}>
                <WeekRow week={week} />
              </li>
            ))}
          </ul>
        )}
        {hidden > 0 && (
          <Button variant="quiet" className="self-start" onClick={() => setShowAll(true)}>
            {`Show ${hidden} earlier ${hidden === 1 ? 'week' : 'weeks'}`}
          </Button>
        )}
      </section>

      {inside.length > 0 && (
        <section aria-labelledby="era-inside" className="flex flex-col gap-4">
          <h2 id="era-inside" className="font-display text-title">
            Eras inside this one
          </h2>
          <ul className="flex flex-col divide-y divide-line border-y border-line">
            {inside.map((child) => (
              <li key={child.id}>
                <EraRow era={child} />
              </li>
            ))}
          </ul>
        </section>
      )}
      <EraForm era={era} open={editing} onOpenChange={setEditing} />
    </section>
  );
}

function Stat({ figure, label }: { figure: number; label: string }) {
  return (
    <p className="flex flex-col px-4 first:pl-0">
      <span className="font-display text-title">
        {new Intl.NumberFormat('en-AU').format(figure)}
      </span>
      <span className="text-meta text-on-band-2">{label}</span>
    </p>
  );
}

/** A contents row (DESIGN.md §4 Controls): the week's name, a dotted leader, then "3 entries". */
function WeekRow({ week }: { week: EraWeek }) {
  const name = week.title ?? formatWeekRange(week.startDate, week.endDate);
  const count = `${week.entryCount} ${week.entryCount === 1 ? 'entry' : 'entries'}`;
  return (
    <Link
      to={`/weeks/${week.id}`}
      aria-label={`${name}, ${count}`}
      className="flex min-h-14 items-center gap-2 text-body"
    >
      <span className="min-w-0">{name}</span>
      <span aria-hidden className="h-0 min-w-4 flex-1 border-b-2 border-dotted border-control" />
      <span className="shrink-0 text-small text-ink-2">{count}</span>
    </Link>
  );
}

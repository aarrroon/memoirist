import { Clock } from 'lucide-react';
import { lazy, Suspense, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import type { Entry } from '../../api/entries';
import { useSeal } from '../../api/lootDrops';
import { Page } from '../../components/Page';
import { TravellingStar } from '../../components/TravellingStar';
import { Button } from '../../components/ui/button';
import {
  addDays,
  countdown,
  dayIn,
  formatDay,
  formatShortDay,
  latestSealDay,
  openingParts,
  sealProgress,
  todayIn,
  unlockInstant,
  type IsoDate,
} from '../../lib/time';
import { DeleteEntryDialog } from '../deleted/DeleteEntryDialog';
import { sealProblem } from './sealProblem';

// The panel and its date picker load only when Extend is pressed (design.md §6).
const SealPanel = lazy(() => import('./SealPanel'));

/**
 * The Loot Drop page (DESIGN.md §4 Loot Drops, §6 `#loot`, F-07, task 4.8): the travelling star at the column's width
 * with the part already travelled lit, "Written 25 Oct 2026" under the star and "You" under the end; the countdown in
 * `display`; the opening moment once, in `title`; a line saying nobody can open or change it before then; *Extend the
 * seal* and *Delete*. Nothing else: the entry it holds is redacted by the server (D-10), and this page is only given
 * its dates. On a laptop it is one block, at most 640 px wide, centred (design.md §10).
 */
export function LootDropPage({
  entry,
  zone,
  now,
}: {
  entry: Pick<Entry, 'id' | 'entryDate' | 'unlockDate'>;
  zone: string;
  now: Date;
}) {
  const navigate = useNavigate();
  const [extending, setExtending] = useState(false);
  const [deleting, setDeleting] = useState(false);
  // Both open from state, not from a Radix Trigger, so focus is handed back to these buttons by hand (DESIGN.md §8).
  const extendButton = useRef<HTMLButtonElement>(null);
  const deleteButton = useRef<HTMLButtonElement>(null);
  const seal = useSeal(entry.id);

  const opens = entry.unlockDate;
  if (!opens) return null;
  const { travelled, total, fraction } = sealProgress(entry.entryDate, opens, zone, now);
  const moment = openingParts(opens, zone);
  // The seal can only move later (D-10): the first day offered is the day after the current opening day.
  const tomorrow = addDays(todayIn(zone, now), 1);
  const afterCurrent = addDays(dayIn(opens, zone), 1);
  const earliest = afterCurrent > tomorrow ? afterCurrent : tomorrow;
  const days = (n: number) => (n === 1 ? '1 day' : `${n} days`);

  // On success the page shows the new opening moment and countdown at once (useSeal puts the answer in the cache).
  const extend = (day: IsoDate) =>
    seal.mutate(unlockInstant(day, zone), { onSuccess: () => setExtending(false) });

  return (
    <Page title="Loot Drop" hideTitle>
      <div className="flex flex-col gap-8 lg:mx-auto lg:w-full lg:max-w-160">
        <figure className="flex flex-col gap-2">
          <TravellingStar
            fraction={fraction}
            specks
            label={`A star whose light set out on ${formatDay(entry.entryDate, { year: true })}. It has travelled ${days(travelled)} of ${total}.`}
            className="w-full"
          />
          <figcaption aria-hidden className="flex justify-between gap-4 text-meta text-ink-2">
            <span>Written {formatShortDay(entry.entryDate)}</span>
            <span>You</span>
          </figcaption>
        </figure>

        <div className="flex flex-col gap-4">
          <p className="font-display text-display text-accent">{countdown(opens, zone, now)}</p>
          <p className="font-display text-title">
            Opens {moment.day} at <span className="whitespace-nowrap">{moment.time}</span>
          </p>
          <p className="text-body text-ink-2">
            Its light is still on its way to you. Nobody can open or change this Loot Drop before
            then, you included.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-4">
          <Button ref={extendButton} onClick={() => setExtending(true)}>
            <Clock size={20} aria-hidden />
            Extend the seal
          </Button>
          <Button ref={deleteButton} variant="quiet" onClick={() => setDeleting(true)}>
            Delete
          </Button>
        </div>
      </div>

      {extending && (
        <Suspense fallback={null}>
          <SealPanel
            open
            onOpenChange={(open) => {
              setExtending(open);
              if (!open) seal.reset();
            }}
            title="Extend the seal"
            action="Extend the seal"
            returnFocus={extendButton}
            zone={zone}
            earliest={earliest}
            latest={latestSealDay(zone, now)}
            busy={seal.isPending}
            error={seal.isError ? sealProblem(seal.error, true) : null}
            primary
            onSeal={extend}
          />
        </Suspense>
      )}

      {/* Task 4.12: the shared delete question, with the copy task 4.8 wrote for a Loot Drop. */}
      <DeleteEntryDialog
        entryId={entry.id}
        sealed
        open={deleting}
        onOpenChange={setDeleting}
        returnFocus={deleteButton}
        onDeleted={() => void navigate('/', { replace: true })}
      />
    </Page>
  );
}

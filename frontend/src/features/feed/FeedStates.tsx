import { CloudOff, Images, Plus, RefreshCw } from 'lucide-react';
import { problemMessage, type ApiProblem } from '../../api/problem';
import { useRef, useState } from 'react';
import { StarMark, TravellingStar } from '../../components/TravellingStar';
import { Button } from '../../components/ui/button';
import { firstLetterSkipped, skipFirstLetter } from '../../lib/firstLetter';
import { addMonthsClamped, formatLongDay, todayIn } from '../../lib/time';
import { useDelayed } from '../../lib/useDelayed';
import { useUiStore } from '../../stores/ui';

// The feed's three states besides the feed itself (task 4.4, DESIGN.md §6 and §7, F-01, F-08, F-14).

/**
 * Loading: nothing for 300 ms, then the shape of the first week (its Era line, a day line and a photo) in
 * `--surface`, so a fast answer never flashes and a slow one shows where things will be. The status line is for
 * screen readers from the start.
 */
export function FeedLoading() {
  const showShapes = useDelayed(300);
  return (
    <div className="flex flex-col gap-6">
      <p role="status" className="sr-only">
        Opening your journal…
      </p>
      {showShapes && (
        <div aria-hidden className="flex flex-col gap-6" data-testid="feed-skeleton">
          <div className="-mx-4 flex h-12 items-center gap-3 border-b border-line px-4 md:mx-0 md:px-0">
            <div className="size-8 rounded-sm bg-surface" />
            <div className="h-4 w-32 rounded-sm bg-surface" />
          </div>
          <div className="h-4 w-40 rounded-sm bg-surface" />
          <div className="-mx-4 aspect-4/5 bg-surface md:mx-0 md:rounded-lg" />
        </div>
      )}
    </div>
  );
}

/**
 * First run (F-01, DESIGN.md §6 `#empty`): an invitation, not an apology, with the action that fills it. Both
 * buttons open capture through the UI store, like the Write menu; the sheet that reads it is task 4.6. The star
 * drawing above the heading is task 4.8's, as decoration, halfway along. The screen ends with the first letter
 * (task 4.16, D-33).
 */
export function FeedEmpty({ zone, now }: { zone: string; now: Date }) {
  const openCapture = useUiStore((s) => s.openCapture);
  const firstEntry = useRef<HTMLButtonElement>(null);
  return (
    <div className="flex flex-col gap-8 pt-8">
      <div className="flex flex-col items-start gap-4">
        <TravellingStar fraction={0.5} className="w-40" />
        <h2 className="font-display text-heading">Your journal starts here</h2>
        <p className="text-body text-ink-2">
          Write about today, or bring in the photos from a day you want to keep. Each week files
          itself.
        </p>
        <div className="flex flex-col items-start gap-4">
          <Button ref={firstEntry} variant="primary" onClick={() => openCapture('entry')}>
            <Plus size={20} aria-hidden />
            Write your first entry
          </Button>
          <Button variant="secondary" onClick={() => openCapture('dump')}>
            <Images size={20} aria-hidden />
            Photo dump
          </Button>
        </div>
      </div>
      <FirstLetter zone={zone} now={now} onSkip={() => firstEntry.current?.focus()} />
    </div>
  );
}

/**
 * The first letter (task 4.16, D-33, DESIGN.md §6 `#empty`): a `--surface` card at the end of the first-run screen
 * that invites a letter to future you, sealed for a month. *Write the letter* opens capture as the letter, whose
 * Seal it seals it on save. *Skip* hides it for good (F-01: it isn't offered again); the button pressed is gone, so
 * focus moves to *Write your first entry* (`onSkip`). The opening day is a month from today in the user's zone.
 */
function FirstLetter({ zone, now, onSkip }: { zone: string; now: Date; onSkip: () => void }) {
  const openCapture = useUiStore((s) => s.openCapture);
  const [skipped, setSkipped] = useState(firstLetterSkipped);
  if (skipped) return null;
  const opens = formatLongDay(addMonthsClamped(todayIn(zone, now), 1));
  return (
    <section
      aria-labelledby="first-letter-heading"
      className="flex flex-col items-start gap-4 rounded-lg bg-surface p-6"
    >
      <h3 id="first-letter-heading" className="font-display text-title">
        A letter to open in a month
      </h3>
      <p className="text-body text-ink-2">
        Write to the person you will be on {opens}. It stays sealed until then, even from you.
      </p>
      <div className="flex flex-wrap items-center gap-4">
        <Button variant="secondary" onClick={() => openCapture('letter')}>
          <StarMark size={20} />
          Write the letter
        </Button>
        <Button
          variant="quiet"
          onClick={() => {
            skipFirstLetter();
            setSkipped(true);
            onSkip();
          }}
        >
          Skip
        </Button>
      </div>
    </section>
  );
}

/**
 * A whole screen that couldn't load (DESIGN.md §6 `#error`, F-14): the *Can't reach memoirist* block, never
 * technical detail. When the server did answer, its kind picks the friendly sentence (problem.ts).
 */
export function FeedError({ error, onRetry }: { error: ApiProblem | Error; onRetry: () => void }) {
  const unreachable = 'kind' in error && error.kind === 'network';
  return (
    <div role="alert" className="flex flex-col items-start gap-4 pt-8">
      <CloudOff size={20} aria-hidden className="text-ink-2" />
      <h2 className="font-display text-title">Can’t reach memoirist</h2>
      <p className="text-body text-ink-2">
        {unreachable ? 'Check your connection, then try again.' : problemMessage(error)} Anything
        you were writing is still here.
      </p>
      <Button variant="secondary" onClick={onRetry}>
        <RefreshCw size={20} aria-hidden />
        Try again
      </Button>
    </div>
  );
}

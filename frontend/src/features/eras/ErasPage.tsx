import { ChevronRight, Plus } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { useEras, type Era } from '../../api/eras';
import { useMe } from '../../api/users';
import { ListLoading, LoadError } from '../../components/LoadState';
import { Page } from '../../components/Page';
import { Button } from '../../components/ui/button';
import { cn } from '../../lib/cn';
import { eraLength, formatEraDates, todayIn } from '../../lib/time';
import { EraForm } from './EraForm';
import { eraRows } from './eraTree';

// The Eras, at /eras (DESIGN.md §6 `#eras`, task 4.9): each Era a row with its cover (or its initial), its name in
// `title`, its dates and length; Eras inside another are indented 24 px under it (D-22). New Era opens the form.
export function ErasPage() {
  const eras = useEras();
  const [creating, setCreating] = useState(false);
  return (
    <Page
      title="Eras"
      action={
        <Button onClick={() => setCreating(true)}>
          <Plus size={20} aria-hidden />
          New Era
        </Button>
      }
    >
      {eras.isPending ? (
        <ListLoading label="Opening your Eras…" />
      ) : eras.isError ? (
        <LoadError error={eras.error} onRetry={() => void eras.refetch()} />
      ) : eras.data.length === 0 ? (
        <p className="text-body text-ink-2">
          An Era is a stretch of your life with a name: a house, a job, a trip. Give it its dates
          and your weeks file themselves under it.
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-line border-b border-line">
          {eraRows(eras.data).map(({ era, nested }) => (
            <li key={era.id}>
              <EraRow era={era} nested={nested} />
            </li>
          ))}
        </ul>
      )}
      <EraForm open={creating} onOpenChange={setCreating} />
    </Page>
  );
}

/** One Era as a link row: cover or initial, name, then "23–25 Oct 2026 · 3 days" (DESIGN.md §6). */
export function EraRow({ era, nested = false }: { era: Era; nested?: boolean }) {
  const me = useMe();
  const today = me.data ? todayIn(me.data.timezone) : null;
  const end = era.endDate ?? today;
  const length = end ? eraLength(era.startDate, end) : null;
  return (
    <Link
      to={`/eras/${era.id}`}
      className={cn('flex min-h-14 items-center gap-4 py-4', nested && 'pl-6')}
    >
      <EraThumb era={era} size={nested ? 'small' : 'large'} />
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="font-display text-title">{era.title}</span>
        <span className="text-small text-ink-2">
          {formatEraDates(era.startDate, era.endDate)}
          {length && ` · ${length}`}
        </span>
      </span>
      <ChevronRight size={20} aria-hidden className="shrink-0 text-ink-2" />
    </Link>
  );
}

/**
 * The Era's initial in a 64 px tile (48 inside another Era). Task 4.14 put the cover on the feed's 32 px Era line,
 * as DESIGN.md §4 draws it, and the audit fails it there as oversize until Q29 gives smaller copies. This list keeps
 * the initial until then: a 960 px or larger original shown at 64 px is 10 to 20 times the size it is drawn at, which
 * design.md §6 and the audit refuse. The Era page shows the cover large, where the original is the right size.
 */
function EraThumb({ era, size }: { era: Era; size: 'large' | 'small' }) {
  const box = size === 'large' ? 'size-16' : 'size-12';
  return (
    <span
      aria-hidden
      className={cn(
        box,
        'flex shrink-0 items-center justify-center rounded-sm bg-raised font-display text-title',
      )}
    >
      {era.title.trim().charAt(0).toUpperCase()}
    </span>
  );
}

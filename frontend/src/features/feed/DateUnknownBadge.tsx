import { CalendarDays } from 'lucide-react';
import { lazy, Suspense, useState } from 'react';
import { useChangeEntryDate, type Entry } from '../../api/entries';
import { problemMessage } from '../../api/problem';
import { Chip } from '../../components/ui/chip';
import { Popover, PopoverTrigger } from '../../components/ui/popover';

// The *date unknown, fix it* badge (D-34, F-19, task 4.13): an entry the photo dump filed under today because its
// photos had no date. Tapping it opens the memory-date month; a day picked there is sent as a full PUT, and the server
// clears the mark because the date changed, so the badge goes when the feed is read again. The month (react-day-picker)
// is loaded on the first tap, not with the feed (design.md §6).

const MemoryDayPicker = lazy(() => import('../../components/MemoryDayPicker'));

export function DateUnknownBadge({ entry, zone }: { entry: Entry; zone: string }) {
  const [open, setOpen] = useState(false);
  const change = useChangeEntryDate();

  return (
    <div className="flex flex-col items-start">
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Chip disabled={change.isPending}>
            <CalendarDays size={16} aria-hidden />
            Date unknown, fix it
          </Chip>
        </PopoverTrigger>
        {open && (
          <Suspense fallback={null}>
            <MemoryDayPicker
              day={null}
              zone={zone}
              label="Pick the day this was"
              onPick={(entryDate) => {
                setOpen(false);
                change.mutate({ entry, entryDate });
              }}
            />
          </Suspense>
        )}
      </Popover>
      {change.isError && (
        <p role="alert" className="text-small text-danger">
          {problemMessage(change.error)}
        </p>
      )}
    </div>
  );
}

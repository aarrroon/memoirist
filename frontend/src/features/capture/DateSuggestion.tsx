import { Button } from '../../components/ui/button';
import { chipDay, type IsoDate } from '../../lib/time';

// The photo's own date, offered as the memory date (D-34, task 4.13): picked photos that were all taken on one day
// other than the memory date bring this row up under the date chip. One tap files the entry on that day; Not now
// leaves the date alone. Neither is primary: Save stays the one primary button (design.md §8).

export function DateSuggestion({
  day,
  zone,
  onUse,
  onDismiss,
}: {
  day: IsoDate;
  zone: string;
  onUse: () => void;
  onDismiss: () => void;
}) {
  const label = chipDay(day, zone);
  return (
    <div
      role="group"
      aria-label="Photo date"
      className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-line pb-4"
    >
      <p className="text-small text-ink-2">Taken {label}</p>
      <div className="flex gap-2">
        <Button onClick={onUse}>Use this date</Button>
        <Button variant="quiet" onClick={onDismiss}>
          Not now
        </Button>
      </div>
    </div>
  );
}

import { DayPicker } from 'react-day-picker';
import { EARLIEST_DAY, fromPickerDate, todayIn, toPickerDate, type IsoDate } from '../lib/time';
import { PopoverContent } from './ui/popover';
import { pickerClasses } from './ui/calendar';

// A memory-date month in a popover (D-09): never after today, never before 1900, both in the user's zone. The same
// month as capture's date chip (DateChip.tsx), for the two places task 4.13 changes a date outside the form: the photo
// dump's undated photos (Change date) and an entry's *date unknown, fix it* badge (D-34). It imports
// react-day-picker, so it is only ever loaded with capture or with React.lazy (design.md §6).

export default function MemoryDayPicker({
  day,
  zone,
  label,
  onPick,
}: {
  /** The day shown as chosen, or null to show this month with nothing chosen. */
  day: IsoDate | null;
  zone: string;
  /** The popover's accessible name, such as "Pick the memory date". */
  label: string;
  onPick: (day: IsoDate) => void;
}) {
  const today = todayIn(zone);
  return (
    <PopoverContent aria-label={label} className="relative p-2">
      <DayPicker
        mode="single"
        required
        autoFocus
        weekStartsOn={1}
        captionLayout="dropdown"
        selected={day ? toPickerDate(day) : undefined}
        defaultMonth={toPickerDate(day ?? today)}
        startMonth={toPickerDate(EARLIEST_DAY)}
        endMonth={toPickerDate(today)}
        disabled={[{ after: toPickerDate(today) }, { before: toPickerDate(EARLIEST_DAY) }]}
        onSelect={(picked) => {
          if (picked) onPick(fromPickerDate(picked));
        }}
        classNames={pickerClasses}
      />
    </PopoverContent>
  );
}

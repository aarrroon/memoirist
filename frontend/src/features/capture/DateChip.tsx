import { CalendarDays } from 'lucide-react';
import { useState } from 'react';
import { DayPicker } from 'react-day-picker';
import { pickerClasses } from '../../components/ui/calendar';
import { Chip } from '../../components/ui/chip';
import { Popover, PopoverContent, PopoverTrigger } from '../../components/ui/popover';
import {
  chipDay,
  EARLIEST_DAY,
  fromPickerDate,
  toPickerDate,
  todayIn,
  type IsoDate,
} from '../../lib/time';

// The memory-date chip (DESIGN.md §4 Write, D-09): today by default, never after today, never before 1900, both in
// the user's zone. react-day-picker (D-13) draws the month; its own stylesheet isn't loaded, so every part below is
// given token classes instead (design.md, Tokens; the classes are in components/ui/calendar.ts).

export function DateChip({
  day,
  zone,
  onChange,
  error,
  triggerRef,
}: {
  /** Lets capture put focus back on the chip after the photo-date row it offered goes (task 4.13). */
  triggerRef?: React.Ref<HTMLButtonElement>;
  day: IsoDate;
  zone: string;
  onChange: (day: IsoDate) => void;
  error?: string;
}) {
  const [open, setOpen] = useState(false);
  const today = todayIn(zone);
  const label = chipDay(day, zone);

  return (
    <div className="flex flex-col">
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Chip ref={triggerRef} aria-label={`Memory date: ${label}. Change it`}>
            <CalendarDays size={16} aria-hidden />
            {label}
          </Chip>
        </PopoverTrigger>
        <PopoverContent aria-label="Pick the memory date" className="relative p-2">
          <DayPicker
            mode="single"
            required
            autoFocus
            weekStartsOn={1}
            captionLayout="dropdown"
            selected={toPickerDate(day)}
            defaultMonth={toPickerDate(day)}
            startMonth={toPickerDate(EARLIEST_DAY)}
            endMonth={toPickerDate(today)}
            disabled={[{ after: toPickerDate(today) }, { before: toPickerDate(EARLIEST_DAY) }]}
            onSelect={(picked) => {
              if (!picked) return;
              onChange(fromPickerDate(picked));
              setOpen(false);
            }}
            classNames={pickerClasses}
          />
        </PopoverContent>
      </Popover>
      {error && <p className="text-small text-danger">{error}</p>}
    </div>
  );
}

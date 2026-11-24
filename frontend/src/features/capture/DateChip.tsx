import { CalendarDays } from 'lucide-react';
import { useState } from 'react';
import { DayPicker } from 'react-day-picker';
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
// given token classes instead (design.md, Tokens).

const pickerClasses = {
  root: 'text-small text-ink',
  months: 'flex flex-col gap-2',
  month: 'flex flex-col gap-2',
  month_caption: 'flex min-h-11 items-center px-2',
  dropdowns: 'flex gap-2',
  dropdown_root: 'relative inline-flex items-center',
  dropdown: 'absolute inset-0 opacity-0',
  caption_label: 'inline-flex min-h-11 items-center gap-1 px-2 text-small font-strong',
  nav: 'absolute top-2 right-2 flex gap-1',
  button_previous: 'flex size-11 items-center justify-center rounded-full disabled:text-ink-2',
  button_next: 'flex size-11 items-center justify-center rounded-full disabled:text-ink-2',
  chevron: 'size-4 fill-current',
  month_grid: 'border-collapse',
  weekdays: '',
  weekday: 'size-11 text-meta font-normal text-ink-2',
  week: '',
  day: 'p-0 text-center',
  day_button:
    'size-11 rounded-full text-small focus-visible:-outline-offset-2 disabled:text-ink-2 disabled:line-through',
  selected: '[&>button]:bg-accent [&>button]:text-on-accent [&>button]:font-strong',
  today: '[&>button]:font-strong',
  disabled: '',
  outside: '[&>button]:text-ink-2',
  hidden: 'invisible',
};

export function DateChip({
  day,
  zone,
  onChange,
  error,
}: {
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
          <Chip aria-label={`Memory date: ${label}. Change it`}>
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

// react-day-picker's parts as token classes (D-13, design.md Tokens): its own stylesheet isn't loaded, so every part
// it draws is given a class here. First written for capture's memory-date chip (task 4.6); moved here in task 4.8 so
// the Loot Drop's opening-day picker draws the same month.
export const pickerClasses = {
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

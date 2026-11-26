import { CalendarDays } from 'lucide-react';
import { useId, useRef, useState, type ReactNode, type RefObject } from 'react';
import { DayPicker } from 'react-day-picker';
import { StarMark } from '../../components/TravellingStar';
import { Button } from '../../components/ui/button';
import { Chip } from '../../components/ui/chip';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogTitle,
} from '../../components/ui/dialog';
import { Popover, PopoverContent, PopoverTrigger } from '../../components/ui/popover';
import {
  chipDay,
  countdown,
  fromPickerDate,
  openingParts,
  quickDayLabel,
  quickDays,
  todayIn,
  toPickerDate,
  unlockInstant,
  type IsoDate,
  type QuickDayKey,
} from '../../lib/time';
import { pickerClasses } from '../../components/ui/calendar';

// Choosing when a Loot Drop opens (task 4.8, F-06, D-23, DESIGN.md §4 Write): pick a day, read back the moment it
// opens (09:00 on that day in the user's zone), press Seal it, then confirm in a question that repeats the date, the
// time and how far away it is, which catches 2072 typed for 2027 (F-06). Task 4.16 (D-33) puts the quick dates above
// the day picker as radio rows: In a month, In a year, On my birthday (only once a birthday is set), and Pick a date,
// which brings out the day chip. A quick date is still only a day: it opens at 09:00 and still asks first (D-10).
// Capture and the Loot Drop page's Extend both use it.

export interface SealFormProps {
  zone: string;
  /** The user's birthday from Settings (users.birthday), or null: no birthday, no "On my birthday" (F-06). */
  birthday: IsoDate | null;
  /** The first day that can be picked: tomorrow, or the day after the current opening day when extending (D-10). */
  earliest: IsoDate;
  /** The last: the day before the 100-year limit (time.ts, latestSealDay). */
  latest: IsoDate;
  /** Called with the chosen day once the user has confirmed it. */
  onSeal: (day: IsoDate) => void;
  busy: boolean;
  /** What went wrong last time, in the user's words. */
  error: string | null;
  /** Seal it is the screen's one primary button in the panel; secondary in capture's laptop box, beside Save. */
  primary: boolean;
  /** The confirming button's words: "Seal it", or "Extend the seal". */
  action?: string;
  /** Seal it can't be pressed yet: capture has nothing to save (D-23). */
  disabled?: boolean;
}

export function SealForm({
  zone,
  birthday,
  earliest,
  latest,
  onSeal,
  busy,
  error,
  primary,
  action = 'Seal it',
  disabled = false,
}: SealFormProps) {
  const [choice, setChoice] = useState<QuickDayKey | 'pick' | null>(null);
  const [picked, setPicked] = useState<IsoDate | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [picking, setPicking] = useState(false);
  const sealButton = useRef<HTMLButtonElement>(null);
  const name = useId();
  // Worked out from today in the user's zone on every render, so a panel left open past midnight moves with the day.
  const quick = quickDays(todayIn(zone), birthday, earliest, latest);
  const day =
    choice === 'pick' ? picked : (quick.find((option) => option.key === choice)?.day ?? null);
  const opens = day ? openingParts(unlockInstant(day, zone), zone) : null;

  return (
    <div className="flex flex-col gap-4">
      <fieldset className="flex flex-col">
        <legend className="sr-only">When it opens</legend>
        {quick.map((option) => (
          <RadioRow
            key={option.key}
            name={name}
            checked={choice === option.key}
            onChoose={() => setChoice(option.key)}
            label={option.label}
            detail={quickDayLabel(option.day)}
          />
        ))}
        <RadioRow
          name={name}
          checked={choice === 'pick'}
          onChoose={() => setChoice('pick')}
          label="Pick a date"
        />
      </fieldset>

      {choice === 'pick' && (
        <Popover open={picking} onOpenChange={setPicking}>
          <PopoverTrigger asChild>
            <Chip
              selected={picked !== null}
              className="self-start"
              aria-label={
                picked ? `Opening day: ${chipDay(picked, zone)}. Change it` : 'Choose the day'
              }
            >
              <CalendarDays size={16} aria-hidden />
              {picked ? chipDay(picked, zone) : 'Choose the day'}
            </Chip>
          </PopoverTrigger>
          <PopoverContent aria-label="Pick the day it opens" className="relative p-2">
            <DayPicker
              mode="single"
              autoFocus
              weekStartsOn={1}
              captionLayout="dropdown"
              selected={picked ? toPickerDate(picked) : undefined}
              defaultMonth={toPickerDate(picked ?? earliest)}
              startMonth={toPickerDate(earliest)}
              endMonth={toPickerDate(latest)}
              disabled={[{ before: toPickerDate(earliest) }, { after: toPickerDate(latest) }]}
              onSelect={(chosen) => {
                if (!chosen) return;
                setPicked(fromPickerDate(chosen));
                setPicking(false);
              }}
              classNames={pickerClasses}
            />
          </PopoverContent>
        </Popover>
      )}

      <div className="flex flex-col" aria-live="polite">
        <p className="text-small text-ink-2">Opens</p>
        {opens ? (
          <p className="text-body font-strong">
            {opens.day} at <span className="whitespace-nowrap">{opens.time}</span>
          </p>
        ) : (
          <p className="text-body text-ink-2">Pick the day it opens.</p>
        )}
      </div>

      {error && (
        <p role="alert" className="text-small text-danger">
          {error}
        </p>
      )}

      <Button
        ref={sealButton}
        variant={primary ? 'primary' : 'secondary'}
        disabled={day === null || busy || disabled}
        onClick={() => setConfirming(true)}
      >
        <StarMark size={20} />
        {busy ? 'Sealing…' : action}
      </Button>

      {day && (
        <SealConfirm
          open={confirming}
          onOpenChange={setConfirming}
          zone={zone}
          day={day}
          action={action}
          onConfirm={() => onSeal(day)}
          returnFocus={sealButton}
        />
      )}
    </div>
  );
}

/** The question before sealing (D-10, F-06): the opening moment in words and how far away it is. */
export function SealConfirm({
  open,
  onOpenChange,
  zone,
  day,
  action,
  onConfirm,
  returnFocus,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  zone: string;
  day: IsoDate;
  action: string;
  onConfirm: () => void;
  /** The question opens from state, not a Radix Trigger: on Go back, focus returns to this button (DESIGN.md §8). */
  returnFocus?: RefObject<HTMLElement | null>;
}) {
  const instant = unlockInstant(day, zone);
  const { day: words, time } = openingParts(instant, zone);
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent
        onCloseAutoFocus={(event) => {
          // After a confirmed seal the panel closes too, and the panel's own returnFocus takes over.
          if (!returnFocus?.current?.isConnected) return;
          event.preventDefault();
          returnFocus.current.focus();
        }}
      >
        <AlertDialogTitle className="font-display text-title">
          Seal this Loot Drop?
        </AlertDialogTitle>
        <AlertDialogDescription className="flex flex-col gap-2 text-body">
          <Line>
            Opens {words} at <span className="whitespace-nowrap">{time}</span>,{' '}
            {countdown(instant, zone)}.
          </Line>
          <Line muted>
            Nobody can open or change it before then, you included. You can make the wait longer
            later, never shorter, and you can still delete it.
          </Line>
        </AlertDialogDescription>
        <div className="flex flex-wrap justify-end gap-2">
          <AlertDialogCancel asChild>
            <Button variant="secondary">Go back</Button>
          </AlertDialogCancel>
          <AlertDialogAction asChild>
            <Button variant="primary" onClick={onConfirm}>
              <StarMark size={20} />
              {action}
            </Button>
          </AlertDialogAction>
        </div>
      </AlertDialogContent>
    </AlertDialog>
  );
}

function Line({ muted = false, children }: { muted?: boolean; children: ReactNode }) {
  return <span className={muted ? 'block text-ink-2' : 'block text-ink'}>{children}</span>;
}

/**
 * One radio row (DESIGN.md §4 Controls, Radios): the whole row is the label and at least 44 px tall; the radio is
 * a 20 px ring in `--control` with an `--accent` dot when chosen. The input itself is the native one, kept for
 * screen readers and the arrow keys, and drawn by the ring beside it; focus shows on the ring (DESIGN.md §8).
 */
function RadioRow({
  name,
  checked,
  onChoose,
  label,
  detail,
}: {
  name: string;
  checked: boolean;
  onChoose: () => void;
  label: string;
  detail?: string;
}) {
  return (
    <label className="flex min-h-11 cursor-pointer items-center gap-4 border-b border-line py-3 first-of-type:border-t">
      <input
        type="radio"
        name={name}
        checked={checked}
        onChange={onChoose}
        className="peer sr-only"
      />
      <span
        aria-hidden
        className={`flex size-5 shrink-0 items-center justify-center rounded-full border-2 peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-accent ${checked ? 'border-accent' : 'border-control'}`}
      >
        {checked && <span className="size-2.5 rounded-full bg-accent" />}
      </span>
      <span className="flex flex-col">
        <span className="text-body font-strong">{label}</span>
        {detail && <span className="text-small text-ink-2">{detail}</span>}
      </span>
    </label>
  );
}

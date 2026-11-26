import { Dialog as Primitive } from 'radix-ui';
import { useId, useState, type ComponentProps, type FormEvent, type ReactNode } from 'react';
import { cn } from '../lib/cn';
import { Button } from './ui/button';
import { Dialog, DialogContent, DialogTitle } from './ui/dialog';

// The small forms of task 4.9 (an Era, a template, one setting): a centred dialog over the scrim (design.md §10),
// at most 440 px wide, the same frame as the Extend dialog of task 4.8. It traps focus and closes with Escape
// (DESIGN.md §8). Its one primary button is Save; Cancel is quiet. A destructive action, when there is one, sits at
// the foot as a quiet button and asks again before it runs.
export function FormDialog({
  open,
  onOpenChange,
  title,
  onSubmit,
  pending,
  error,
  saveLabel = 'Save',
  extra,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  onSubmit: () => void;
  pending: boolean;
  /** A message for the whole form (problemMessage), shown above the buttons. */
  error?: string | null;
  saveLabel?: string;
  /** Shown at the foot, left of the buttons: a Delete, say. */
  extra?: ReactNode;
  children: ReactNode;
}) {
  // The dialogs open from state, not a Radix trigger, so Radix doesn't know where to put focus back. The element
  // that had focus when this dialog was made is the button that opened it (callers key the dialog by `open`, or
  // mount it open), and focus goes back there on close, as task 4.8's dialogs do (DESIGN.md §8).
  const [opener] = useState(() =>
    document.activeElement instanceof HTMLElement ? document.activeElement : null,
  );
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!pending) onSubmit();
  };
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        onCloseAutoFocus={(event) => {
          if (opener?.isConnected) {
            event.preventDefault();
            opener.focus();
          }
        }}
        aria-describedby={undefined}
        className="max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-110 overflow-y-auto p-6"
      >
        <form onSubmit={submit} noValidate className="flex flex-col gap-4">
          <DialogTitle className="font-display text-title">{title}</DialogTitle>
          {children}
          {error && (
            <p role="alert" className="text-small text-danger">
              {error}
            </p>
          )}
          <div className="flex flex-wrap items-center justify-end gap-2">
            {extra && <div className="mr-auto">{extra}</div>}
            <Primitive.Close asChild>
              <Button variant="quiet">Cancel</Button>
            </Primitive.Close>
            <Button type="submit" variant="primary" disabled={pending} aria-busy={pending}>
              {saveLabel}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

const fieldClass =
  'min-h-11 w-full rounded-sm border border-control bg-page px-3 text-body text-ink aria-invalid:border-danger';

/** A labelled field with its error under it in --danger (DESIGN.md §7). */
export function Field({
  label,
  error,
  hint,
  value,
  onChange,
  multiline = false,
  ...props
}: {
  label: string;
  error?: string;
  hint?: string;
  value: string;
  onChange: (value: string) => void;
  multiline?: boolean;
} & Pick<ComponentProps<'input'>, 'type' | 'maxLength' | 'min' | 'max' | 'autoComplete'>) {
  const id = useId();
  const described = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ');
  const shared = {
    id,
    value,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': described || undefined,
  };
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="text-small font-strong">
        {label}
      </label>
      {multiline ? (
        <textarea
          {...shared}
          onChange={(event) => onChange(event.target.value)}
          maxLength={props.maxLength}
          rows={3}
          className={cn(fieldClass, 'py-2')}
        />
      ) : (
        <input
          {...shared}
          {...props}
          onChange={(event) => onChange(event.target.value)}
          className={fieldClass}
        />
      )}
      {hint && (
        <p id={`${id}-hint`} className="text-small text-ink-2">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="text-small text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

/** A checkbox row: the whole row is the label, at least 44 px tall (DESIGN.md §4 Controls). */
export function CheckRow({
  label,
  hint,
  checked,
  onChange,
}: {
  label: string;
  hint?: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  const id = useId();
  return (
    <label htmlFor={id} className="flex min-h-11 items-start gap-3">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        className="mt-1 size-5 accent-accent"
      />
      <span className="flex flex-col">
        <span className="text-body">{label}</span>
        {hint && <span className="text-small text-ink-2">{hint}</span>}
      </span>
    </label>
  );
}

import { forwardRef, type ComponentProps, type ReactNode } from 'react';
import { cn } from '../../lib/cn';

// A chip (DESIGN.md §4 Controls, design.md §9; first built in task 4.6): 32 px tall with full corners, `small` text
// and a 1 px `--control` border, inside a 44 px tall button, so the target is 44 px while the chip looks 32. A
// selected chip is filled with the accent. forwardRef, because Radix's `asChild` triggers hand it their ref.

type ChipProps = ComponentProps<'button'> & { selected?: boolean; children: ReactNode };

export const Chip = forwardRef<HTMLButtonElement, ChipProps>(function Chip(
  { selected = false, className, children, type = 'button', ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={cn(
        'group inline-flex min-h-11 shrink-0 items-center rounded-full focus-visible:outline-offset-0',
        className,
      )}
      {...props}
    >
      <span
        className={cn(
          'inline-flex h-8 items-center gap-2 rounded-full border px-3 text-small',
          selected
            ? 'border-accent bg-accent text-on-accent'
            : 'border-control text-ink group-disabled:text-ink-2',
        )}
      >
        {children}
      </span>
    </button>
  );
});

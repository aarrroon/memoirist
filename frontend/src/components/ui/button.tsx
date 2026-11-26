import { cva, type VariantProps } from 'class-variance-authority';
import type { ComponentProps } from 'react';
import { cn } from '../../lib/cn';

// The three button styles (DESIGN.md §4 Controls, design.md §8), first needed by the feed's empty and error
// states (task 4.4). All are 44 px tall with full corners and `body` 600: primary is the accent (one on a
// screen), secondary has a 1 px `--control` border, quiet is text only. class-variance-authority (D-13) maps
// the variant name to its classes, so a screen asks for `variant="secondary"` instead of repeating them.
const buttonVariants = cva(
  'inline-flex min-h-11 items-center justify-center gap-2 rounded-full text-body font-strong',
  {
    variants: {
      variant: {
        // Disabled (capture's Save on an empty entry, task 4.6): no accent, so it doesn't read as the action to take.
        primary: 'bg-accent px-4 text-on-accent disabled:bg-surface disabled:text-ink-2',
        secondary: 'border border-control px-4 text-ink',
        quiet: 'px-3 text-ink',
      },
    },
    defaultVariants: { variant: 'secondary' },
  },
);

export function Button({
  className,
  variant,
  type = 'button',
  ...props
}: ComponentProps<'button'> & VariantProps<typeof buttonVariants>) {
  return <button type={type} className={cn(buttonVariants({ variant }), className)} {...props} />;
}

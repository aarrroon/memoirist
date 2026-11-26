import { DropdownMenu as Primitive } from 'radix-ui';
import type { ComponentProps } from 'react';
import { cn } from '../../lib/cn';

// shadcn/ui's dropdown menu (D-13), copied in and rewritten to the design tokens (design.md, Tokens): only the
// parts memoirist uses. Radix gives it the menu-button pattern: arrow keys move between items, Enter or Space
// chooses, Escape closes and puts focus back on the button, and typing a letter jumps to an item.

export const DropdownMenu = Primitive.Root;
export const DropdownMenuTrigger = Primitive.Trigger;

/** The floating panel: `--page` with the one shadow (DESIGN.md §3), `lg` corners, in a portal over everything. */
export function DropdownMenuContent({
  className,
  sideOffset = 8,
  ...props
}: ComponentProps<typeof Primitive.Content>) {
  return (
    <Primitive.Portal>
      <Primitive.Content
        sideOffset={sideOffset}
        className={cn(
          'z-30 flex min-w-48 flex-col gap-1 rounded-lg border border-line bg-page p-2 text-ink shadow-float',
          className,
        )}
        {...props}
      />
    </Primitive.Portal>
  );
}

/** A 44 px row with `sm` corners (DESIGN.md §3, menu rows); `--raised` under the pointer or the keyboard. */
export function DropdownMenuItem({ className, ...props }: ComponentProps<typeof Primitive.Item>) {
  return (
    <Primitive.Item
      className={cn(
        'flex min-h-11 cursor-default items-center gap-3 rounded-sm px-3 text-body select-none focus-visible:-outline-offset-2 data-highlighted:bg-raised',
        className,
      )}
      {...props}
    />
  );
}

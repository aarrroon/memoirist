import { Popover as Primitive } from 'radix-ui';
import type { ComponentProps } from 'react';
import { cn } from '../../lib/cn';

// shadcn/ui's popover (D-13), copied in and rewritten to the tokens: capture's date picker, tag picker and photo
// menu (task 4.6). Radix moves focus into it, closes it with Escape or a click outside, and puts focus back on the
// button that opened it.

export const Popover = Primitive.Root;
export const PopoverTrigger = Primitive.Trigger;
export const PopoverAnchor = Primitive.Anchor;

/** `--page` with the one shadow and `lg` corners (DESIGN.md §3), like the Write menu. */
export function PopoverContent({
  className,
  sideOffset = 8,
  align = 'start',
  ...props
}: ComponentProps<typeof Primitive.Content>) {
  return (
    <Primitive.Portal>
      <Primitive.Content
        sideOffset={sideOffset}
        align={align}
        collisionPadding={16}
        className={cn(
          'z-50 flex max-w-80 flex-col gap-2 rounded-lg border border-line bg-page p-2 text-ink shadow-float',
          className,
        )}
        {...props}
      />
    </Primitive.Portal>
  );
}

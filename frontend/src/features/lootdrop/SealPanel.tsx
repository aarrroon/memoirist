import { Dialog as Primitive } from 'radix-ui';
import type { RefObject } from 'react';
import { SealForm, type SealFormProps } from './SealForm';

// The seal panel (DESIGN.md §4 Write, task 4.8): on a phone it opens from the bottom over a scrim, `--surface` with
// `lg` top corners and the one shadow; on a laptop it is a dialog in the middle of the screen (design.md §10). It
// traps focus and closes with Escape (DESIGN.md §8). Capture's Seal button and the Loot Drop page's *Extend the seal*
// open it. It is a module of its own so the page can load it, and the date picker, only when it is wanted (design.md
// §6): `export default` for React.lazy.

export interface SealPanelProps extends SealFormProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** "Seal as Loot Drop", or "Extend the seal". */
  title: string;
  /**
   * The button that opened the panel. The panel has no Radix Trigger (it opens from state), so Radix would leave
   * focus on the page's body when it closes; it goes back to this button instead (DESIGN.md §8).
   */
  returnFocus?: RefObject<HTMLElement | null>;
}

export default function SealPanel({
  open,
  onOpenChange,
  title,
  returnFocus,
  ...form
}: SealPanelProps) {
  return (
    <Primitive.Root open={open} onOpenChange={onOpenChange}>
      <Primitive.Portal>
        <Primitive.Overlay className="fixed inset-0 z-50 bg-scrim" />
        <Primitive.Content
          onCloseAutoFocus={(event) => {
            if (!returnFocus?.current) return;
            event.preventDefault();
            returnFocus.current.focus();
          }}
          className="fixed inset-x-0 bottom-0 z-50 flex max-h-dvh flex-col gap-4 overflow-y-auto rounded-t-lg bg-surface px-4 pt-6 pb-8 text-ink shadow-float outline-none md:px-6 lg:inset-x-auto lg:top-1/2 lg:bottom-auto lg:left-1/2 lg:w-110 lg:-translate-x-1/2 lg:-translate-y-1/2 lg:rounded-lg lg:p-8"
        >
          <Primitive.Title className="font-display text-title">{title}</Primitive.Title>
          <Primitive.Description className="text-body text-ink-2">
            Nobody, including you, can open it until the day you pick. You can make the wait longer
            later, never shorter.
          </Primitive.Description>
          <SealForm {...form} />
        </Primitive.Content>
      </Primitive.Portal>
    </Primitive.Root>
  );
}

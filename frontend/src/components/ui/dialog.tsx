import { AlertDialog as AlertPrimitive, Dialog as Primitive } from 'radix-ui';
import type { ComponentProps } from 'react';
import { cn } from '../../lib/cn';

// shadcn/ui's dialog and alert dialog (D-13), copied in and rewritten to the tokens (task 4.6). A dialog is centred
// over a scrim that covers everything, the nav included (design.md §10); it traps focus and closes with Escape
// (DESIGN.md §8). The alert dialog is the one that must be answered: capture's keep-or-discard question (D-23).

export const Dialog = Primitive.Root;
export const DialogTitle = Primitive.Title;
export const DialogDescription = Primitive.Description;

const overlay = 'fixed inset-0 z-40 bg-scrim';
const panel =
  'fixed top-1/2 left-1/2 z-40 flex -translate-x-1/2 -translate-y-1/2 flex-col rounded-lg bg-page text-ink shadow-float outline-none';

/** The laptop capture dialog's frame; the caller sizes it. */
export function DialogContent({ className, ...props }: ComponentProps<typeof Primitive.Content>) {
  return (
    <Primitive.Portal>
      <Primitive.Overlay className={overlay} />
      <Primitive.Content className={cn(panel, className)} {...props} />
    </Primitive.Portal>
  );
}

export const AlertDialog = AlertPrimitive.Root;
export const AlertDialogTitle = AlertPrimitive.Title;
export const AlertDialogDescription = AlertPrimitive.Description;
export const AlertDialogCancel = AlertPrimitive.Cancel;
export const AlertDialogAction = AlertPrimitive.Action;

/** A small question over everything, capture included: 24 px inside, at most 400 px wide. */
export function AlertDialogContent({
  className,
  ...props
}: ComponentProps<typeof AlertPrimitive.Content>) {
  return (
    <AlertPrimitive.Portal>
      <AlertPrimitive.Overlay className={cn(overlay, 'z-50')} />
      <AlertPrimitive.Content
        className={cn(panel, 'z-50 w-[calc(100%-2rem)] max-w-100 gap-4 p-6', className)}
        {...props}
      />
    </AlertPrimitive.Portal>
  );
}

import { useEffect, useRef, type RefObject } from 'react';
import { useDeleteEntry } from '../../api/lootDrops';
import { problemMessage } from '../../api/problem';
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogTitle,
} from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';

// The delete question (task 4.12, F-09 step 1, D-30): one alert dialog for every Delete, from a feed entry's menu,
// an entry's page and a Loot Drop's page (task 4.8 wrote its copy). It says where the entry goes and for how long,
// so deleting never feels final: that is what Recently deleted is for.

export function DeleteEntryDialog({
  entryId,
  sealed,
  open,
  onOpenChange,
  returnFocus,
  onDeleted,
}: {
  entryId: string;
  /** A sealed Loot Drop stays sealed in Recently deleted (D-10), and the question says so. */
  sealed: boolean;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** The button that asked, which gets focus back when the dialog closes, if it is still on the page. */
  returnFocus: RefObject<HTMLElement | null>;
  /** After the server has the delete: the page leaves, or the feed's card has already gone. */
  onDeleted?: () => void;
}) {
  const remove = useDeleteEntry(entryId);
  // The delete refetches the feed before it settles, and the refetch can take the card, this dialog with it, off the
  // page while the dialog is still open, so onCloseAutoFocus never runs. Then focus goes to the screen on unmount.
  const openNow = useRef(open);
  useEffect(() => {
    openNow.current = open;
  }, [open]);
  useEffect(
    () => () => {
      if (!openNow.current) return;
      // Radix's focus scope, unmounting with us, puts focus back on the (gone) trigger in a setTimeout of its own,
      // which lands on <body>; two timer turns later the screen takes it, unless something else already has.
      setTimeout(() =>
        setTimeout(() => {
          if (document.activeElement === document.body) document.getElementById('main')?.focus();
        }),
      );
    },
    [],
  );

  return (
    <AlertDialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next);
        if (!next) remove.reset();
      }}
    >
      <AlertDialogContent
        onCloseAutoFocus={(event) => {
          event.preventDefault();
          // The asking button may have gone with its entry (the feed's card leaves once deleted): then focus goes
          // to the screen itself, never to the top of the document.
          if (returnFocus.current?.isConnected) returnFocus.current.focus();
          else document.getElementById('main')?.focus();
        }}
      >
        <AlertDialogTitle className="font-display text-title">
          {sealed ? 'Delete this Loot Drop?' : 'Delete this entry?'}
        </AlertDialogTitle>
        <AlertDialogDescription className="text-body text-ink-2">
          {sealed
            ? 'It moves to Recently deleted, still sealed. You can restore it there for 30 days.'
            : 'It moves to Recently deleted. You can restore it there for 30 days.'}
        </AlertDialogDescription>
        {remove.isError && (
          <p role="alert" className="text-small text-danger">
            {problemMessage(remove.error)}
          </p>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <AlertDialogCancel asChild>
            <Button variant="secondary">Keep it</Button>
          </AlertDialogCancel>
          <Button
            className="border-danger text-danger"
            disabled={remove.isPending}
            onClick={() =>
              remove.mutate(undefined, {
                onSuccess: () => {
                  onOpenChange(false);
                  onDeleted?.();
                },
              })
            }
          >
            Delete
          </Button>
        </div>
      </AlertDialogContent>
    </AlertDialog>
  );
}

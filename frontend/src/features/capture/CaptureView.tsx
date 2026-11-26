import { useId, useRef, useState } from 'react';
import { Drawer } from 'vaul';
import { useMe } from '../../api/users';
import { Button } from '../../components/ui/button';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogTitle,
  Dialog,
  DialogContent,
  DialogTitle,
} from '../../components/ui/dialog';
import { useUiStore, type CaptureKind } from '../../stores/ui';
import { CaptureForm } from './CaptureForm';
import { clearDraft } from './draft';
import { PhotoDump } from './PhotoDump';

// The frame around capture (DESIGN.md §4 Write, task 4.6): a full-screen Vaul sheet on a phone or tablet, and a dialog
// up to 960 px wide on a laptop (from `lg`, 64rem). Both trap focus and close with Escape (DESIGN.md §8). Closing
// with unsaved changes asks Keep editing or Discard (D-23, G-01). This module and everything it imports (TipTap,
// dnd-kit, Vaul, the date picker) is loaded only when capture first opens (design.md §6), by CaptureHost.

const LAPTOP = '(min-width: 64rem)';

export default function CaptureView({
  kind,
  replyTo = null,
}: {
  kind: CaptureKind;
  /** The opened Loot Drop a reply answers (task 4.16, D-33). */
  replyTo?: string | null;
}) {
  const close = useUiStore((s) => s.closeCapture);
  const me = useMe();
  // Chosen once, when capture opens. Following the width live would swap the sheet for the dialog when a tablet turns
  // (1024 px is `lg`), which remounts the form and drops the photos picked so far: they can't be kept in the draft.
  const [isLaptop] = useState(() => window.matchMedia(LAPTOP).matches);
  const [asking, setAsking] = useState(false);
  const titleId = useId();

  // The photo dump keeps no draft (task 4.13: picked files can't go into local storage), so closing it must leave a
  // New entry draft alone.
  const dump = kind === 'dump';
  const discardDraft = () => {
    if (!dump) clearDraft();
  };

  const requestClose = (dirty: boolean) => {
    if (dirty) setAsking(true);
    else {
      // Nothing worth keeping: an empty draft shouldn't come back next time.
      discardDraft();
      close();
    }
  };

  // Escape and the scrim reach the frame, not the form, so the form keeps the frame told whether it is dirty. A ref,
  // because the answer is only read when closing and needn't re-render anything.
  const dirty = useRef(false);

  const frameProps = {
    zone: me.data?.timezone ?? '',
    titleId,
    layout: isLaptop ? ('dialog' as const) : ('sheet' as const),
    onRequestClose: requestClose,
    onDone: close,
    onDirtyChange: (value: boolean) => {
      dirty.current = value;
    },
  };

  const form = !me.data ? null : dump ? (
    <PhotoDump {...frameProps} />
  ) : (
    <CaptureForm
      kind={kind}
      zone={me.data.timezone}
      birthday={me.data.birthday}
      replyTo={replyTo}
      titleId={titleId}
      layout={isLaptop ? 'dialog' : 'sheet'}
      onRequestClose={requestClose}
      onDone={close}
      onDirtyChange={(value) => {
        dirty.current = value;
      }}
    />
  );
  const body = form ?? (
    <p className="px-4 py-4 text-body text-ink-2" id={titleId}>
      Opening…
    </p>
  );

  const onOpenChange = (open: boolean) => {
    if (!open) requestClose(dirty.current);
  };

  const confirm = (
    <AlertDialog open={asking} onOpenChange={setAsking}>
      <AlertDialogContent>
        <AlertDialogTitle className="font-display text-title">
          {dump ? 'Leave these photos out?' : 'Discard this entry?'}
        </AlertDialogTitle>
        <AlertDialogDescription className="text-body text-ink-2">
          {dump
            ? 'Photos not filed yet won’t be added. Any already filed stay in your journal.'
            : 'What you’ve written since the last save will be lost.'}
        </AlertDialogDescription>
        <div className="flex flex-wrap justify-end gap-2">
          <AlertDialogCancel asChild>
            <Button variant="secondary">Keep editing</Button>
          </AlertDialogCancel>
          <AlertDialogAction asChild>
            <Button
              className="border-danger text-danger"
              onClick={() => {
                discardDraft();
                close();
              }}
            >
              Discard
            </Button>
          </AlertDialogAction>
        </div>
      </AlertDialogContent>
    </AlertDialog>
  );

  if (isLaptop) {
    return (
      <>
        <Dialog open onOpenChange={onOpenChange}>
          <DialogContent
            aria-labelledby={titleId}
            aria-describedby={undefined}
            className="h-[calc(100dvh-4rem)] max-h-180 w-[calc(100%-4rem)] max-w-240 overflow-hidden"
          >
            <DialogTitle className="sr-only">Write</DialogTitle>
            {body}
          </DialogContent>
        </Dialog>
        {confirm}
      </>
    );
  }

  return (
    <>
      {/* autoFocus: Vaul leaves focus where it was by default; a keyboard user must land inside the sheet.
          handleOnly: no drag handle is drawn, so a drag in the text or the photos never pulls the sheet away; Cancel
          and Escape close it, through the same question as the dialog. */}
      <Drawer.Root open onOpenChange={onOpenChange} handleOnly repositionInputs={false} autoFocus>
        <Drawer.Portal>
          <Drawer.Overlay className="fixed inset-0 z-40 bg-scrim" />
          <Drawer.Content
            aria-labelledby={titleId}
            aria-describedby={undefined}
            className="fixed inset-x-0 top-0 bottom-0 z-40 flex flex-col bg-page text-ink outline-none md:top-8 md:rounded-t-lg"
          >
            <Drawer.Title className="sr-only">Write</Drawer.Title>
            {body}
          </Drawer.Content>
        </Drawer.Portal>
      </Drawer.Root>
      {confirm}
    </>
  );
}

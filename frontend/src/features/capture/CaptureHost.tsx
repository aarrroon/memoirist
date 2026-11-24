import { lazy, Suspense, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { useUiStore, type CaptureKind } from '../../stores/ui';

// Where capture lives in the shell (task 4.6). This file is small and loads with the feed; the sheet and everything
// it needs (TipTap, dnd-kit, Vaul, the date picker, Sonner) are split into their own chunks and fetched the first
// time capture opens, so the feed's first JavaScript stays small (design.md §6).

const CaptureView = lazy(() => import('./CaptureView'));
const CaptureToaster = lazy(() => import('./CaptureToaster'));

const KINDS: readonly CaptureKind[] = ['entry', 'dump', 'page'];
const isKind = (value: string | null): value is CaptureKind =>
  value !== null && (KINDS as readonly string[]).includes(value);

export function CaptureHost() {
  const capture = useUiStore((s) => s.capture);
  const openCapture = useUiStore((s) => s.openCapture);
  const [params, setParams] = useSearchParams();
  // The toast after a save outlives the sheet, so its host stays mounted once capture has been used.
  const [used, setUsed] = useState(false);

  // `?write=entry` (or page) opens capture from a link: a home-screen shortcut later (Phase 2), and the design check,
  // which can only load a URL. The parameter is removed at once, so Back doesn't reopen it.
  const write = params.get('write');
  useEffect(() => {
    if (!isKind(write)) return;
    openCapture(write);
    setParams(
      (current) => {
        current.delete('write');
        return current;
      },
      { replace: true },
    );
  }, [write, openCapture, setParams]);

  // Set while rendering, not in an effect: React re-renders at once with the new value and no extra commit.
  if (capture !== null && !used) setUsed(true);

  return (
    <Suspense fallback={null}>
      {capture !== null && <CaptureView kind={capture} />}
      {used && <CaptureToaster />}
    </Suspense>
  );
}

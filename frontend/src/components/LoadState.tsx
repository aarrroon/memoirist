import { RefreshCw } from 'lucide-react';
import { problemMessage } from '../api/problem';
import { useDelayed } from '../lib/useDelayed';
import { Button } from './ui/button';

// Loading and failure for the list screens of task 4.9 (Eras, an Era, Templates, You), after the feed's (task 4.4,
// DESIGN.md §7): nothing for 300 ms, then row shapes in --surface; a failure names the problem in the app's own
// words and offers Try again (F-14).

export function ListLoading({ label, rows = 3 }: { label: string; rows?: number }) {
  const show = useDelayed(300);
  return (
    <div className="flex flex-col">
      <p role="status" className="sr-only">
        {label}
      </p>
      {/* The rows' space is held from the start, so the sections below don't jump when the list arrives
          (design.md §6); only the shapes wait 300 ms. */}
      {Array.from({ length: rows }, (_, index) => (
        <div
          key={index}
          aria-hidden
          className="flex min-h-14 items-center gap-4 border-b border-line"
        >
          {show && <div className="h-4 w-40 rounded-sm bg-surface" />}
        </div>
      ))}
    </div>
  );
}

export function LoadError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-start gap-4">
      <p className="text-body text-ink-2">{problemMessage(error)}</p>
      <Button onClick={onRetry}>
        <RefreshCw size={20} aria-hidden />
        Try again
      </Button>
    </div>
  );
}

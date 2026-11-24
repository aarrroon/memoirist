import { Square } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { BARS } from '../../lib/recording';
import { formatLength } from '../../lib/media';
import type { useRecorder } from './useRecorder';

// The recording in progress, in capture (task 4.7): what shows between pressing Voice and pressing Stop, and the
// messages when the microphone can't be used (F-03: "a message explaining how to allow it; photos still work").
// It shapes the finished voice note's row (DESIGN.md §4): a 44 px round button, 32 bars, then the time in `meta`.
// Stop is outlined in the accent, because it is the action to take (design.md §7); Save stays the one filled
// primary button on the screen (design.md §8).

type Recorder = ReturnType<typeof useRecorder>;

export function VoiceRecorder({ recorder, onClose }: { recorder: Recorder; onClose: () => void }) {
  const { state } = recorder;

  if (state.status === 'idle') return null;

  if (state.status === 'asking') {
    return (
      <div className="flex min-h-11 items-center justify-between gap-3">
        <p className="text-small text-ink-2" aria-live="polite">
          Allow the microphone to record a voice note.
        </p>
        <Button variant="quiet" onClick={() => recorder.discard()}>
          Cancel
        </Button>
      </div>
    );
  }

  if (state.status === 'denied' || state.status === 'failed') {
    const message =
      state.status === 'denied'
        ? 'memoirist can’t use the microphone. Allow it for this site in your browser’s settings, then try again. Photos still work.'
        : state.reason === 'no-microphone'
          ? 'No microphone was found. Connect one, then try again. Photos still work.'
          : 'This browser couldn’t record a voice note. Photos still work.';
    return (
      <div role="alert" className="flex flex-col items-start gap-2">
        <p className="text-small">{message}</p>
        <div className="flex gap-2">
          {(state.status === 'denied' || state.reason === 'no-microphone') && (
            <Button variant="secondary" onClick={() => recorder.start()}>
              Try again
            </Button>
          )}
          <Button variant="quiet" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    );
  }

  // Recording: the newest loudness on the right, the slots not yet filled left blank.
  const bars = [...Array.from({ length: BARS - recorder.live.length }, () => 0), ...recorder.live];
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-3">
        <button
          type="button"
          // Focus lands here when recording starts, so Enter or Space stops it at once.
          autoFocus
          onClick={() => recorder.stop()}
          aria-label="Stop recording"
          className="flex size-11 shrink-0 items-center justify-center rounded-full border border-accent text-accent"
        >
          <Square size={16} aria-hidden fill="currentColor" />
        </button>
        <span aria-hidden className="flex h-8 max-w-60 min-w-0 flex-1 items-center justify-between">
          {bars.map((level, index) => (
            <span
              key={index}
              // A measured loudness, not a size from the scale (design.md §1: heights are free).
              style={{ height: `${Math.round(Math.max(0.15, level) * 100)}%` }}
              className={level > 0 ? 'w-0.75 rounded-full bg-ink-2' : 'w-0.75 rounded-full bg-line'}
            />
          ))}
        </span>
        <span className="shrink-0 text-meta text-ink-2">
          {formatLength(recorder.elapsed) ?? '0:00'}
        </span>
      </div>
      <div className="flex items-center justify-between gap-3">
        <p className="text-small text-ink-2" role="status">
          Recording
        </p>
        <Button variant="quiet" onClick={() => recorder.discard()}>
          Discard
        </Button>
      </div>
    </div>
  );
}

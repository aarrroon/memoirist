import { Pause, Play } from 'lucide-react';
import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';
import { readDuration } from '../lib/audioDuration';
import { formatLength } from '../lib/media';

// A voice note's row (DESIGN.md §4 Voice note row, D-41; tasks 4.5 and 4.7): a 44 px round play button with a
// `--control` border, the waveform, then the length in `meta`. It plays in the feed and in capture.
//
// wavesurfer.js isn't installed (TECH_STACK, D-13 lists it as later polish), so this is a plain player built on
// <audio>: the waveform is a slider (role="slider") the keyboard moves with the arrow keys, Home and End, and a
// pointer moves by pressing on it. Its 32 bars are the recording's real loudness, which only exists for a note
// recorded in this capture (nothing stores levels on the server). Without them, the slider is a plain line: a
// waveform of made-up bars would show a recording that isn't there.
//
// The file is not fetched until Play is pressed (preload="none", task 4.5). A recording from Chrome has no length in
// its header, so the first Play reads it (lib/audioDuration.ts) before playing.

/** How far one arrow key moves, in seconds. */
const STEP = 5;

export interface VoiceNoteProps {
  /** The audio: a presigned URL, or an object URL for a recording not yet uploaded. */
  src: string;
  /** "voice note", or "voice note 2 of 3": what the buttons and the slider are named after. */
  name: string;
  /** Loudness bars from 0 to 1 (lib/recording.ts foldLevels), when this note was recorded here. */
  levels?: readonly number[];
  /** The length when already known (a recording just made), so it shows before the first Play. */
  seconds?: number | null;
}

export function VoiceNote({ src, name, levels, seconds = null }: VoiceNoteProps) {
  const audio = useRef<HTMLAudioElement>(null);
  const [playing, setPlaying] = useState(false);
  const [length, setLength] = useState<number | null>(
    seconds !== null && Number.isFinite(seconds) ? seconds : null,
  );
  const [position, setPosition] = useState(0);

  useEffect(() => {
    const element = audio.current;
    return () => element?.pause();
  }, []);

  const toggle = async () => {
    const element = audio.current;
    if (!element) return;
    if (playing) {
      element.pause();
      return;
    }
    if (length === null) {
      const found = await readDuration(element);
      if (found !== null) setLength(found);
    }
    void element.play().catch(() => setPlaying(false));
  };

  const seek = (to: number) => {
    const element = audio.current;
    if (!element || length === null) return;
    const clamped = Math.min(length, Math.max(0, to));
    element.currentTime = clamped;
    setPosition(clamped);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const moves: Record<string, number> = {
      ArrowRight: position + STEP,
      ArrowUp: position + STEP,
      ArrowLeft: position - STEP,
      ArrowDown: position - STEP,
      Home: 0,
      End: length ?? 0,
    };
    const to = moves[event.key];
    if (to === undefined || length === null) return;
    event.preventDefault();
    seek(to);
  };

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (length === null) return;
    const box = event.currentTarget.getBoundingClientRect();
    if (box.width <= 0) return;
    seek(((event.clientX - box.left) / box.width) * length);
  };

  const played = length ? position / length : 0;
  // The time played while it plays, else the length (DESIGN.md §4); a paused place is shown by the slider.
  const shown = playing ? formatLength(position) : formatLength(length ?? Number.NaN);

  return (
    <div className="flex items-center gap-3">
      <button
        type="button"
        onClick={() => void toggle()}
        aria-label={playing ? `Pause ${name}` : `Play ${name}`}
        className="flex size-11 shrink-0 items-center justify-center rounded-full border border-control text-ink"
      >
        {playing ? <Pause size={20} aria-hidden /> : <Play size={20} aria-hidden />}
      </button>
      <div
        role="slider"
        tabIndex={0}
        aria-label={`Position in ${name}`}
        aria-valuemin={0}
        aria-valuemax={length === null ? 0 : Math.round(length)}
        aria-valuenow={Math.round(position)}
        aria-valuetext={
          length === null
            ? 'Length not known until it plays'
            : `${formatLength(position) ?? '0:00'} of ${formatLength(length) ?? '0:00'}`
        }
        aria-disabled={length === null ? true : undefined}
        onKeyDown={onKeyDown}
        onPointerDown={onPointerDown}
        className="flex h-11 max-w-60 min-w-0 flex-1 touch-none items-center"
      >
        {levels && levels.length > 0 ? (
          <span aria-hidden className="flex h-8 w-full items-center justify-between">
            {levels.map((level, index) => (
              <span
                key={index}
                // The bar's height is the recording's loudness, a measured value rather than a size from the
                // scale (design.md §1: heights are free); a quiet moment still shows a short bar.
                style={{ height: `${Math.round(Math.max(0.15, level) * 100)}%` }}
                className={
                  (index + 0.5) / levels.length <= played
                    ? 'w-0.75 rounded-full bg-ink'
                    : 'w-0.75 rounded-full bg-ink-2'
                }
              />
            ))}
          </span>
        ) : (
          <span aria-hidden className="relative h-0.5 w-full rounded-full bg-ink-2">
            <span
              style={{ width: `${Math.round(played * 100)}%` }}
              className="absolute inset-y-0 left-0 rounded-full bg-ink"
            />
          </span>
        )}
      </div>
      {shown && <span className="shrink-0 text-meta text-ink-2">{shown}</span>}
      <audio
        ref={audio}
        src={src}
        preload="none"
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onEnded={() => {
          setPlaying(false);
          setPosition(0);
        }}
        onTimeUpdate={(event) => {
          // The seek-to-the-end that finds a WebM's length (readDuration) fires this too; only playing moves it.
          if (!event.currentTarget.paused) setPosition(event.currentTarget.currentTime);
        }}
        onLoadedMetadata={(event) => {
          const duration = event.currentTarget.duration;
          if (Number.isFinite(duration)) setLength(duration);
        }}
      />
    </div>
  );
}

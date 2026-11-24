import { Pause, Play } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { Media } from '../api/feed';
import { formatLength } from '../lib/media';

// A voice note's row under an entry's text (task 4.5, DESIGN.md §4 Voice note, D-41): a 44 px round play button
// with a `--control` border, then its length. The file is not fetched until Play is pressed (preload="none"),
// so a feed of voice notes costs nothing on a slow phone; the length shows once the browser has read it.
// The row writes no "Voice note" label: DESIGN.md §4 gives none, the button's name says it, and an entry with only
// a voice note already shows "Voice note" as its display title (D-24), so a label would repeat it (D-40 review).
// The waveform of the design needs the recording's levels, which nothing stores yet; task 4.7 (voice notes) owns it.

export function VoiceNote({ note, index, count }: { note: Media; index: number; count: number }) {
  const audio = useRef<HTMLAudioElement>(null);
  const [playing, setPlaying] = useState(false);
  const [length, setLength] = useState<string | null>(null);
  const name = count > 1 ? `voice note ${index + 1} of ${count}` : 'voice note';

  useEffect(() => {
    const element = audio.current;
    return () => element?.pause();
  }, []);

  const toggle = () => {
    const element = audio.current;
    if (!element) return;
    if (!playing) {
      void element.play().catch(() => setPlaying(false));
    } else {
      element.pause();
    }
  };

  return (
    <div className="flex items-center gap-3">
      <button
        type="button"
        onClick={toggle}
        aria-label={playing ? `Pause ${name}` : `Play ${name}`}
        className="flex size-11 shrink-0 items-center justify-center rounded-full border border-control text-ink"
      >
        {playing ? <Pause size={20} aria-hidden /> : <Play size={20} aria-hidden />}
      </button>
      {length && <span className="text-meta text-ink-2">{length}</span>}
      <audio
        ref={audio}
        src={note.fileUrl}
        preload="none"
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onEnded={() => setPlaying(false)}
        onLoadedMetadata={(event) => setLength(formatLength(event.currentTarget.duration))}
      />
    </div>
  );
}

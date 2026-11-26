// A voice note's length, read from the <audio> element that will play it (task 4.7).
//
// Why this exists: Chrome's MediaRecorder writes WebM without a duration in its header, so a recording made in the
// app reports `duration === Infinity` when it is loaded, and the player could show neither its length nor a seek
// bar. Seeking far past the end makes the browser read the file to its end; it then knows the real length, fires
// `durationchange`, and we seek back to the start. The file is small (a voice note), and this runs only when the
// user presses Play, so a feed of voice notes still loads nothing up front (preload="none", task 4.5).

/** The part of HTMLMediaElement this needs, so a test can pass a plain EventTarget. */
export interface MediaLike extends EventTarget {
  readonly duration: number;
  readonly readyState: number;
  currentTime: number;
  preload: string;
  load(): void;
}

/** HTMLMediaElement.HAVE_METADATA: the duration (finite or not) is known from here on. */
const HAVE_METADATA = 1;

// TODO(human) PRACTICE: write `nextEvent(target, names, ms)`: a promise that resolves with the name of the first of
// `names` that `target` fires, or with null if none fires within `ms`. Whatever happens, it must remove every
// listener it added and clear its timer, so a player that is opened and closed many times leaks nothing.
// Hint: one `finish(result)` function that cleans up and then resolves; use it from each listener and the timer.
// A promise can be settled only once, so a late event after the timeout is harmless, but its listener isn't.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function nextEvent(target: EventTarget, names: readonly string[], ms: number) {
  return new Promise<string | null>((resolve) => {
    const listeners = names.map((name) => [name, () => finish(name)] as const);
    const timer = setTimeout(() => finish(null), ms);
    function finish(result: string | null) {
      clearTimeout(timer);
      for (const [name, listener] of listeners) target.removeEventListener(name, listener);
      resolve(result);
    }
    for (const [name, listener] of listeners) target.addEventListener(name, listener);
  });
}
// └── END ANSWER ▲ ──

/**
 * The real length of the audio in seconds, or null when it can't be found within `ms` (a broken file, no network).
 * Leaves the element at the start, ready to play.
 */
export async function readDuration(audio: MediaLike, ms = 5000): Promise<number | null> {
  if (audio.readyState < HAVE_METADATA) {
    audio.preload = 'metadata';
    const loading = nextEvent(audio, ['loadedmetadata', 'error'], ms);
    audio.load();
    if ((await loading) !== 'loadedmetadata') return null;
  }
  if (Number.isFinite(audio.duration)) return audio.duration;
  // NaN: the browser read the file but can't tell its length. Only Infinity is the WebM case below.
  if (audio.duration !== Number.POSITIVE_INFINITY) return null;

  // The WebM without a length: seek past the end, wait for the browser to find it, then come back.
  const found = nextEvent(audio, ['durationchange', 'error'], ms);
  audio.currentTime = Number.MAX_SAFE_INTEGER;
  const event = await found;
  const duration = audio.duration;
  // Wait for the seek back to land: reaching the end can fire 'ended', and it must arrive before Play does, or the
  // player would think a note that has just started had finished (seen in Chrome, task 4.7).
  const back = nextEvent(audio, ['seeked', 'error'], ms);
  audio.currentTime = 0;
  await back;
  return event === 'durationchange' && Number.isFinite(duration) ? duration : null;
}

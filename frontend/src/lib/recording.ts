// Small, pure facts about recording a voice note (task 4.7, frontend.md, Capture and media). The browser parts
// (MediaRecorder, the microphone, the analyser) live in features/capture/useRecorder.ts; this file only decides.

/** The formats a recording may be made in, best first (frontend.md): Opus in WebM is what Chrome, Edge and Firefox
 *  record; Safari and iOS record AAC in MP4. Both are on the server's allow-list (D-17, API_CONTRACT §Media). */
export const RECORDING_TYPES = ['audio/webm;codecs=opus', 'audio/mp4'] as const;

export type RecordingType = (typeof RECORDING_TYPES)[number];

/**
 * The first format this browser can record, or null when it can record none of them (then capture hides Voice,
 * F-03). `isTypeSupported` is passed in, so the choice can be tested without a browser.
 */
export function pickRecordingType(
  isTypeSupported: ((type: string) => boolean) | undefined,
): RecordingType | null {
  if (!isTypeSupported) return null;
  return RECORDING_TYPES.find((type) => isTypeSupported(type)) ?? null;
}

/** Whether this browser can record a voice note at all: a microphone API, MediaRecorder and one of our formats. */
export function canRecord(): boolean {
  if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia) return false;
  if (typeof MediaRecorder === 'undefined') return false;
  return pickRecordingType((type) => MediaRecorder.isTypeSupported(type)) !== null;
}

/** A finished recording as a File, named for its format. The name is never stored (API_CONTRACT §Media); the type
 *  is what the server checks, and it drops ";codecs=opus" itself. */
export function recordingFile(chunks: Blob[], type: string, at: Date): File {
  const extension = type.startsWith('audio/mp4') ? 'm4a' : 'webm';
  return new File(chunks, `voice-note.${extension}`, { type, lastModified: at.getTime() });
}

/** How many bars a voice note's waveform has (DESIGN.md §4 Voice note row). */
export const BARS = 32;

/**
 * Folds a recording's loudness samples (each 0 to 1, one every animation frame while recording) into `count` bars:
 * each bar is the loudest sample of its slice, so a short word still shows. A recording with fewer samples than
 * bars repeats them; one with none gives no bars.
 */
export function foldLevels(samples: readonly number[], count = BARS): number[] {
  if (samples.length === 0) return [];
  const bars: number[] = [];
  for (let bar = 0; bar < count; bar++) {
    const from = Math.floor((bar * samples.length) / count);
    const to = Math.max(from + 1, Math.floor(((bar + 1) * samples.length) / count));
    let peak = 0;
    for (let i = from; i < to && i < samples.length; i++) peak = Math.max(peak, samples[i] ?? 0);
    bars.push(Math.min(1, Math.max(0, peak)));
  }
  return bars;
}

/** The loudness of one frame of time-domain samples (bytes centred on 128, from an AnalyserNode), from 0 to 1. The
 *  root mean square is scaled up, because speech rarely goes past a third of full scale. */
export function loudness(frame: Uint8Array): number {
  if (frame.length === 0) return 0;
  let sum = 0;
  for (const byte of frame) {
    const centred = (byte - 128) / 128;
    sum += centred * centred;
  }
  return Math.min(1, Math.sqrt(sum / frame.length) * 3);
}

import { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import { BARS, foldLevels, loudness, pickRecordingType, recordingFile } from '../../lib/recording';

// Recording a voice note in capture (task 4.7, frontend.md: MediaRecorder with the first supported of
// audio/webm;codecs=opus and audio/mp4). The hook owns the browser parts: the microphone stream, the MediaRecorder,
// and an AnalyserNode that samples loudness ten times a second for the live bars and the saved waveform. It hands
// the finished recording to capture as a File, which then uploads like any other (saveEntry.ts).

/** A recording stops by itself after 20 minutes: at 64 kb/s that is about 10 MB, well under the 25 MB limit (D-17)
 *  whatever the browser's real bitrate. */
export const MAX_SECONDS = 20 * 60;
const BITS_PER_SECOND = 64_000;
const SAMPLE_MS = 100;

export interface Recording {
  file: File;
  seconds: number;
  /** BARS loudness bars from 0 to 1, or [] when the browser gave no analyser. */
  levels: number[];
}

export type RecorderState =
  | { status: 'idle' }
  /** Waiting for the user to answer the browser's microphone prompt. */
  | { status: 'asking' }
  | { status: 'recording' }
  /** The user (or the browser's settings) said no to the microphone: F-03 explains how to allow it. */
  | { status: 'denied' }
  /** No microphone, or the recorder broke. */
  | { status: 'failed'; reason: 'no-microphone' | 'other' };

export type RecorderAction =
  | { type: 'ask' }
  | { type: 'started' }
  | { type: 'denied' }
  | { type: 'failed'; reason: 'no-microphone' | 'other' }
  | { type: 'finished' };

// TODO(human) PRACTICE: write the reducer for the recorder's states. Start from any state but 'asking' and
// 'recording'; 'started' only follows 'asking'; 'denied' and 'failed' follow 'asking' or 'recording'; 'finished'
// (stop or discard) always returns to idle. An action that doesn't fit the current state leaves it unchanged.
// Hint: switch on action.type and return a new object; `satisfies` or the RecorderState union makes TypeScript check
// each branch. Think about a discard pressed while the microphone prompt is still open: what must 'started' do then?
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function recorderReducer(state: RecorderState, action: RecorderAction): RecorderState {
  switch (action.type) {
    case 'ask':
      return state.status === 'asking' || state.status === 'recording'
        ? state
        : { status: 'asking' };
    case 'started':
      return state.status === 'asking' ? { status: 'recording' } : state;
    case 'denied':
      return state.status === 'asking' || state.status === 'recording'
        ? { status: 'denied' }
        : state;
    case 'failed':
      return state.status === 'asking' || state.status === 'recording'
        ? { status: 'failed', reason: action.reason }
        : state;
    case 'finished':
      return { status: 'idle' };
  }
}
// └── END ANSWER ▲ ──

/** What the browser's getUserMedia error means for the user. */
function failureOf(error: unknown): RecorderAction {
  const name = error instanceof DOMException || error instanceof Error ? error.name : '';
  if (name === 'NotAllowedError' || name === 'SecurityError') return { type: 'denied' };
  if (name === 'NotFoundError' || name === 'OverconstrainedError')
    return { type: 'failed', reason: 'no-microphone' };
  return { type: 'failed', reason: 'other' };
}

interface Session {
  stream: MediaStream;
  recorder: MediaRecorder;
  context: AudioContext | null;
  timer: ReturnType<typeof setInterval>;
  startedAt: number;
  samples: number[];
  keep: boolean;
}

export function useRecorder(onRecorded: (recording: Recording) => void) {
  const [state, dispatch] = useReducer(recorderReducer, { status: 'idle' });
  const [elapsed, setElapsed] = useState(0);
  const [live, setLive] = useState<number[]>([]);
  const session = useRef<Session | null>(null);
  // Bumped by every stop or discard, so a microphone prompt answered after a discard is released at once.
  const attempt = useRef(0);
  const recorded = useRef(onRecorded);
  useEffect(() => {
    recorded.current = onRecorded;
  }, [onRecorded]);

  const end = useCallback((keep: boolean) => {
    attempt.current++;
    const current = session.current;
    session.current = null;
    setElapsed(0);
    setLive([]);
    dispatch({ type: 'finished' });
    if (!current) return;
    current.keep = keep;
    clearInterval(current.timer);
    // onstop (below) builds the file, then releases the microphone, so the browser's recording light goes off.
    if (current.recorder.state !== 'inactive') current.recorder.stop();
    else release(current);
  }, []);

  const start = useCallback(async () => {
    const type = pickRecordingType(
      typeof MediaRecorder === 'undefined' ? undefined : (t) => MediaRecorder.isTypeSupported(t),
    );
    if (!type || !navigator.mediaDevices?.getUserMedia) {
      dispatch({ type: 'ask' });
      dispatch({ type: 'failed', reason: 'other' });
      return;
    }
    const mine = ++attempt.current;
    dispatch({ type: 'ask' });
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (error) {
      if (mine === attempt.current) dispatch(failureOf(error));
      return;
    }
    if (mine !== attempt.current) {
      // Discarded while the prompt was open: give the microphone straight back.
      stream.getTracks().forEach((track) => track.stop());
      return;
    }

    let recorder: MediaRecorder;
    try {
      recorder = new MediaRecorder(stream, { mimeType: type, audioBitsPerSecond: BITS_PER_SECOND });
    } catch {
      stream.getTracks().forEach((track) => track.stop());
      dispatch({ type: 'failed', reason: 'other' });
      return;
    }

    let context: AudioContext | null;
    let analyser: AnalyserNode | null = null;
    try {
      context = new AudioContext();
      analyser = context.createAnalyser();
      analyser.fftSize = 1024;
      context.createMediaStreamSource(stream).connect(analyser);
    } catch {
      // No Web Audio (or it refused): the recording still works, without bars.
      context = null;
      analyser = null;
    }
    const frame = analyser ? new Uint8Array(analyser.fftSize) : null;

    const chunks: Blob[] = [];
    const current: Session = {
      stream,
      recorder,
      context,
      timer: setInterval(() => {
        const seconds = (performance.now() - current.startedAt) / 1000;
        if (analyser && frame) {
          analyser.getByteTimeDomainData(frame);
          current.samples.push(loudness(frame));
          setLive(current.samples.slice(-BARS));
        }
        setElapsed(seconds);
        if (seconds >= MAX_SECONDS) end(true);
      }, SAMPLE_MS),
      startedAt: performance.now(),
      samples: [],
      keep: true,
    };
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) chunks.push(event.data);
    };
    recorder.onstop = () => {
      release(current);
      if (!current.keep || chunks.length === 0) return;
      const seconds = (performance.now() - current.startedAt) / 1000;
      // The recorder's own type can name the codec the browser really chose; ours is the fallback.
      const file = recordingFile(chunks, recorder.mimeType || type, new Date());
      recorded.current({ file, seconds, levels: foldLevels(current.samples) });
    };
    recorder.onerror = () => {
      current.keep = false;
      end(false);
      dispatch({ type: 'ask' });
      dispatch({ type: 'failed', reason: 'other' });
    };
    session.current = current;
    // A slice every second, so a long recording isn't held as one growing buffer.
    recorder.start(1000);
    dispatch({ type: 'started' });
  }, [end]);

  // Closing capture mid-recording throws the recording away and frees the microphone.
  useEffect(() => () => end(false), [end]);

  return {
    state,
    elapsed,
    live,
    start: () => void start(),
    stop: () => end(true),
    discard: () => end(false),
  };
}

function release(session: Session) {
  session.stream.getTracks().forEach((track) => track.stop());
  void session.context?.close().catch(() => undefined);
}

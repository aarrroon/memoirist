import { describe, expect, it } from 'vitest';
import { foldLevels, loudness, pickRecordingType, recordingFile } from './recording';

describe('pickRecordingType', () => {
  it('prefers Opus in WebM, as Chrome records it', () => {
    expect(pickRecordingType(() => true)).toBe('audio/webm;codecs=opus');
  });

  it('falls back to MP4, as Safari and iOS record it', () => {
    expect(pickRecordingType((type) => type === 'audio/mp4')).toBe('audio/mp4');
  });

  it('gives null when neither format can be recorded, or there is no MediaRecorder', () => {
    expect(pickRecordingType(() => false)).toBeNull();
    expect(pickRecordingType(undefined)).toBeNull();
  });
});

describe('recordingFile', () => {
  it('names the file for its format and keeps the declared type', () => {
    const at = new Date('2026-11-25T01:00:00Z');
    const webm = recordingFile([new Blob(['a'])], 'audio/webm;codecs=opus', at);
    expect(webm.name).toBe('voice-note.webm');
    expect(webm.type).toBe('audio/webm;codecs=opus');
    expect(recordingFile([new Blob(['a'])], 'audio/mp4', at).name).toBe('voice-note.m4a');
  });
});

describe('foldLevels', () => {
  it('keeps the loudest sample of each slice', () => {
    const samples = Array.from({ length: 64 }, (_, i) => (i % 2 === 0 ? 0.1 : 0.9));
    const bars = foldLevels(samples);
    expect(bars).toHaveLength(32);
    expect(bars.every((bar) => bar === 0.9)).toBe(true);
  });

  it('repeats a short recording across the bars, and gives none for no samples', () => {
    expect(foldLevels([0.5, 1], 4)).toEqual([0.5, 0.5, 1, 1]);
    expect(foldLevels([])).toEqual([]);
  });

  it('clamps values from outside 0 to 1', () => {
    expect(foldLevels([2, -1], 2)).toEqual([1, 0]);
  });
});

describe('loudness', () => {
  it('is 0 for silence and grows with the signal', () => {
    expect(loudness(new Uint8Array(8).fill(128))).toBe(0);
    expect(loudness(new Uint8Array([128, 160, 128, 96]))).toBeGreaterThan(0.3);
    expect(loudness(new Uint8Array([0, 255, 0, 255]))).toBe(1);
    expect(loudness(new Uint8Array())).toBe(0);
  });
});

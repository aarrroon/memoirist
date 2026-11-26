import { describe, expect, it } from 'vitest';
import { TALLEST, WIDEST, frameRatio } from './photoFrame';

describe('frameRatio', () => {
  it('keeps a photo’s own shape when it sits between 4:5 and 1.91:1', () => {
    expect(frameRatio({ width: 960, height: 640 })).toBe(1.5);
    expect(frameRatio({ width: 1000, height: 1000 })).toBe(1);
  });

  it('gives a tall photo the 4:5 frame', () => {
    expect(frameRatio({ width: 1080, height: 1920 })).toBe(TALLEST);
  });

  it('gives a wide panorama the 1.91:1 frame', () => {
    expect(frameRatio({ width: 6000, height: 1000 })).toBe(WIDEST);
  });

  it('reads the limits themselves as inside', () => {
    expect(frameRatio({ width: 800, height: 1000 })).toBe(TALLEST);
    expect(frameRatio({ width: 1910, height: 1000 })).toBeCloseTo(WIDEST);
  });

  it('gives a photo with no size the 4:5 frame (D-17)', () => {
    expect(frameRatio({ width: null, height: null })).toBe(TALLEST);
    expect(frameRatio(undefined)).toBe(TALLEST);
  });

  it('never gives NaN or Infinity, whatever the numbers', () => {
    const odd = [
      { width: 0, height: 0 },
      { width: 640, height: 0 },
      { width: 0, height: 480 },
      { width: -640, height: 480 },
      { width: 640, height: -480 },
      { width: Number.NaN, height: 480 },
      { width: Number.POSITIVE_INFINITY, height: 480 },
      { width: 640, height: null },
    ];
    for (const photo of odd) {
      expect(frameRatio(photo), JSON.stringify(photo)).toBe(TALLEST);
    }
  });
});

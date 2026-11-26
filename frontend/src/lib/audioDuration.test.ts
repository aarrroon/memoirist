import { afterEach, describe, expect, it, vi } from 'vitest';
import { nextEvent, readDuration, type MediaLike } from './audioDuration';

/** A fake <audio>: an EventTarget that counts its listeners and plays out a script when it is loaded or seeked. */
class FakeAudio extends EventTarget implements MediaLike {
  duration = Number.NaN;
  readyState = 0;
  preload = 'none';
  listeners = 0;
  private time = 0;
  private readonly onLoad: (self: FakeAudio) => void;
  private readonly onSeek: (self: FakeAudio, to: number) => void;
  constructor(
    onLoad: (self: FakeAudio) => void,
    onSeek: (self: FakeAudio, to: number) => void = () => undefined,
  ) {
    super();
    this.onLoad = onLoad;
    this.onSeek = onSeek;
  }
  get currentTime() {
    return this.time;
  }
  set currentTime(to: number) {
    this.time = to;
    this.onSeek(this, to);
    later(() => this.dispatchEvent(new Event('seeked')));
  }
  load() {
    this.onLoad(this);
  }
  override addEventListener(...args: Parameters<EventTarget['addEventListener']>) {
    this.listeners++;
    super.addEventListener(...args);
  }
  override removeEventListener(...args: Parameters<EventTarget['removeEventListener']>) {
    this.listeners--;
    super.removeEventListener(...args);
  }
}

const later = (fn: () => void) => queueMicrotask(fn);

afterEach(() => vi.useRealTimers());

describe('nextEvent', () => {
  it('resolves with the first event fired and removes every listener', async () => {
    const target = new FakeAudio(() => undefined);
    const waiting = nextEvent(target, ['a', 'b'], 1000);
    expect(target.listeners).toBe(2);
    target.dispatchEvent(new Event('b'));
    await expect(waiting).resolves.toBe('b');
    expect(target.listeners).toBe(0);
  });

  it('resolves with null after the time runs out, and cleans up', async () => {
    vi.useFakeTimers();
    const target = new FakeAudio(() => undefined);
    const waiting = nextEvent(target, ['a'], 50);
    vi.advanceTimersByTime(50);
    await expect(waiting).resolves.toBeNull();
    expect(target.listeners).toBe(0);
  });
});

describe('readDuration', () => {
  it('reads a normal file’s length from its metadata', async () => {
    const audio = new FakeAudio((self) =>
      later(() => {
        self.duration = 34.2;
        self.readyState = 1;
        self.dispatchEvent(new Event('loadedmetadata'));
      }),
    );
    await expect(readDuration(audio)).resolves.toBe(34.2);
    expect(audio.preload).toBe('metadata');
  });

  it('finds the length of a Chrome WebM recording that says Infinity, and seeks back to the start', async () => {
    const audio = new FakeAudio(
      (self) =>
        later(() => {
          self.duration = Number.POSITIVE_INFINITY;
          self.readyState = 1;
          self.dispatchEvent(new Event('loadedmetadata'));
        }),
      (self, to) => {
        if (to > 1000)
          later(() => {
            self.duration = 7.5;
            self.dispatchEvent(new Event('durationchange'));
          });
      },
    );
    await expect(readDuration(audio)).resolves.toBe(7.5);
    expect(audio.currentTime).toBe(0);
    expect(audio.listeners).toBe(0);
  });

  it('gives null, without seeking, when the browser can’t tell the length (NaN)', async () => {
    const audio = new FakeAudio(
      (self) =>
        later(() => {
          self.readyState = 1;
          self.dispatchEvent(new Event('loadedmetadata'));
        }),
      () => {
        throw new Error('must not seek');
      },
    );
    await expect(readDuration(audio)).resolves.toBeNull();
  });

  it('gives null for a file that fails to load', async () => {
    const audio = new FakeAudio((self) => later(() => self.dispatchEvent(new Event('error'))));
    await expect(readDuration(audio)).resolves.toBeNull();
  });

  it('gives null when the length never turns up', async () => {
    vi.useFakeTimers();
    const audio = new FakeAudio(() => undefined);
    audio.readyState = 1;
    audio.duration = Number.POSITIVE_INFINITY;
    const reading = readDuration(audio, 100);
    await vi.advanceTimersByTimeAsync(100);
    await expect(reading).resolves.toBeNull();
    expect(audio.currentTime).toBe(0);
  });
});

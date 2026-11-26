import { afterEach, describe, expect, test, vi } from 'vitest';
import {
  copiesNeeded,
  copyType,
  fileProblem,
  makeCopies,
  MAX_FILE_BYTES,
  shrinkPhoto,
} from './files';

// The client's own checks before an upload (D-17): the allow-list, 25 MB, and shrinking photos first.

const compress = vi.hoisted(() => vi.fn());
vi.mock('browser-image-compression', () => ({ default: compress }));

describe('fileProblem', () => {
  test('accepts the D-17 types up to 25 MB, with a parameter on the type', () => {
    expect(fileProblem({ type: 'image/jpeg', size: MAX_FILE_BYTES })).toBeNull();
    expect(fileProblem({ type: 'audio/webm;codecs=opus', size: 1000 })).toBeNull();
  });

  test('refuses HEIC, video and anything over 25 MB', () => {
    expect(fileProblem({ type: 'image/heic', size: 1000 })).toMatch(/can’t be added/);
    expect(fileProblem({ type: 'video/mp4', size: 1000 })).toMatch(/can’t be added/);
    expect(fileProblem({ type: 'image/png', size: MAX_FILE_BYTES + 1 })).toBe(
      'This file is over 25 MB.',
    );
  });
});

describe('shrinkPhoto', () => {
  const photo = new File([new Uint8Array(5000)], 'beach.jpg', { type: 'image/jpeg' });

  test('sends the smaller copy, keeping the name and type', async () => {
    compress.mockResolvedValueOnce(new Blob([new Uint8Array(1000)], { type: 'image/jpeg' }));
    const shrunk = await shrinkPhoto(photo);
    expect(shrunk.size).toBe(1000);
    expect(shrunk.name).toBe('beach.jpg');
    expect(shrunk.type).toBe('image/jpeg');
    expect(compress).toHaveBeenLastCalledWith(
      photo,
      expect.objectContaining({ maxWidthOrHeight: 2560 }),
    );
  });

  test('shrinks a diary page more gently than a photo, so handwriting stays readable (task 4.13)', async () => {
    compress.mockResolvedValueOnce(new Blob([new Uint8Array(1000)], { type: 'image/jpeg' }));
    await shrinkPhoto(photo, 'PAGE_SCAN');
    expect(compress).toHaveBeenLastCalledWith(
      photo,
      expect.objectContaining({ maxWidthOrHeight: 4096, maxSizeMB: 6 }),
    );
  });

  test('keeps the original when shrinking fails or makes it bigger', async () => {
    compress.mockRejectedValueOnce(new Error('no canvas'));
    expect(await shrinkPhoto(photo)).toBe(photo);
    compress.mockResolvedValueOnce(new Blob([new Uint8Array(9000)], { type: 'image/jpeg' }));
    expect(await shrinkPhoto(photo)).toBe(photo);
  });

  test('leaves a voice note alone', async () => {
    const voice = new File([new Uint8Array(10)], 'note.webm', { type: 'audio/webm' });
    compress.mockClear();
    expect(await shrinkPhoto(voice)).toBe(voice);
    expect(compress).not.toHaveBeenCalled();
  });
});

// Task 4.18 (D-43): which copies a photo gets, in which format, and how they are made.
describe('copiesNeeded', () => {
  test('one copy for each size the photo is bigger than, by its longest side', () => {
    expect(copiesNeeded(2560, 1920)).toEqual(['thumbnail', 'screen']);
    expect(copiesNeeded(1080, 1920)).toEqual(['thumbnail', 'screen']);
    expect(copiesNeeded(1200, 900)).toEqual(['thumbnail']);
    expect(copiesNeeded(1600, 1200)).toEqual(['thumbnail']);
  });

  test('a photo no bigger than a copy gets none of that size', () => {
    expect(copiesNeeded(480, 320)).toEqual([]);
    expect(copiesNeeded(300, 200)).toEqual([]);
  });

  test('a size that can’t be read gets no copies, so screens use the original', () => {
    expect(copiesNeeded(0, 0)).toEqual([]);
    expect(copiesNeeded(Number.NaN, 900)).toEqual([]);
    expect(copiesNeeded(-5, -5)).toEqual([]);
    expect(copiesNeeded(Number.POSITIVE_INFINITY, 10)).toEqual([]);
  });
});

describe('copyType', () => {
  test('a WebP stays WebP; JPEG and PNG become JPEG', () => {
    expect(copyType('image/webp')).toBe('image/webp');
    expect(copyType('image/png')).toBe('image/jpeg');
    expect(copyType('image/jpeg')).toBe('image/jpeg');
  });
});

describe('makeCopies', () => {
  const decodesTo = (width: number, height: number) =>
    vi.stubGlobal(
      'createImageBitmap',
      vi.fn(() => Promise.resolve({ width, height, close: () => undefined })),
    );
  afterEach(() => vi.unstubAllGlobals());

  test('makes a 480 px thumbnail and a 1600 px screen copy, named after the photo, on the page’s own thread', async () => {
    decodesTo(2560, 1707);
    compress.mockClear();
    compress.mockImplementation((_file: File, options: { fileType: string }) =>
      Promise.resolve(new Blob([new Uint8Array(10)], { type: options.fileType })),
    );
    const png = new File([new Uint8Array(5000)], 'beach.png', { type: 'image/png' });

    const copies = await makeCopies(png);

    expect(copies.thumbnail?.name).toBe('beach-thumbnail.jpg');
    expect(copies.thumbnail?.type).toBe('image/jpeg');
    expect(copies.screen?.name).toBe('beach-screen.jpg');
    expect(compress).toHaveBeenCalledWith(
      png,
      expect.objectContaining({
        maxWidthOrHeight: 480,
        fileType: 'image/jpeg',
        useWebWorker: false,
      }),
    );
    expect(compress).toHaveBeenCalledWith(
      png,
      expect.objectContaining({ maxWidthOrHeight: 1600, useWebWorker: false }),
    );
  });

  test('a WebP photo’s copies are WebP', async () => {
    decodesTo(2000, 1000);
    compress.mockImplementation((_file: File, options: { fileType: string }) =>
      Promise.resolve(new Blob([new Uint8Array(10)], { type: options.fileType })),
    );
    const copies = await makeCopies(
      new File([new Uint8Array(50)], 'a.webp', { type: 'image/webp' }),
    );
    expect(copies.screen?.name).toBe('a-screen.webp');
    expect(copies.screen?.type).toBe('image/webp');
  });

  test('a copy that fails is left out; a picture that can’t be decoded gets none', async () => {
    decodesTo(2000, 1000);
    compress.mockReset();
    compress
      .mockRejectedValueOnce(new Error('no canvas'))
      .mockResolvedValueOnce(new Blob([new Uint8Array(10)], { type: 'image/jpeg' }));
    const photo = new File([new Uint8Array(50)], 'a.jpg', { type: 'image/jpeg' });
    expect(Object.keys(await makeCopies(photo))).toEqual(['screen']);

    vi.stubGlobal(
      'createImageBitmap',
      vi.fn(() => Promise.reject(new Error('broken'))),
    );
    expect(await makeCopies(photo)).toEqual({});
  });

  test('a voice note gets none', async () => {
    decodesTo(2000, 1000);
    expect(
      await makeCopies(new File([new Uint8Array(5)], 'n.webm', { type: 'audio/webm' })),
    ).toEqual({});
  });
});

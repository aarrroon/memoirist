import { describe, expect, test, vi } from 'vitest';
import { fileProblem, MAX_FILE_BYTES, shrinkPhoto } from './files';

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

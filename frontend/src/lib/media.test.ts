import { describe, expect, test } from 'vitest';
import { pictureUrl } from './media';

// pictureUrl (task 4.18, D-43): each screen asks for a size, and a missing copy falls back to the next size up,
// ending at the original. Never down: a missing screen copy must not become the 480 px thumbnail.

const FILE = 'https://store/p.jpg?sig';
const THUMB = 'https://store/p-thumbnail.jpg?sig';
const SCREEN = 'https://store/p-screen.jpg?sig';

describe('pictureUrl', () => {
  test('gives each size its own copy when the photo has both', () => {
    const media = { fileUrl: FILE, thumbnailUrl: THUMB, screenUrl: SCREEN };
    expect(pictureUrl(media, 'thumbnail')).toBe(THUMB);
    expect(pictureUrl(media, 'screen')).toBe(SCREEN);
    expect(pictureUrl(media, 'original')).toBe(FILE);
  });

  test('a missing thumbnail falls back to the screen copy, then to the original', () => {
    expect(pictureUrl({ fileUrl: FILE, thumbnailUrl: null, screenUrl: SCREEN }, 'thumbnail')).toBe(
      SCREEN,
    );
    expect(pictureUrl({ fileUrl: FILE, thumbnailUrl: null, screenUrl: null }, 'thumbnail')).toBe(
      FILE,
    );
  });

  test('a missing screen copy goes up to the original, never down to the thumbnail', () => {
    expect(pictureUrl({ fileUrl: FILE, thumbnailUrl: THUMB, screenUrl: null }, 'screen')).toBe(
      FILE,
    );
  });

  test('the original is always the original, even when copies exist', () => {
    expect(pictureUrl({ fileUrl: FILE, thumbnailUrl: THUMB, screenUrl: SCREEN }, 'original')).toBe(
      FILE,
    );
  });
});

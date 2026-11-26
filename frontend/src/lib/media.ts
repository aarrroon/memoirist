import type { Media } from '../api/feed';

// Small facts about an entry's media (task 4.5), shared by the swipe, the voice note row and the feed entry.

/** A photo or a diary-page photo: what goes in the swipe. A voice note is its own row under the text (D-41). */
export const isPicture = (media: Media) => media.mediaType.startsWith('image/');

/** "0:42", "12:05": a voice note's length as its row writes it, or null when the browser can't tell yet (a
 *  recording from MediaRecorder reports Infinity until it has been read to the end). */
export function formatLength(seconds: number): string | null {
  if (!Number.isFinite(seconds) || seconds < 0) return null;
  const whole = Math.round(seconds);
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, '0')}`;
}

/** Which copy of a photo a screen draws (D-43): the thumbnail for anything small, the screen copy for the swipe and
 *  an Era page's cover, the original only in the entry view, where photos are shown whole. */
export type PictureSize = 'thumbnail' | 'screen' | 'original';

// TODO(human) PRACTICE: Write pictureUrl(media, size): the URL a screen should draw for this photo. A copy can be
//   missing (null) for a photo from before task 4.18, from another client, or one already no bigger than that copy.
//   Decide which way to fall back, and make TypeScript refuse to compile if someone adds a fourth size and forgets it.
// Hint: D-43 says "falls back to the next size up, ending at the original": a missing thumbnail becomes the screen
//   copy, never the other way, because a blurry swipe is worse than a heavy tile. `??` falls through null and
//   undefined only (not ''), and a switch whose default assigns the value to a `never` variable is an exhaustive check.
//   Checked by media.test.ts and the cover, week-tile, swipe and entry-view tests.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function pictureUrl(
  media: Pick<Media, 'fileUrl' | 'thumbnailUrl' | 'screenUrl'>,
  size: PictureSize,
): string {
  switch (size) {
    case 'thumbnail':
      return media.thumbnailUrl ?? media.screenUrl ?? media.fileUrl;
    case 'screen':
      return media.screenUrl ?? media.fileUrl;
    case 'original':
      return media.fileUrl;
    default: {
      const unknown: never = size;
      throw new Error(`No such picture size: ${String(unknown)}`);
    }
  }
}
// └── END ANSWER ▲ ──

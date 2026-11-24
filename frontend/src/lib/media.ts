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

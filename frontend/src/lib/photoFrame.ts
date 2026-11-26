// The shape of a swipe's frame (task 4.5, D-17, D-41, DESIGN.md §4 Swipe). The frame takes the first photo's own
// shape, kept between 4:5 tall and 1.91:1 wide, and the other photos are cropped to it. The server stores each
// photo's width and height from its header, so the frame's space is reserved before any photo arrives and nothing
// below it jumps (layout shift <= 0.1, design.md §6).

/** 4:5, the tallest frame the feed allows, and the frame of a photo with no stored size (D-17). */
export const TALLEST = 4 / 5;

/** 1.91:1, the widest frame the feed allows. */
export const WIDEST = 1.91;

/** What the frame needs from a photo: the API's MediaResponse has both, each a number or null. */
export type Sized = { width: number | null; height: number | null };

// TODO(human) PRACTICE: Write frameRatio(photo): the width ÷ height of the frame for a swipe whose first photo is
// `photo`, kept between TALLEST and WIDEST. A photo with no size gets TALLEST.
// Hint: D-17 says a voice note, an unreadable header and anything uploaded before task 4.5 have width and height
// null, and the database stores both or neither. Still, this is data from the network: what should 0, a negative
// number, NaN or Infinity give? Each must give a usable frame, never NaN, which CSS would ignore (so the box would
// collapse to 0 px and the photo would push the page down when it loads). Math.min and Math.max clamp in one line.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function frameRatio(photo: Sized | undefined): number {
  const width = photo?.width;
  const height = photo?.height;
  if (
    !width ||
    !height ||
    !Number.isFinite(width) ||
    !Number.isFinite(height) ||
    width < 0 ||
    height < 0
  ) {
    return TALLEST;
  }
  return Math.min(WIDEST, Math.max(TALLEST, width / height));
}
// └── END ANSWER ▲ ──

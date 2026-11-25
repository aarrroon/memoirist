import type { components } from '../api/schema';
import { toNode, type DocNode } from './tiptap';

type Summary = Pick<
  components['schemas']['EntrySummaryResponse'],
  'title' | 'previewLine' | 'firstMediaKind'
>;

type MediaKind = NonNullable<Summary['firstMediaKind']>;

const MEDIA_LABEL = { PHOTO: 'Photo', PAGE_SCAN: 'Diary page', VOICE_NOTE: 'Voice note' } as const;

/** The server's preview line is VARCHAR(120) (D-24, ContentTextExtractor); the client cuts its own to match. */
export const MAX_PREVIEW_CHARACTERS = 120;

/**
 * How a list names an entry (D-24): its title, else its preview line (the server's first non-heading line), else a
 * label for its first media item. Null when there is nothing at all, which includes every sealed row: the server
 * sends none of the three while it is sealed (D-10), and the caller shows the star and the dates instead.
 */
export function displayTitle(summary: Summary): string | null {
  if (summary.title) return summary.title;
  if (summary.previewLine) return summary.previewLine;
  return summary.firstMediaKind ? MEDIA_LABEL[summary.firstMediaKind] : null;
}

/** One line of a caption, and whether it came from a heading (a template's prompt, D-06). */
type Line = { text: string; heading: boolean };

/** The lines of a caption, in reading order: one per paragraph, heading and hard break, as the server counts them. */
function linesOf(node: DocNode): Line[] {
  if (node.type === 'paragraph' || node.type === 'heading') {
    const isHeading = node.type === 'heading';
    const lines: Line[] = [{ text: '', heading: isHeading }];
    for (const child of node.content) {
      if (child.type === 'hardBreak') lines.push({ text: '', heading: isHeading });
      else if (child.text !== null) {
        // A stray line break inside one text run starts a new line too, as on the server.
        const [first = '', ...rest] = child.text.split(/\r\n|\r|\n/);
        lines[lines.length - 1]!.text += first;
        for (const part of rest) lines.push({ text: part, heading: isHeading });
      }
    }
    return lines;
  }
  // doc, lists, list items and quotes only hold other blocks.
  return node.content.flatMap(linesOf);
}

/** At most 120 characters, counted by code point so an emoji is never cut in half. */
function cut(text: string): string {
  const points = Array.from(text);
  return points.length <= MAX_PREVIEW_CHARACTERS
    ? text
    : points.slice(0, MAX_PREVIEW_CHARACTERS).join('').trimEnd();
}

// TODO(human) PRACTICE: Write previewLine(document): the line a list shows for an entry with no title (D-24). It is
// the first line with any text that isn't a heading, or the first heading when the caption has nothing else, cut
// to 120 characters; null when no line has text. `document` is untrusted JSON from the network.
// Hint: toNode() checks the shape for you, and linesOf() above flattens the tree into lines. Skipping headings is
// the point: an entry started from a template would otherwise be listed under its first prompt. Trim before you
// test for "empty", and keep the first heading you pass in case nothing else turns up (one pass, no second loop).
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function previewLine(document: unknown): string | null {
  const root = toNode(document);
  if (!root) return null;
  let firstHeading: string | null = null;
  for (const line of linesOf(root)) {
    const text = line.text.trim();
    if (text === '') continue;
    if (!line.heading) return cut(text);
    firstHeading ??= text;
  }
  return firstHeading === null ? null : cut(firstHeading);
}
// └── END ANSWER ▲ ──

/** The kind of an entry's first media item, as the server names it in EntrySummary.firstMediaKind (D-24). */
function mediaKind(media: { mediaType: string; purpose: string }): MediaKind {
  if (media.mediaType.startsWith('audio/')) return 'VOICE_NOTE';
  return media.purpose === 'PAGE_SCAN' ? 'PAGE_SCAN' : 'PHOTO';
}

type FullEntry = Pick<
  components['schemas']['EntryResponse'],
  'title' | 'contentJson' | 'isLocked' | 'media'
>;

/**
 * The display title of a whole entry (task 4.10, the week summary's bars): GET /weekly-logs/{id} sends full
 * entries, not summaries, so the preview line and the first media kind are worked out here the way the server
 * works them out for a summary. A sealed entry has none: the caller shows the star and its dates (D-10, D-41).
 */
export function entryDisplayTitle(entry: FullEntry): string | null {
  if (entry.isLocked) return null;
  const first = [...entry.media].sort((a, b) => a.displayOrder - b.displayOrder)[0];
  return displayTitle({
    title: entry.title,
    previewLine: previewLine(entry.contentJson),
    firstMediaKind: first ? mediaKind(first) : null,
  });
}

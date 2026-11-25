import type { components } from '../api/schema';

type Summary = Pick<
  components['schemas']['EntrySummaryResponse'],
  'title' | 'previewLine' | 'firstMediaKind'
>;

const MEDIA_LABEL = { PHOTO: 'Photo', PAGE_SCAN: 'Diary page', VOICE_NOTE: 'Voice note' } as const;

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

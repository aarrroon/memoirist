import type { Media } from '../../api/feed';
import { entry, era, photo, sealedEntry, voiceNote, week } from './feed';

// Test data for the week summary (task 4.10): the round 3 mockup's Grampians week as GET /weekly-logs/{id} sends it.
// It holds a sealed Loot Drop the server has already redacted (D-10). Entries in Recently deleted and other users'
// entries never reach the client (the backend's WeeklyLog tests, task 3.7, prove the server leaves them out), so
// the sealed one is the case the client must handle.

const grampiansTag = { id: 'tag-grampians', name: 'Grampians', source: 'USER' };
const friends = { id: 'tag-friends', name: 'Friends', source: 'USER' };
const outdoors = { id: 'tag-outdoors', name: 'Outdoors', source: 'USER' };

const page = (id: string, entryId: string, order = 0): Media => ({
  ...photo(id, entryId, order),
  purpose: 'PAGE_SCAN',
});
const photos = (entryId: string, count: number) =>
  Array.from({ length: count }, (_, i) => photo(`${entryId}-p${i}`, entryId, i));

const heading = (text: string) => ({
  type: 'heading',
  attrs: { level: 2 },
  content: [{ type: 'text', text }],
});

export const grampiansEra = era('era-gramp', 'Grampians Long Weekend', '2026-10-23', '2026-10-25');
export const brunswickEra = era('era-bruns', 'The Brunswick Years', '2024-03-02', null);

export const grampiansWeek = week(
  'week-gramp',
  '2026-10-19',
  '2026-10-25',
  [grampiansEra, brunswickEra],
  [
    entry('e-packing', '2026-10-19', {
      title: 'Packing list, round two',
      tags: [grampiansTag],
      media: photos('e-packing', 1),
    }),
    // No title: the first line that isn't the template's prompt names it.
    entry('e-halls', '2026-10-23', {
      contentJson: {
        type: 'doc',
        content: [
          heading('Highlights'),
          { type: 'paragraph', content: [{ type: 'text', text: 'Halls Gap, finally' }] },
        ],
      } as never,
      tags: [grampiansTag, friends],
      media: photos('e-halls', 3),
    }),
    sealedEntry('e-sealed', '2026-10-24', '2027-10-24T22:00:00Z'),
    entry('e-sunrise', '2026-10-24', {
      title: 'Sunrise at the Pinnacle',
      tags: [grampiansTag, friends, outdoors],
      media: [...photos('e-sunrise', 4), voiceNote('e-sunrise-v', 'e-sunrise', 4)],
    }),
    // Nothing but a diary page: "Diary page".
    entry('e-page', '2026-10-24', { media: [page('e-page-p', 'e-page')] }),
    // Nothing but a voice note: "Voice note".
    entry('e-voice', '2026-10-25', {
      tags: [grampiansTag, outdoors],
      media: [voiceNote('e-voice-v', 'e-voice')],
    }),
  ],
  'Grampians Trip Week',
);

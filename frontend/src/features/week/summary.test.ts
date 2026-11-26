import { describe, expect, test } from 'vitest';
import { entry, photo, sealedEntry, week } from '../../test/msw/feed';
import { MAX_THUMBNAILS, mediaCounts, summarise } from './summary';
import { grampiansWeek } from '../../test/msw/week';

describe('summarise (task 4.10, D-25)', () => {
  test('counts readable entries, pictures and voice notes; a sealed Loot Drop counts for nothing', () => {
    const summary = summarise(grampiansWeek);
    // Six entries in the week, one of them sealed (D-10).
    expect(summary.entries).toBe(5);
    expect(summary.photos).toBe(1 + 3 + 4 + 1);
    expect(summary.voiceNotes).toBe(2);
    expect(summary.thumbnails).toHaveLength(9);
    expect(summary.thumbnails.map((t) => t.entry.id)).not.toContain('e-sealed');
  });

  test('counts each tag, most used first, then by name', () => {
    expect(summarise(grampiansWeek).tags.map((t) => `${t.name} ${t.count}`)).toEqual([
      'Grampians 4',
      'Friends 2',
      'Outdoors 2',
    ]);
  });

  test('never counts a sealed entry, even one that arrived with content (a server bug)', () => {
    const leaky = {
      ...sealedEntry('s', '2026-10-20', '2027-01-01T00:00:00Z'),
      media: [photo('secret', 's')],
      tags: [{ id: 'tag-secret', name: 'Secret', source: 'USER' }],
    };
    const summary = summarise(week('w', '2026-10-19', '2026-10-25', [], [leaky]));
    expect(summary).toEqual({ entries: 0, photos: 0, voiceNotes: 0, thumbnails: [], tags: [] });
  });

  test('keeps at most ten thumbnails, in the week’s order and each entry’s display order', () => {
    const many = Array.from({ length: 12 }, (_, i) => photo(`m${i}`, 'big', 11 - i));
    const summary = summarise(
      week('w', '2026-10-19', '2026-10-25', [], [entry('big', '2026-10-20', { media: many })]),
    );
    expect(summary.photos).toBe(12);
    expect(summary.thumbnails).toHaveLength(MAX_THUMBNAILS);
    expect(summary.thumbnails[0]!.media.id).toBe('m11');
  });

  test('mediaCounts splits an entry’s pictures from its voice notes', () => {
    expect(mediaCounts(grampiansWeek.entries[3]!)).toEqual({ pictures: 4, voiceNotes: 1 });
  });
});

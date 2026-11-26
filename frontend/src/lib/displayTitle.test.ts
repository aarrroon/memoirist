import { describe, expect, test } from 'vitest';
import { entry, photo, sealedEntry, voiceNote } from '../test/msw/feed';
import { displayTitle, entryDisplayTitle, previewLine } from './displayTitle';

// D-24: how a list names an entry. previewLine must agree with the server's ContentTextExtractor, because the
// timeline (summaries) and the week summary (full entries) name the same entry and must name it the same way.

const p = (...content: unknown[]) => ({ type: 'paragraph', content });
const h = (text: string) => ({
  type: 'heading',
  attrs: { level: 2 },
  content: [{ type: 'text', text }],
});
const t = (text: string) => ({ type: 'text', text });
const docOf = (...content: unknown[]) => ({ type: 'doc', content });

describe('previewLine', () => {
  test('is the first line with text that is not a heading', () => {
    expect(
      previewLine(docOf(h('What triggered the spiral?'), p(), p(t('  The 8:15 was cancelled. ')))),
    ).toBe('The 8:15 was cancelled.');
  });

  test('falls back to the first heading when there is nothing else', () => {
    expect(previewLine(docOf(h('Who was there'), h('The best bit'), p()))).toBe('Who was there');
  });

  test('splits a paragraph at hard breaks and line breaks in a text run', () => {
    expect(previewLine(docOf(p(t('   '), { type: 'hardBreak' }, t('Second line'))))).toBe(
      'Second line',
    );
    expect(previewLine(docOf(h('Prompt\nstill a heading'), p(t('\nAfter a blank'))))).toBe(
      'After a blank',
    );
  });

  test('reads inside lists and quotes', () => {
    const list = { type: 'bulletList', content: [{ type: 'listItem', content: [p(t('Tent'))] }] };
    expect(previewLine(docOf(h('Packing'), list))).toBe('Tent');
    expect(previewLine(docOf({ type: 'blockquote', content: [p(t('Quoted'))] }))).toBe('Quoted');
  });

  test('cuts at 120 code points without splitting an emoji', () => {
    const long = 'a'.repeat(119) + '😀😀';
    expect(Array.from(previewLine(docOf(p(t(long))))!)).toHaveLength(120);
    expect(previewLine(docOf(p(t(long))))!.endsWith('😀')).toBe(true);
  });

  test('is null for no document, an empty one, or something that is not a document', () => {
    expect(previewLine(null)).toBeNull();
    expect(previewLine(docOf(p(), p(t('   '))))).toBeNull();
    expect(previewLine('<p>html</p>')).toBeNull();
    expect(previewLine({ content: [p(t('no type'))] })).toBeNull();
  });
});

describe('entryDisplayTitle (title → first non-heading line → Photo / Diary page / Voice note)', () => {
  test('the title wins', () => {
    expect(
      entryDisplayTitle(
        entry('e', '2026-10-19', {
          title: 'Halls Gap, finally',
          contentJson: docOf(p(t('x'))) as never,
        }),
      ),
    ).toBe('Halls Gap, finally');
  });

  test('then the first line that is not a template prompt', () => {
    expect(
      entryDisplayTitle(
        entry('e', '2026-10-19', {
          contentJson: docOf(h('Highlights'), p(t('Pinnacle at dawn'))) as never,
        }),
      ),
    ).toBe('Pinnacle at dawn');
  });

  test('then a label for the first media item, by display order', () => {
    const pageScan = { ...photo('m2', 'e', 0), purpose: 'PAGE_SCAN' };
    expect(
      entryDisplayTitle(entry('e', '2026-10-19', { media: [photo('m1', 'e', 1), pageScan] })),
    ).toBe('Diary page');
    expect(entryDisplayTitle(entry('e', '2026-10-19', { media: [photo('m1', 'e')] }))).toBe(
      'Photo',
    );
    expect(entryDisplayTitle(entry('e', '2026-10-19', { media: [voiceNote('v', 'e')] }))).toBe(
      'Voice note',
    );
  });

  test('is null for an empty entry and for every sealed one, whatever it holds', () => {
    expect(entryDisplayTitle(entry('e', '2026-10-19'))).toBeNull();
    // A server bug that sent a sealed entry's title must still not name it here (D-10).
    expect(
      entryDisplayTitle({
        ...sealedEntry('s', '2026-10-19', '2027-10-24T22:00:00Z'),
        title: 'Secret',
      }),
    ).toBeNull();
  });

  test('summaries keep their own rule', () => {
    expect(displayTitle({ title: null, previewLine: null, firstMediaKind: 'VOICE_NOTE' })).toBe(
      'Voice note',
    );
  });
});

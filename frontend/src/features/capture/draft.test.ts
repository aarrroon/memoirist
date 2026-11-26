import { afterEach, describe, expect, test } from 'vitest';
import { clearDraft, readDraft, writeDraft, type Draft } from './draft';

// The draft in local storage (D-23): it comes back after a reload, and anything that isn't a draft is ignored.

const draft: Draft = {
  kind: 'entry',
  clientId: '7f6c2a3e-9f61-4f4e-9a52-1e0c3c0b8d11',
  entryId: null,
  entryVersion: null,
  entryDate: '2026-11-20',
  templateId: 'template-night',
  prompts: ['Who was there'],
  title: 'Dumplings',
  content: {
    type: 'doc',
    content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Lygon' }] }],
  },
  tagIds: ['tag-family'],
  savedAt: '2026-11-24T08:41:00.000Z',
};

afterEach(() => localStorage.clear());

describe('the capture draft', () => {
  test('is read back as it was written, and is gone once cleared', () => {
    writeDraft(draft);
    expect(readDraft()).toEqual(draft);
    clearDraft();
    expect(readDraft()).toBeNull();
  });

  test('is ignored when it is not JSON, or not a draft this app understands', () => {
    localStorage.setItem('memoirist.capture-draft', '{not json');
    expect(readDraft()).toBeNull();
    localStorage.setItem('memoirist.capture-draft', JSON.stringify({ ...draft, kind: 'video' }));
    expect(readDraft()).toBeNull();
    localStorage.setItem(
      'memoirist.capture-draft',
      JSON.stringify({ ...draft, entryDate: '24/11/2026' }),
    );
    expect(readDraft()).toBeNull();
    localStorage.setItem(
      'memoirist.capture-draft',
      JSON.stringify({ ...draft, content: { type: 'html', html: '<img onerror=alert(1)>' } }),
    );
    expect(readDraft()).toBeNull();
  });

  test('keeps the prompts of several large templates (more than 20)', () => {
    const many = { ...draft, prompts: Array.from({ length: 45 }, (_, i) => `Prompt ${i + 1}`) };
    writeDraft(many);
    expect(readDraft()).toEqual(many);
  });

  test('drops keys it doesn’t know', () => {
    localStorage.setItem(
      'memoirist.capture-draft',
      JSON.stringify({ ...draft, userId: 'someone-else' }),
    );
    expect(readDraft()).toEqual(draft);
  });
});

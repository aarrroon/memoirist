import type { JSONContent } from '@tiptap/react';
import { describe, expect, test } from 'vitest';
import {
  captionForSave,
  insertTemplateHeadings,
  isBlankDoc,
  removeEmptyPrompts,
} from './templateDoc';

// Templates in the editor (D-06, D-23): prompts go in above the text, and the ones left empty come out on save.

const p = (text?: string): JSONContent =>
  text ? { type: 'paragraph', content: [{ type: 'text', text }] } : { type: 'paragraph' };
const h = (text: string): JSONContent => ({
  type: 'heading',
  attrs: { level: 2 },
  content: [{ type: 'text', text }],
});
const doc = (...content: JSONContent[]): JSONContent => ({ type: 'doc', content });
const prompts = ['Who was there', 'The best bit'];

describe('insertTemplateHeadings', () => {
  test('puts each prompt in as a heading with a line to write on, into an empty editor', () => {
    expect(insertTemplateHeadings(doc(p()), prompts)).toEqual(
      doc(h('Who was there'), p(), h('The best bit'), p()),
    );
    expect(insertTemplateHeadings(null, prompts)).toEqual(
      doc(h('Who was there'), p(), h('The best bit'), p()),
    );
  });

  test('puts the prompts above text already written (G-07)', () => {
    expect(insertTemplateHeadings(doc(p('Dumplings on Lygon.')), prompts)).toEqual(
      doc(h('Who was there'), p(), h('The best bit'), p(), p('Dumplings on Lygon.')),
    );
  });

  test('a template with no prompts leaves an empty editor writable', () => {
    expect(insertTemplateHeadings(doc(p()), [])).toEqual(doc(p()));
  });
});

describe('removeEmptyPrompts', () => {
  test('drops a prompt with nothing under it and keeps an answered one', () => {
    const written = doc(h('Who was there'), p('Mei and Jonno'), h('The best bit'), p(), p());
    expect(removeEmptyPrompts(written, prompts)).toEqual(
      doc(h('Who was there'), p('Mei and Jonno')),
    );
  });

  test('drops a prompt followed straight by the next heading', () => {
    const written = doc(h('Who was there'), h('The best bit'), p('The thermos photo'));
    expect(removeEmptyPrompts(written, prompts)).toEqual(
      doc(h('The best bit'), p('The thermos photo')),
    );
  });

  test('keeps a heading the user wrote themselves, even with nothing under it', () => {
    const written = doc(p('Intro'), h('My own heading'));
    expect(removeEmptyPrompts(written, prompts)).toEqual(written);
  });

  test('counts a list under a prompt as an answer', () => {
    const list: JSONContent = {
      type: 'bulletList',
      content: [{ type: 'listItem', content: [p('Agreeing to go back')] }],
    };
    const written = doc(h('The best bit'), list);
    expect(removeEmptyPrompts(written, prompts)).toEqual(written);
  });
});

describe('captionForSave', () => {
  test('is null when only empty prompts are left, so an empty template prompt is never saved', () => {
    expect(
      captionForSave(doc(h('Who was there'), p(), h('The best bit'), p()), prompts),
    ).toBeNull();
  });

  test('is null for an untouched editor and for no document at all', () => {
    expect(captionForSave(doc(p()), [])).toBeNull();
    expect(captionForSave(null, prompts)).toBeNull();
  });

  test('keeps writing that has no template', () => {
    expect(captionForSave(doc(p('Quiet day.')), [])).toEqual(doc(p('Quiet day.')));
  });
});

test('isBlankDoc sees spaces as blank and a horizontal rule as something', () => {
  expect(isBlankDoc(doc(p('   ')))).toBe(true);
  expect(isBlankDoc(doc({ type: 'horizontalRule' }))).toBe(false);
});

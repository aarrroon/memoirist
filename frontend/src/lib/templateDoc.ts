import type { JSONContent } from '@tiptap/react';

// Templates in the editor (D-06, D-23, task 4.6). Choosing a template puts its prompts in as headings above what is
// already written; saving drops the prompts the user skipped, so an unanswered "What triggered the spiral?" never
// reaches the entry. Both work on TipTap's JSON (D-05), the same document the server stores.

/** The heading level prompts use, and the toolbar's H button (the server allows 1 to 3, D-05). */
export const PROMPT_LEVEL = 2;

const textOf = (node: JSONContent): string =>
  (node.text ?? '') + (node.content ?? []).map(textOf).join('');

const isBlankParagraph = (node: JSONContent) =>
  node.type === 'paragraph' && textOf(node).trim() === '';

const emptyDoc = (): JSONContent => ({ type: 'doc', content: [{ type: 'paragraph' }] });

/**
 * The document with the template's prompts inserted above everything already there (G-07): each prompt is a heading
 * followed by an empty paragraph to write in. A document holding only blank paragraphs is replaced, not kept below.
 */
export function insertTemplateHeadings(doc: JSONContent | null, prompts: string[]): JSONContent {
  const existing = (doc?.content ?? []).filter((node) => !isBlankParagraph(node));
  const inserted = prompts.flatMap<JSONContent>((prompt) => [
    { type: 'heading', attrs: { level: PROMPT_LEVEL }, content: [{ type: 'text', text: prompt }] },
    { type: 'paragraph' },
  ]);
  const content = [...inserted, ...existing];
  return content.length === 0 ? emptyDoc() : { type: 'doc', content };
}

/**
 * Removes every template prompt the user left unanswered: a heading whose text is one of `prompts` and that is
 * followed only by blank paragraphs until the next heading or the end. A heading the user wrote themselves, or a
 * prompt with an answer under it, stays. Blank paragraphs at the end are dropped too.
 */
export function removeEmptyPrompts(doc: JSONContent, prompts: string[]): JSONContent {
  const nodes = doc.content ?? [];
  const promptSet = new Set(prompts.map((p) => p.trim()));
  const kept: JSONContent[] = [];
  let skipUntil = 0;

  const blankAt = (at: number) => {
    const node = nodes[at];
    return node !== undefined && isBlankParagraph(node);
  };

  nodes.forEach((node, i) => {
    if (skipUntil > i) return;
    if (node.type === 'heading' && promptSet.has(textOf(node).trim())) {
      let end = i + 1;
      while (blankAt(end)) end++;
      const next = nodes[end];
      const answered = next !== undefined && next.type !== 'heading';
      if (!answered) {
        skipUntil = end; // drop the prompt and its blank paragraphs
        return;
      }
    }
    kept.push(node);
  });
  while (kept.length > 0 && isBlankParagraph(kept[kept.length - 1] as JSONContent)) kept.pop();
  return { ...doc, content: kept };
}

/** True when the document holds no words: blank paragraphs, or nothing at all. Such a caption is sent as null. */
export function isBlankDoc(doc: JSONContent | null | undefined): boolean {
  if (!doc) return true;
  return textOf(doc).trim() === '' && !(doc.content ?? []).some((n) => n.type === 'horizontalRule');
}

/**
 * What capture sends as `contentJson`: the editor's document with skipped prompts removed (D-06), or null when no
 * words are left, so a photo-only entry has no caption (D-05).
 */
export function captionForSave(doc: JSONContent | null, prompts: string[]): JSONContent | null {
  if (!doc) return null;
  const cleaned = removeEmptyPrompts(doc, prompts);
  return isBlankDoc(cleaned) ? null : cleaned;
}

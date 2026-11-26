// Reading an entry's TipTap JSON (task 4.5, D-05). The document comes from the network, and the generator types it
// as an empty object (TipTapDocument), so every field is checked before it is used: anything that isn't the shape
// below is dropped, never trusted. RichText draws what this returns.

export type DocMark = { type: string; attrs: Record<string, unknown> };
export type DocNode = {
  type: string;
  text: string | null;
  attrs: Record<string, unknown>;
  marks: DocMark[];
  content: DocNode[];
};

const isObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

function toMark(value: unknown): DocMark | null {
  if (!isObject(value) || typeof value.type !== 'string') return null;
  return { type: value.type, attrs: isObject(value.attrs) ? value.attrs : {} };
}

/** A node and its children with every field checked, or null when `value` isn't a node at all. */
export function toNode(value: unknown): DocNode | null {
  if (!isObject(value) || typeof value.type !== 'string') return null;
  return {
    type: value.type,
    text: typeof value.text === 'string' ? value.text : null,
    attrs: isObject(value.attrs) ? value.attrs : {},
    marks: Array.isArray(value.marks) ? value.marks.map(toMark).filter((m) => m !== null) : [],
    content: Array.isArray(value.content)
      ? value.content.map(toNode).filter((n) => n !== null)
      : [],
  };
}

/** True when the document holds some text: an entry with only empty paragraphs has no caption to show. */
export function hasText(document: unknown): boolean {
  const walk = (node: DocNode | null): boolean =>
    !!node && ((node.text !== null && node.text.trim() !== '') || node.content.some(walk));
  return walk(toNode(document));
}

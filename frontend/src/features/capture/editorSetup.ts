import Link from '@tiptap/extension-link';
import { useEditor, type JSONContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { safeHref } from '../../lib/links';

// The entry's text (task 4.6, D-05): TipTap with StarterKit and Link only (frontend.md, Editor and content), giving
// TipTap JSON, never HTML. StarterKit 3 bundles more than the server accepts, so the extras are switched off here:
// the server's allow-list is paragraph, heading 1-3, both lists, blockquote, hard break and horizontal rule, with
// bold, italic, strike, code and link (API_CONTRACT, Content rules). A document that used underline or a code block
// would be refused with 400 on save.

export const extensions = [
  StarterKit.configure({
    heading: { levels: [1, 2, 3] },
    underline: false,
    codeBlock: false,
    // StarterKit 3 includes Link; it is configured on its own below so its rules are in one place.
    link: false,
  }),
  Link.configure({
    // Opening a link while writing would leave the page; links open from the entry, not the editor.
    openOnClick: false,
    autolink: true,
    defaultProtocol: 'https',
    // The same allow-list as the server and the reader (lib/links.ts): http, https and mailto, no control
    // characters. Anything else typed or pasted stays plain text.
    isAllowedUri: (url) => safeHref(url) !== null,
    shouldAutoLink: (url) => safeHref(url) !== null,
    HTMLAttributes: { rel: 'noopener noreferrer nofollow', target: null },
  }),
];

/** The editor's look: entry text in `body`, prompts as `body` 600 (DESIGN.md §4 Write), 8 px between blocks. */
const contentClass =
  'min-h-32 text-body [&_a]:text-accent [&_a]:underline [&_blockquote]:border-l-2 [&_blockquote]:border-line [&_blockquote]:pl-4 [&_h1]:font-strong [&_h2]:font-strong [&_h3]:font-strong [&_hr]:border-line [&_ol]:list-decimal [&_ol]:pl-6 [&_strong]:font-strong [&_ul]:list-disc [&_ul]:pl-6 [&>*+*]:mt-2';

export function useEntryEditor(initial: JSONContent | null, onChange: (doc: JSONContent) => void) {
  return useEditor({
    extensions,
    content: initial ?? undefined,
    // This app renders only in the browser, so the editor can exist on the first render.
    immediatelyRender: true,
    editorProps: {
      attributes: {
        class: contentClass,
        'aria-label': 'Entry text',
        'aria-multiline': 'true',
        role: 'textbox',
      },
    },
    onUpdate: ({ editor }) => onChange(editor.getJSON()),
  });
}

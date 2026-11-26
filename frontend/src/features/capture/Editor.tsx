import { EditorContent, useEditorState, type Editor } from '@tiptap/react';
import { Bold, Heading, Italic, List } from 'lucide-react';
import { useState } from 'react';
import { PROMPT_LEVEL } from '../../lib/templateDoc';

// The toolbar and the editable text (task 4.6). The editor itself is set up in editorSetup.ts (D-05).

/** The toolbar (bold, italic, heading, list: 44 px icon buttons between hairlines) and the text itself. */
export function EntryEditor({
  editor,
  error,
  hint = 'What happened?',
}: {
  editor: Editor;
  error?: string;
  /** The line drawn over the empty editor: the first letter (task 4.16) begins 'Dear future me…'. */
  hint?: string;
}) {
  const [current, setCurrent] = useState(0);
  // useEditorState re-renders only when these answers change, not on every keystroke (TipTap 3).
  const state = useEditorState({
    editor,
    selector: ({ editor: e }) => ({
      bold: e.isActive('bold'),
      italic: e.isActive('italic'),
      heading: e.isActive('heading', { level: PROMPT_LEVEL }),
      list: e.isActive('bulletList'),
      empty: e.isEmpty,
    }),
  });

  const tools = [
    {
      label: 'Bold',
      Icon: Bold,
      on: state.bold,
      run: () => editor.chain().focus().toggleBold().run(),
    },
    {
      label: 'Italic',
      Icon: Italic,
      on: state.italic,
      run: () => editor.chain().focus().toggleItalic().run(),
    },
    {
      label: 'Heading',
      Icon: Heading,
      on: state.heading,
      run: () => editor.chain().focus().toggleHeading({ level: PROMPT_LEVEL }).run(),
    },
    {
      label: 'List',
      Icon: List,
      on: state.list,
      run: () => editor.chain().focus().toggleBulletList().run(),
    },
  ];

  return (
    <div className="flex flex-col gap-2">
      <div
        role="toolbar"
        aria-label="Text style"
        className="flex gap-2 border-y border-line"
        onKeyDown={(event) => {
          // A toolbar is one Tab stop; the arrow keys move between its buttons (WAI-ARIA toolbar pattern).
          if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
          event.preventDefault();
          const step = event.key === 'ArrowRight' ? 1 : tools.length - 1;
          const next = (current + step) % tools.length;
          setCurrent(next);
          event.currentTarget.querySelectorAll('button')[next]?.focus();
        }}
      >
        {tools.map(({ label, Icon, on, run }, index) => (
          <button
            key={label}
            type="button"
            aria-label={label}
            aria-pressed={on}
            tabIndex={index === current ? 0 : -1}
            onFocus={() => setCurrent(index)}
            onClick={run}
            className={
              on
                ? 'flex size-11 items-center justify-center rounded-sm text-accent focus-visible:-outline-offset-2'
                : 'flex size-11 items-center justify-center rounded-sm text-ink focus-visible:-outline-offset-2'
            }
          >
            <Icon size={20} aria-hidden />
          </button>
        ))}
      </div>
      <div className="relative">
        {state.empty && (
          // StarterKit has no placeholder, and only StarterKit and Link are allowed, so the hint is drawn over the
          // empty editor and hidden from screen readers (the editor has its own label).
          <p aria-hidden className="pointer-events-none absolute top-0 left-0 text-body text-ink-2">
            {hint}
          </p>
        )}
        <EditorContent editor={editor} />
      </div>
      {error && <p className="text-small text-danger">{error}</p>}
    </div>
  );
}

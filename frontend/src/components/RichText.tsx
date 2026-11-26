import { Fragment, type ReactNode } from 'react';
import { cn } from '../lib/cn';
import { safeHref } from '../lib/links';
import { hasText, toNode, type DocMark, type DocNode } from '../lib/tiptap';

// An entry's own text, drawn from its TipTap JSON (task 4.5, D-05, non-negotiable 6). React elements are built
// from the nodes, so nothing the user wrote is ever parsed as HTML: no dangerouslySetInnerHTML, no innerHTML.
//
// Why a small renderer of our own and not TipTap's: the feed must not ship the editor (design.md §6, first
// JavaScript 200 KB or less), and TipTap's static renderer is a package that isn't installed (D-13). The server's
// allow-list is short (TECH_STACK, Content), so the whole of it fits in one switch.
//
// lib/tiptap checks every field of the document first. A node or mark the switch doesn't know draws its children
// (or nothing): the server refuses those on save, so meeting one means a newer server, and losing its formatting
// beats losing the text.

type Mark = DocMark;
type Node = DocNode;

/** Wraps a run of text in its marks, outermost first, as TipTap stores them. */
function withMarks(text: ReactNode, marks: Mark[], key: string): ReactNode {
  return marks.reduceRight<ReactNode>((inner, mark, index) => {
    const k = `${key}m${index}`;
    switch (mark.type) {
      case 'bold':
        return (
          <strong key={k} className="font-strong">
            {inner}
          </strong>
        );
      case 'italic':
        return <em key={k}>{inner}</em>;
      case 'strike':
        return <s key={k}>{inner}</s>;
      case 'code':
        return <code key={k}>{inner}</code>;
      case 'link': {
        const href = safeHref(mark.attrs.href);
        // A link the screen won't open is shown as its text, so nothing the user wrote is lost.
        if (!href) return <Fragment key={k}>{inner}</Fragment>;
        return (
          <a
            key={k}
            href={href}
            target="_blank"
            rel="noopener noreferrer nofollow"
            className="text-accent underline"
          >
            {inner}
          </a>
        );
      }
      default:
        return <Fragment key={k}>{inner}</Fragment>;
    }
  }, text);
}

function renderNode(node: Node, key: string, compact: boolean): ReactNode {
  const children = node.content.map((child, index) =>
    renderNode(child, `${key}.${index}`, compact),
  );
  switch (node.type) {
    case 'doc':
      return <Fragment key={key}>{children}</Fragment>;
    case 'text':
      return node.text ? withMarks(node.text, node.marks, key) : null;
    case 'paragraph':
      // A blank line the writer left adds nothing to three clamped lines in the feed.
      if (compact && node.content.length === 0) return null;
      return <p key={key}>{children}</p>;
    case 'heading':
      // A template's prompt (D-06). In a caption it is body 600, not a heading: the feed's outline is weeks and
      // entries, and a caption's headings would break it (DESIGN.md §4 Write: prompts as body 600).
      return (
        <p key={key} className="font-strong">
          {children}
        </p>
      );
    case 'bulletList':
      return (
        <ul key={key} className="list-disc pl-6">
          {children}
        </ul>
      );
    case 'orderedList': {
      const start = typeof node.attrs.start === 'number' ? node.attrs.start : undefined;
      return (
        <ol key={key} start={start} className="list-decimal pl-6">
          {children}
        </ol>
      );
    }
    case 'listItem':
      return <li key={key}>{children}</li>;
    case 'blockquote':
      return (
        <blockquote key={key} className="border-l border-line pl-4">
          {children}
        </blockquote>
      );
    case 'hardBreak':
      return <br key={key} />;
    case 'horizontalRule':
      return compact ? null : <hr key={key} className="border-line" />;
    default:
      return children.length > 0 ? <Fragment key={key}>{children}</Fragment> : null;
  }
}

/**
 * An entry's text, read-only. `clamp` is the feed's caption: `body`, cut after three lines (DESIGN.md §4 Feed), with
 * blank paragraphs and rules left out. Without it every block is drawn, for the entry view (task 4.12).
 */
export function RichText({
  document,
  clamp = false,
  className,
}: {
  document: unknown;
  clamp?: boolean;
  className?: string;
}) {
  const root = toNode(document);
  if (!root || !hasText(document)) return null;
  return (
    <div className={cn(clamp && 'line-clamp-3', className)}>{renderNode(root, 'n', clamp)}</div>
  );
}

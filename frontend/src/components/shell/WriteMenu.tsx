import { BookOpen, Images, Plus, Type } from 'lucide-react';
import { useUiStore, type CaptureKind } from '../../stores/ui';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '../ui/dropdown-menu';

// The one Write button (CLAUDE.md, Design rules; DESIGN.md §4 Shell): a plus, never a pencil. It is a menu button
// (DESIGN.md §8) that offers New entry, Photo dump and Diary page, and choosing one opens that capture view through
// the UI store. The capture sheet itself is task 4.6.

const CHOICES: { kind: CaptureKind; label: string; Icon: typeof Plus }[] = [
  { kind: 'entry', label: 'New entry', Icon: Type },
  { kind: 'dump', label: 'Photo dump', Icon: Images },
  { kind: 'page', label: 'Diary page', Icon: BookOpen },
];

/**
 * `tab`: the centre column of the phone's tab bar, a 48 × 28 accent pill over the label "Write", with the menu
 * above it. `side`: the laptop's full-width accent pill, with the menu below it.
 */
export function WriteMenu({ variant }: { variant: 'tab' | 'side' }) {
  const openCapture = useUiStore((s) => s.openCapture);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        {variant === 'tab' ? (
          <button
            type="button"
            className="flex flex-col items-center justify-center gap-1 text-meta text-ink focus-visible:-outline-offset-2"
          >
            <span className="flex h-7 w-12 items-center justify-center rounded-full bg-accent text-on-accent">
              <Plus size={20} aria-hidden />
            </span>
            Write
          </button>
        ) : (
          <button
            type="button"
            className="flex h-11 w-full items-center justify-center gap-2 rounded-full bg-accent px-4 text-body font-strong text-on-accent"
          >
            <Plus size={20} aria-hidden />
            Write
          </button>
        )}
      </DropdownMenuTrigger>
      <DropdownMenuContent
        side={variant === 'tab' ? 'top' : 'bottom'}
        align={variant === 'tab' ? 'center' : 'start'}
      >
        {CHOICES.map(({ kind, label, Icon }) => (
          <DropdownMenuItem key={kind} onSelect={() => openCapture(kind)}>
            <Icon size={20} aria-hidden className="text-ink-2" />
            {label}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

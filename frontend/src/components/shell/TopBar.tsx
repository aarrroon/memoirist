import { ArrowLeft } from 'lucide-react';
import { Link } from 'react-router';
import { MoonLine } from './MoonLine';
import type { ShellHandle } from './places';

// The phone's top bar (DESIGN.md §4 Shell): 56 px, sticky, `--page`. The Journal, Timeline, Eras and You show the
// moon date line; an inner screen shows a back link that names where it goes, in `body` 600 with an arrow.
// Hidden from `lg`, where the side nav carries the moon line. The background spans the width, but the content sits
// in the same centred 640 px column as <main>, so on a tablet it starts on the screen's gutter (design.md §1).
export function TopBar({ back }: { back: ShellHandle['back'] }) {
  return (
    <header className="sticky top-0 z-10 flex h-14 shrink-0 bg-page lg:hidden">
      <div className="mx-auto flex w-full max-w-160 items-center px-4 md:px-6">
        {back ? (
          <Link to={back.to} className="flex min-h-11 items-center gap-2 text-body font-strong">
            <ArrowLeft size={20} aria-hidden />
            {back.label}
          </Link>
        ) : (
          <MoonLine />
        )}
      </div>
    </header>
  );
}

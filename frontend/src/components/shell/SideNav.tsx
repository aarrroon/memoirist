import { FileText, House, Layers, List, Trash2, User } from 'lucide-react';
import { Link } from 'react-router';
import { MoonLine } from './MoonLine';
import { WriteMenu } from './WriteMenu';
import type { Place } from './places';

// The laptop's side nav (DESIGN.md §3 and §4 Shell): 264 px from `lg`, with the moon line, Write as a full-width
// accent pill, then Journal, Timeline, Eras and Templates, and Recently deleted and You at its foot. The current
// place has a `--surface` fill and 600. The same places as the tab bar, in the same order (design.md §9).

const MAIN = [
  { place: 'journal', to: '/', label: 'Journal', Icon: House },
  { place: 'timeline', to: '/entries', label: 'Timeline', Icon: List },
  { place: 'eras', to: '/eras', label: 'Eras', Icon: Layers },
  { place: 'templates', to: '/templates', label: 'Templates', Icon: FileText },
] as const;

const FOOT = [
  { place: 'deleted', to: '/recently-deleted', label: 'Recently deleted', Icon: Trash2 },
  { place: 'you', to: '/settings', label: 'You', Icon: User },
] as const;

function NavItem({
  to,
  label,
  Icon,
  current,
}: {
  to: string;
  label: string;
  Icon: typeof House;
  current: boolean;
}) {
  return (
    <li>
      <Link
        to={to}
        aria-current={current ? 'page' : undefined}
        className="flex h-11 items-center gap-3 rounded-sm px-3 text-body text-ink-2 aria-[current=page]:bg-surface aria-[current=page]:font-strong aria-[current=page]:text-ink"
      >
        <Icon size={20} aria-hidden />
        {label}
      </Link>
    </li>
  );
}

export function SideNav({ place }: { place: Place }) {
  return (
    <aside className="sticky top-0 hidden h-dvh w-66 shrink-0 flex-col gap-6 border-r border-line px-4 py-8 lg:flex">
      <MoonLine />
      <WriteMenu variant="side" />
      <nav aria-label="Main" className="flex flex-1 flex-col justify-between">
        <ul className="flex flex-col gap-1">
          {MAIN.map((item) => (
            <NavItem key={item.place} {...item} current={item.place === place} />
          ))}
        </ul>
        <ul className="flex flex-col gap-1">
          {FOOT.map((item) => (
            <NavItem key={item.place} {...item} current={item.place === place} />
          ))}
        </ul>
      </nav>
    </aside>
  );
}

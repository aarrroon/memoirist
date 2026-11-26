import { House, Layers, List, User } from 'lucide-react';
import { Link } from 'react-router';
import { WriteMenu } from './WriteMenu';
import type { Tab } from './places';

// The phone's tab bar (DESIGN.md §4 Shell, design.md §9): 64 px, fixed, five equal columns with Write in the
// centre and no wider than its column. Each tab is a 20 px icon over a `meta` label in `--ink-2`; the current one
// is `--ink` and 600, and exactly one is current (aria-current="page"). Hidden from `lg`, where the side nav takes
// over.

function TabLink({
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
    <Link
      to={to}
      aria-current={current ? 'page' : undefined}
      className="flex flex-col items-center justify-center gap-1 text-meta text-ink-2 focus-visible:-outline-offset-2 aria-[current=page]:font-strong aria-[current=page]:text-ink"
    >
      <Icon size={20} aria-hidden />
      {label}
    </Link>
  );
}

export function TabBar({ tab }: { tab: Tab }) {
  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-20 grid h-16 grid-cols-5 border-t border-line bg-page lg:hidden"
    >
      <TabLink to="/" label="Journal" Icon={House} current={tab === 'journal'} />
      <TabLink to="/entries" label="Timeline" Icon={List} current={tab === 'timeline'} />
      <WriteMenu variant="tab" />
      <TabLink to="/eras" label="Eras" Icon={Layers} current={tab === 'eras'} />
      <TabLink to="/settings" label="You" Icon={User} current={tab === 'you'} />
    </nav>
  );
}

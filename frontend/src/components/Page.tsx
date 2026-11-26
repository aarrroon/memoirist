import type { ReactNode } from 'react';

// A screen's frame: its title in `heading` (DESIGN.md §2) and the browser tab's title. React 19 lifts a <title>
// rendered anywhere into <head>, so each screen names itself and a screen reader announces the change.
// `hideTitle` keeps the heading for screen readers only, for a screen whose top bar already says where you are
// (the Journal, under the moon line).
// `action` (task 4.9) is the one button beside the title: New Era, New template.
export function Page({
  title,
  hideTitle = false,
  action,
  children,
}: {
  title: string;
  hideTitle?: boolean;
  action?: ReactNode;
  children?: ReactNode;
}) {
  const heading = <h1 className={hideTitle ? 'sr-only' : 'font-display text-heading'}>{title}</h1>;
  return (
    <section className="flex flex-col gap-4">
      <title>{`${title} · memoirist`}</title>
      {action ? (
        <div className="flex items-center justify-between gap-4">
          {heading}
          {action}
        </div>
      ) : (
        heading
      )}
      {children}
    </section>
  );
}

/** The line a screen shows until the task that builds it lands. */
export function Pending({ children }: { children: ReactNode }) {
  return <p className="text-body text-ink-2">{children}</p>;
}

import { Outlet, ScrollRestoration, useMatches } from 'react-router';
import { SideNav } from './SideNav';
import { TabBar } from './TabBar';
import { TopBar } from './TopBar';
import { CaptureHost } from '../../features/capture/CaptureHost';
import { shellFor, tabFor } from './places';

// The app shell (task 4.3, DESIGN.md §3 and §4 Shell). Phone first: a top bar, the screen, and the tab bar fixed
// at the bottom; one 16 px gutter. From `md` the column is 640 px wide and centred with a 24 px gutter. From `lg`
// the bars give way to a 264 px side nav and a column up to 696 px with a 32 px gutter. The route's `handle` says
// which place is current and whether there's a back link (places.ts).
export function AppShell() {
  const shell = shellFor(useMatches());

  return (
    <div className="lg:flex">
      {/* The first thing a keyboard reaches: past the bars, straight to the screen (DESIGN.md §8). Following a
          #fragment only scrolls and moves the caret, so the click handler moves focus itself; the next Tab then
          starts inside the screen. */}
      <a
        href="#main"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById('main')?.focus();
        }}
        className="sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-30 focus:rounded-full focus:bg-page focus:px-4 focus:py-3 focus:text-body focus:font-strong focus:shadow-float"
      >
        Skip to content
      </a>
      <SideNav place={shell.place} />
      {/* pb-16 keeps the end of the screen clear of the 64 px tab bar on a phone. */}
      <div className="flex min-w-0 flex-1 flex-col pb-16 lg:pb-0">
        <TopBar back={shell.back} />
        <main
          id="main"
          tabIndex={-1}
          className="mx-auto w-full max-w-160 px-4 py-6 outline-none md:px-6 lg:mx-0 lg:max-w-174 lg:px-8 lg:py-8"
        >
          <Outlet />
        </main>
      </div>
      <TabBar tab={tabFor(shell.place)} />
      {/* Capture, opened by Write (task 4.6): a sheet on a phone, a dialog on a laptop, loaded on first use. */}
      <CaptureHost />
      <ScrollRestoration />
    </div>
  );
}

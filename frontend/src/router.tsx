import { createBrowserRouter, type RouteObject } from 'react-router';
import { NotFoundPage, RouteErrorPage } from './components/NotFoundPage';
import { AppShell } from './components/shell/AppShell';
import type { ShellHandle } from './components/shell/places';
import { RecentlyDeletedPage } from './features/deleted/RecentlyDeletedPage';
import { ErasPage } from './features/eras/ErasPage';
import { FeedPage } from './features/feed/FeedPage';
import { SettingsPage } from './features/settings/SettingsPage';
import { TagsPage } from './features/tags/TagsPage';
import { TemplatesPage } from './features/templates/TemplatesPage';
import { TimelinePage } from './features/timeline/TimelinePage';
import { WeekSummaryPage } from './features/week/WeekSummaryPage';

// The routes (task 4.3, DESIGN.md §6). One layout route draws the shell; every screen renders in its <Outlet />.
// Each screen's `handle` says where it sits in the navigation (places.ts): which tab is current, and on a phone
// whether the top bar shows the moon line or a back link. `satisfies` checks the handle's shape here, where the
// route is written, while React Router itself only sees `unknown`.
export const routes: RouteObject[] = [
  {
    path: '/',
    Component: AppShell,
    errorElement: <RouteErrorPage />,
    children: [
      { index: true, Component: FeedPage, handle: { place: 'journal' } satisfies ShellHandle },
      {
        path: 'entries',
        Component: TimelinePage,
        handle: { place: 'timeline' } satisfies ShellHandle,
      },
      {
        path: 'weeks/:weekId',
        Component: WeekSummaryPage,
        handle: { place: 'journal', back: { to: '/', label: 'Journal' } } satisfies ShellHandle,
      },
      { path: 'eras', Component: ErasPage, handle: { place: 'eras' } satisfies ShellHandle },
      {
        path: 'templates',
        Component: TemplatesPage,
        handle: {
          place: 'templates',
          back: { to: '/settings', label: 'You' },
        } satisfies ShellHandle,
      },
      {
        path: 'tags',
        Component: TagsPage,
        handle: { place: 'you', back: { to: '/settings', label: 'You' } } satisfies ShellHandle,
      },
      { path: 'settings', Component: SettingsPage, handle: { place: 'you' } satisfies ShellHandle },
      {
        path: 'recently-deleted',
        Component: RecentlyDeletedPage,
        handle: { place: 'deleted', back: { to: '/settings', label: 'You' } } satisfies ShellHandle,
      },
      // No handle: the shell falls back to Journal with a back link (places.ts, FALLBACK_SHELL).
      { path: '*', Component: NotFoundPage },
    ],
  },
];

/** The app's router, made once in main.tsx, outside React, as React Router's data mode expects. */
export function createAppRouter() {
  return createBrowserRouter(routes);
}

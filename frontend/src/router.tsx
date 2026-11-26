import { createBrowserRouter, type RouteObject } from 'react-router';
import { ScreenLoading } from './components/LoadState';
import { NotFoundPage, RouteErrorPage } from './components/NotFoundPage';
import { AppShell } from './components/shell/AppShell';
import type { ShellHandle } from './components/shell/places';
import { FeedPage } from './features/feed/FeedPage';

// The routes (task 4.3, DESIGN.md §6). One layout route draws the shell; every screen renders in its <Outlet />.
// Each screen's `handle` says where it sits in the navigation (places.ts): which tab is current, and on a phone
// whether the top bar shows the moon line or a back link. `satisfies` checks the handle's shape here, where the
// route is written, while React Router itself only sees `unknown`.
//
// Task 4.12: every screen but the Journal is loaded on first visit with React Router's `lazy`, so the feed's first
// JavaScript stays under design.md §6's 200 KB gzip (it was 199.42 kB after task 4.11). The router fetches a
// screen's chunk before it commits the navigation, so the old screen stays up meanwhile instead of a Suspense
// fallback flashing in. `handle` stays static: the shell reads it from the match before the chunk arrives.
// On a cold load of an inner screen the router is not yet initialised; each lazy route's hydrateFallbackElement
// lets the shell draw at once with ScreenLoading in the column (without it React Router draws nothing at all).
// The object form loads only `Component`; each import names the screen's export, so nothing needs a default export.
//
// A function, not a constant: React Router writes the loaded Component into the route objects it was given and drops
// their `lazy`, so a second router built from the same objects (every test renders its own) found no route for
// /settings and answered 404. Each router gets fresh objects.
export function createRoutes(): RouteObject[] {
  return [
    {
      path: '/',
      Component: AppShell,
      errorElement: <RouteErrorPage />,
      children: [
        { index: true, Component: FeedPage, handle: { place: 'journal' } satisfies ShellHandle },
        {
          path: 'entries',
          hydrateFallbackElement: <ScreenLoading />,
          lazy: {
            Component: async () => (await import('./features/timeline/TimelinePage')).TimelinePage,
          },
          handle: { place: 'timeline' } satisfies ShellHandle,
        },
        {
          // Task 4.8: a sealed Loot Drop's page, or the entry once it can be read (DESIGN.md §6).
          path: 'entries/:entryId',
          hydrateFallbackElement: <ScreenLoading />,
          lazy: {
            Component: async () => (await import('./features/lootdrop/EntryPage')).EntryPage,
          },
          handle: { place: 'journal', back: { to: '/', label: 'Journal' } } satisfies ShellHandle,
        },
        {
          path: 'weeks/:weekId',
          hydrateFallbackElement: <ScreenLoading />,
          lazy: {
            Component: async () =>
              (await import('./features/week/WeekSummaryPage')).WeekSummaryPage,
          },
          handle: { place: 'journal', back: { to: '/', label: 'Journal' } } satisfies ShellHandle,
        },
        {
          path: 'eras',
          hydrateFallbackElement: <ScreenLoading />,
          lazy: { Component: async () => (await import('./features/eras/ErasPage')).ErasPage },
          handle: { place: 'eras' } satisfies ShellHandle,
        },
        {
          // Task 4.9: one Era, its title band, its weeks and the Eras inside it (DESIGN.md §6 `#era`).
          path: 'eras/:eraId',
          hydrateFallbackElement: <ScreenLoading />,
          lazy: { Component: async () => (await import('./features/eras/EraPage')).EraPage },
          handle: { place: 'eras', back: { to: '/eras', label: 'Eras' } } satisfies ShellHandle,
        },
        {
          path: 'templates',
          hydrateFallbackElement: <ScreenLoading />,
          lazy: {
            Component: async () =>
              (await import('./features/templates/TemplatesPage')).TemplatesPage,
          },
          handle: {
            place: 'templates',
            back: { to: '/settings', label: 'You' },
          } satisfies ShellHandle,
        },
        {
          path: 'tags',
          hydrateFallbackElement: <ScreenLoading />,
          lazy: { Component: async () => (await import('./features/tags/TagsPage')).TagsPage },
          handle: { place: 'you', back: { to: '/settings', label: 'You' } } satisfies ShellHandle,
        },
        {
          path: 'settings',
          hydrateFallbackElement: <ScreenLoading />,
          lazy: {
            Component: async () => (await import('./features/settings/SettingsPage')).SettingsPage,
          },
          handle: { place: 'you' } satisfies ShellHandle,
        },
        {
          // Task 4.12: entries wait here 30 days to be restored or erased (D-30, F-18).
          path: 'recently-deleted',
          hydrateFallbackElement: <ScreenLoading />,
          lazy: {
            Component: async () =>
              (await import('./features/deleted/RecentlyDeletedPage')).RecentlyDeletedPage,
          },
          handle: {
            place: 'deleted',
            back: { to: '/settings', label: 'You' },
          } satisfies ShellHandle,
        },
        // No handle: the shell falls back to Journal with a back link (places.ts, FALLBACK_SHELL).
        { path: '*', Component: NotFoundPage },
      ],
    },
  ];
}

/** The app's router, made once in main.tsx, outside React, as React Router's data mode expects. */
export function createAppRouter() {
  return createBrowserRouter(createRoutes());
}

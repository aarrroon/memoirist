import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { createQueryClient } from '../api/queryClient';
import { createRoutes } from '../router';

// Renders a component the way main.tsx does, with a fresh QueryClient per test so no cached data leaks from
// one test into the next. The app's retry policy is kept, so tests see the same error timing as users.
export function renderWithQuery(ui: ReactElement) {
  const queryClient = createQueryClient();
  return {
    queryClient,
    ...render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>),
  };
}

// Renders the whole app (the shell and its routes, task 4.3) at an address, with an in-memory history instead of
// the browser's, so a test can start anywhere and read `router.state.location` after a click.
export function renderApp(path = '/') {
  const router = createMemoryRouter(createRoutes(), { initialEntries: [path] });
  return { router, ...renderWithQuery(<RouterProvider router={router} />) };
}

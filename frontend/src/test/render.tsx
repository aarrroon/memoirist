import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { createQueryClient } from '../api/queryClient';

// Renders a component the way main.tsx does, with a fresh QueryClient per test so no cached data leaks from
// one test into the next. The app's retry policy is kept, so tests see the same error timing as users.
export function renderWithQuery(ui: ReactElement) {
  const queryClient = createQueryClient();
  return {
    queryClient,
    ...render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>),
  };
}

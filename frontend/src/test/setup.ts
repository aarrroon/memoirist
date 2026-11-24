import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './msw/server';

// A request with no handler fails the test, so no test can reach a real server by accident.
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));

afterEach(() => {
  // Vitest globals are off, so React Testing Library can't register its own cleanup.
  cleanup();
  server.resetHandlers();
});

afterAll(() => server.close());

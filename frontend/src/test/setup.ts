import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './msw/server';

// jsdom has no layout, so it doesn't implement scrolling and prints "Not implemented" when React Router's
// <ScrollRestoration /> (the shell, task 4.3) scrolls to the top after a navigation. Nothing scrolls in a test.
window.scrollTo = () => undefined;

// A request with no handler fails the test, so no test can reach a real server by accident.
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));

afterEach(() => {
  // Vitest globals are off, so React Testing Library can't register its own cleanup.
  cleanup();
  server.resetHandlers();
});

afterAll(() => server.close());

import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './msw/server';

// jsdom has no layout, so it doesn't implement scrolling and prints "Not implemented" when React Router's
// <ScrollRestoration /> (the shell, task 4.3) scrolls to the top after a navigation. Nothing scrolls in a test.
window.scrollTo = () => undefined;

// Embla (the swipe, task 4.5) watches its slides with ResizeObserver and IntersectionObserver, which jsdom lacks.
// These do nothing, as nothing has a size in jsdom: the swipe's own keyboard handling is what the tests drive. The
// IntersectionObserver stub never reports an entry, so the feed's foot still loads more only through its button.
class InertObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords() {
    return [];
  }
}
globalThis.ResizeObserver ??= InertObserver as unknown as typeof ResizeObserver;
globalThis.IntersectionObserver ??= InertObserver as unknown as typeof IntersectionObserver;

// Embla also reads its breakpoint options through matchMedia, which jsdom lacks. No query matches in a test, so
// reduced motion is off and the swipe glides (not that anything moves in jsdom).
// typeof, not `'matchMedia' in window`: under Vitest the `in` check let Embla meet an undefined matchMedia.
if (typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (query: string) => inertMediaQuery(query),
  });
}
function inertMediaQuery(query: string): MediaQueryList {
  return {
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  };
}

// jsdom has no media playback: pressing Play on a voice note would print "Not implemented".
window.HTMLMediaElement.prototype.play = function play() {
  this.dispatchEvent(new Event('play'));
  return Promise.resolve();
};
window.HTMLMediaElement.prototype.pause = function pause() {
  this.dispatchEvent(new Event('pause'));
};

// A request with no handler fails the test, so no test can reach a real server by accident.
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));

afterEach(() => {
  // Vitest globals are off, so React Testing Library can't register its own cleanup.
  cleanup();
  server.resetHandlers();
});

afterAll(() => server.close());

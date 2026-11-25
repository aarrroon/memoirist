import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './msw/server';

// findBy and waitFor give up after 1 s by default. In the full run 34 files share the CPU, and a screen that
// renders in 50 ms alone (an MSW answer, then a lazy chunk) can take over a second, so 1 to 6 random tests failed
// with "Unable to find …" while every file passed alone (task 4.9). 5 s still fails a test that never renders.
configure({ asyncUtilTimeout: 5_000 });

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

// jsdom has no object URLs. Capture (task 4.6) shows each picked photo from one; a test only needs a string.
// (Vitest's URL passes them to Node's, which has them; this is for when it doesn't.)
let objectUrls = 0;
stub(URL, 'createObjectURL', () => `blob:test/${++objectUrls}`);
stub(URL, 'revokeObjectURL', () => undefined);

// jsdom lacks these, and Radix's popover, Vaul and Sonner call them (task 4.6).
stub(Element.prototype, 'scrollIntoView', () => undefined);
stub(Element.prototype, 'hasPointerCapture', () => false);
stub(Element.prototype, 'releasePointerCapture', () => undefined);
stub(Element.prototype, 'setPointerCapture', () => undefined);

/** Adds a do-nothing method only where jsdom has none, so a real one is never replaced. */
function stub(target: object, name: string, value: (...args: never[]) => unknown) {
  if (!(name in target))
    Object.defineProperty(target, name, { configurable: true, writable: true, value });
}

// jsdom has no media playback: pressing Play on a voice note would print "Not implemented".
window.HTMLMediaElement.prototype.play = function play() {
  this.dispatchEvent(new Event('play'));
  return Promise.resolve();
};
// Nor does it load media: a voice note's first Play reads its length (lib/audioDuration.ts), so loading answers at
// once with the metadata event; jsdom's duration stays NaN, which the player treats as not known.
window.HTMLMediaElement.prototype.load = function load() {
  queueMicrotask(() => this.dispatchEvent(new Event('loadedmetadata')));
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

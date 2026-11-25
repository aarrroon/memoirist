import { useSyncExternalStore } from 'react';

// Appearance (task 4.9, D-41): Match phone, Light or Dark. It is kept on the device, in local storage, because it is
// about this screen, not about the user: a phone can be dark while a laptop is light. index.html applies the same
// rule before the first paint (its inline script reads the same key), so a reload never flashes the wrong mode; this
// module applies it again the moment the choice changes, so the whole app switches at once.

export type Appearance = 'system' | 'light' | 'dark';

/** The local storage key. index.html's inline script reads it too: change both together. */
export const APPEARANCE_KEY = 'memoirist.appearance';

const listeners = new Set<() => void>();

/** The stored choice, or Match phone when there is none, it is unreadable, or storage is blocked. */
export function readAppearance(): Appearance {
  try {
    const stored = window.localStorage.getItem(APPEARANCE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : 'system';
  } catch {
    return 'system';
  }
}

function prefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

/** The theme the tokens use (index.css, [data-theme='dark']): the choice, or the phone's own setting. */
export function resolveTheme(choice: Appearance, phoneIsDark: boolean): 'light' | 'dark' {
  if (choice === 'system') return phoneIsDark ? 'dark' : 'light';
  return choice;
}

/** Puts the theme on <html>, where every token reads it, so the whole app changes in one style recalculation. */
export function applyAppearance(choice: Appearance = readAppearance()): void {
  document.documentElement.dataset.theme = resolveTheme(choice, prefersDark());
}

/** Keeps the choice on this device and applies it. Match phone removes the key, so the phone decides again. */
export function setAppearance(choice: Appearance): void {
  try {
    if (choice === 'system') window.localStorage.removeItem(APPEARANCE_KEY);
    else window.localStorage.setItem(APPEARANCE_KEY, choice);
  } catch {
    // Storage blocked (a private window): the choice still applies until the page is closed.
  }
  applyAppearance(choice);
  listeners.forEach((listener) => listener());
}

// TODO(human) PRACTICE: Write subscribe and useAppearance: a React hook that returns the stored Appearance and
// re-renders when it changes, in this tab (setAppearance) or in another tab of the app (the `storage` event).
// Hint: React 19's useSyncExternalStore(subscribe, getSnapshot) is made for state that lives outside React. The
// snapshot must be cheap and return the same value while nothing changed (a string is fine, a new object each call
// is not). subscribe must return the function that unsubscribes, and remove exactly what it added.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
function subscribe(onChange: () => void): () => void {
  const onStorage = (event: StorageEvent) => {
    if (event.key === APPEARANCE_KEY || event.key === null) onChange();
  };
  listeners.add(onChange);
  window.addEventListener('storage', onStorage);
  return () => {
    listeners.delete(onChange);
    window.removeEventListener('storage', onStorage);
  };
}

/** The Appearance chosen on this device; re-renders when it changes here or in another tab. */
export function useAppearance(): Appearance {
  return useSyncExternalStore(subscribe, readAppearance, () => 'system');
}
// └── END ANSWER ▲ ──

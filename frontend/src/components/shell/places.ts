import type { UIMatch } from 'react-router';

// Where each screen sits in the app's navigation (DESIGN.md §4 Shell, design.md §9). Each route in router.tsx
// declares it in its `handle`, so the route table is the one place that says "the week summary belongs to the
// Journal tab and goes back to it". The shell reads it; it never guesses from the URL.

/** The places in the laptop's side nav, in its order. The phone's tab bar folds Templates and Recently deleted into You. */
export type Place = 'journal' | 'timeline' | 'eras' | 'templates' | 'deleted' | 'you';

/** The phone's tab bar: Journal, Timeline, (Write), Eras, You. */
export type Tab = 'journal' | 'timeline' | 'eras' | 'you';

export interface ShellHandle {
  /** The place marked as current in the side nav, and through tabFor() in the tab bar. Exactly one, always. */
  place: Place;
  /** An inner screen's back link on a phone, naming where it goes (DESIGN.md §4). Without one, the moon line. */
  back?: { to: string; label: string };
}

/** Templates, Recently deleted and the tags page are reached from You on a phone, so You stays current there. */
export function tabFor(place: Place): Tab {
  return place === 'templates' || place === 'deleted' ? 'you' : place;
}

/** What the shell shows when no route says (an unknown address): Journal current, and a way back to it. */
export const FALLBACK_SHELL: ShellHandle = {
  place: 'journal',
  back: { to: '/', label: 'Journal' },
};

function isShellHandle(handle: unknown): handle is ShellHandle {
  return typeof handle === 'object' && handle !== null && 'place' in handle;
}

// TODO(human) PRACTICE: Write shellFor(matches): the ShellHandle for the screen on show.
// useMatches() gives every route that matched the URL, outermost first: for /weeks/abc that is the shell's own
// route, then the week route. Any of them may have a `handle`, typed `unknown` because React Router can't know it.
// Hint: the innermost route knows most about the screen, so search from the end, and narrow `unknown` with the
// type guard above instead of a cast. When nothing matches (an unknown address), fall back to FALLBACK_SHELL.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function shellFor(matches: readonly UIMatch[]): ShellHandle {
  for (let i = matches.length - 1; i >= 0; i--) {
    const handle = matches[i]?.handle;
    if (isShellHandle(handle)) return handle;
  }
  return FALLBACK_SHELL;
}
// └── END ANSWER ▲ ──

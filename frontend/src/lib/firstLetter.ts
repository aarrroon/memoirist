// Whether the first letter was skipped (task 4.16, D-33, F-01: "It isn't offered again"). Kept in this browser's
// local storage: Phase 1 has no account and no field for it on the server, and it is a convenience, not a record.
// Writing the letter needs no mark of its own: a sealed letter is an entry, so the first-run screen no longer shows.

const STORAGE_KEY = 'memoirist.first-letter';

export function firstLetterSkipped(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'skipped';
  } catch {
    // Storage blocked (a private window): the letter is offered, and Skip still hides it for this visit.
    return false;
  }
}

export function skipFirstLetter(): void {
  try {
    localStorage.setItem(STORAGE_KEY, 'skipped');
  } catch {
    // Nothing to keep it in; the screen hides the card anyway.
  }
}

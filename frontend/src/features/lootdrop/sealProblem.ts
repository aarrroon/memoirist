import { ApiProblem, problemMessage } from '../../api/problem';

/**
 * A failed seal in the user's words (F-06, F-07, task 4.8). A day earlier than the current opening is 409 entry-sealed,
 * which problem.ts already says kindly; a day past the 100-year limit is a 400; anything else (the network, the
 * server) changed nothing and can be tried again: "Not sealed, try again" (F-06), or for an extension, the seal as it
 * was still holds.
 */
export function sealProblem(error: unknown, extending = false): string {
  if (error instanceof ApiProblem && error.kind === 'entry-sealed') return problemMessage(error);
  if (error instanceof ApiProblem && error.kind === 'validation-failed')
    return 'That day is too far ahead. Pick one within 100 years.';
  return extending ? 'The seal didn’t change. Try again.' : 'Not sealed. Try again.';
}

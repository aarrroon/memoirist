import type { components } from './schema';

// The one place an API error becomes something the UI can use (task 4.2). Every hook throws an ApiProblem,
// so a screen reads `problem.kind` and `problemMessage(problem)` and never parses a response itself.

/** Field name to message: the generated Problem schema's shape (an empty object when there are none). */
export type FieldErrors = NonNullable<components['schemas']['Problem']['fieldErrors']>;

/** The `type` suffixes in API_CONTRACT.md §Errors, plus two the client makes up for itself. */
export type ProblemKind =
  | 'validation-failed'
  | 'malformed-request'
  | 'not-found'
  | 'method-not-allowed'
  | 'not-acceptable'
  | 'entry-sealed'
  | 'entry-stale'
  | 'conflict'
  | 'payload-too-large'
  | 'unsupported-media-type'
  | 'storage-unavailable'
  | 'music-search-unavailable'
  | 'internal-error'
  // No answer at all: the network is down, or the dev proxy found no backend.
  | 'network'
  // An answer that isn't one of our problems (an HTML error page, an unknown suffix, about:blank).
  | 'unknown';

const KNOWN_KINDS: ReadonlySet<string> = new Set<ProblemKind>([
  'validation-failed',
  'malformed-request',
  'not-found',
  'method-not-allowed',
  'not-acceptable',
  'entry-sealed',
  'entry-stale',
  'conflict',
  'payload-too-large',
  'unsupported-media-type',
  'storage-unavailable',
  'music-search-unavailable',
  'internal-error',
]);

const TYPE_PREFIX = 'urn:memoirist:problem:';

export class ApiProblem extends Error {
  readonly kind: ProblemKind;
  /** The HTTP status, or 0 when no response came back. */
  readonly status: number;
  /** Field name to message, only on validation-failed (API_CONTRACT §Errors). */
  readonly fieldErrors: Readonly<FieldErrors>;

  constructor(kind: ProblemKind, status: number, detail: string, fieldErrors: FieldErrors = {}) {
    // `detail` is a sentence the server wrote for the caller and never repeats what was sent, so it is safe
    // as the Error's message. It is still never logged with entry text around it (CLAUDE.md, mistakes table).
    super(detail);
    this.name = 'ApiProblem';
    this.kind = kind;
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

// TODO(human) PRACTICE: Write toApiProblem(body, status). `body` is whatever openapi-fetch parsed from an
// error response: usually a Problem, but it can be an empty string, HTML text from a proxy, or JSON from
// something that isn't our API. Return an ApiProblem whose kind is the suffix after TYPE_PREFIX when it is one
// of KNOWN_KINDS, and 'unknown' otherwise. Keep fieldErrors only when it is an object of strings.
// Hint: the generated Problem type says what the server promises, but at runtime this is untrusted input:
// narrow `unknown` step by step (isRecord, typeof) instead of casting. A missing detail falls back to a fixed
// sentence, never to the raw body, which could hold anything the server or a proxy echoed.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function toApiProblem(body: unknown, status: number): ApiProblem {
  if (!isRecord(body)) {
    return new ApiProblem('unknown', status, `The server answered ${status}.`);
  }
  const type = typeof body.type === 'string' ? body.type : '';
  const suffix = type.startsWith(TYPE_PREFIX) ? type.slice(TYPE_PREFIX.length) : '';
  const kind: ProblemKind = KNOWN_KINDS.has(suffix) ? (suffix as ProblemKind) : 'unknown';
  const detail =
    typeof body.detail === 'string' && body.detail !== ''
      ? body.detail
      : `The server answered ${status}.`;

  const fieldErrors: FieldErrors = {};
  if (isRecord(body.fieldErrors)) {
    for (const [field, message] of Object.entries(body.fieldErrors)) {
      if (typeof message === 'string') fieldErrors[field] = message;
    }
  }
  return new ApiProblem(kind, status, detail, fieldErrors);
}
// └── END ANSWER ▲ ──

/**
 * Returns `data`, or throws the error as an ApiProblem. Every query and mutation function ends with it, so a
 * failed request reaches TanStack Query as a thrown error (its `error` state) and never as `undefined` data.
 */
export function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (result.error !== undefined || !result.response.ok) {
    throw toApiProblem(result.error, result.response.status);
  }
  // openapi-fetch leaves `data` undefined for a 204; those endpoints return void, so T is undefined too.
  return result.data as T;
}

/** A request that got no response at all (offline, DNS, the dev proxy refusing). */
export function networkProblem(): ApiProblem {
  return new ApiProblem('network', 0, 'The request did not reach the server.');
}

/** Whether a retry could help: no response, or a server-side failure. A 4xx answers the same way again. */
export function isRetryable(error: unknown): boolean {
  if (!(error instanceof ApiProblem)) return false;
  return error.kind === 'network' || error.status >= 500;
}

// What the user reads. The server's `detail` is written for developers and in English only, so screens show
// these instead; a form shows `fieldErrors` beside its fields (.claude/rules/frontend.md, Errors).
const MESSAGES: Record<ProblemKind, string> = {
  'validation-failed': 'Some details need another look.',
  'malformed-request': 'Something went wrong sending that. Try again.',
  'not-found': 'This isn’t here any more.',
  'method-not-allowed': 'Something went wrong. Try again.',
  'not-acceptable': 'Something went wrong. Try again.',
  // Also sent when a client moves a sealed entry's opening date earlier (API_CONTRACT §Errors), so the
  // sentence covers both cases (task 4.2 review).
  'entry-sealed': 'This Loot Drop is sealed. Its opening date can only move later.',
  'entry-stale': 'This entry changed somewhere else. Reload it to see the latest version.',
  conflict: 'That already exists.',
  // A file over 25 MB and entry text over 200 KB both send this (API_CONTRACT §Errors), so it names neither.
  'payload-too-large': 'That’s too large to save.',
  'unsupported-media-type': 'That file type isn’t supported.',
  'storage-unavailable': 'Photos can’t be reached right now. Try again soon.',
  'music-search-unavailable':
    'Song search isn’t available right now. Paste a Spotify link instead.',
  'internal-error': 'Something went wrong on our side. Try again soon.',
  network: 'Can’t reach memoirist. Check your connection and try again.',
  unknown: 'Something went wrong. Try again.',
};

export function problemMessage(error: unknown): string {
  return error instanceof ApiProblem ? MESSAGES[error.kind] : MESSAGES.unknown;
}

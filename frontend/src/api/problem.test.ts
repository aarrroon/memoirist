import { describe, expect, test } from 'vitest';
import {
  ApiProblem,
  isRetryable,
  networkProblem,
  problemMessage,
  toApiProblem,
  unwrap,
} from './problem';

describe('toApiProblem', () => {
  test('reads the suffix, status, detail and field errors of one of our problems', () => {
    const problem = toApiProblem(
      {
        type: 'urn:memoirist:problem:validation-failed',
        title: 'Validation failed',
        status: 400,
        detail: 'entryDate must not be after today',
        instance: '/api/v1/entries',
        timestamp: '2026-10-25T02:30:00Z',
        fieldErrors: { entryDate: 'must not be after today' },
      },
      400,
    );

    expect(problem).toBeInstanceOf(ApiProblem);
    expect(problem.kind).toBe('validation-failed');
    expect(problem.status).toBe(400);
    expect(problem.message).toBe('entryDate must not be after today');
    expect(problem.fieldErrors).toEqual({ entryDate: 'must not be after today' });
  });

  test.each([
    ['entry-sealed', 409],
    ['entry-stale', 409],
    ['not-found', 404],
    ['payload-too-large', 413],
    ['music-search-unavailable', 503],
  ])('knows %s', (suffix, status) => {
    const problem = toApiProblem({ type: `urn:memoirist:problem:${suffix}`, detail: 'x' }, status);

    expect(problem.kind).toBe(suffix);
  });

  test.each([
    ['an HTML page from a proxy', '<html><body>502 Bad Gateway</body></html>'],
    ['an empty body', ''],
    ['no body', undefined],
    ['a JSON array', ['not', 'a', 'problem']],
    ["Spring's about:blank", { type: 'about:blank', detail: 'Gone' }],
    ['a suffix the client does not know', { type: 'urn:memoirist:problem:brand-new', detail: 'x' }],
    ["someone else's problem type", { type: 'urn:other:problem:not-found', detail: 'x' }],
  ])('turns %s into an unknown problem', (_, body) => {
    const problem = toApiProblem(body, 502);

    expect(problem.kind).toBe('unknown');
    expect(problem.status).toBe(502);
  });

  test('never takes its message from a body that is not a problem', () => {
    const problem = toApiProblem('<html>secret stack trace</html>', 500);

    expect(problem.message).toBe('The server answered 500.');
  });

  test('keeps only string field errors', () => {
    const problem = toApiProblem(
      {
        type: 'urn:memoirist:problem:validation-failed',
        detail: 'x',
        fieldErrors: { size: 'must be at most 50', page: 3, nested: { a: 'b' } },
      },
      400,
    );

    expect(problem.fieldErrors).toEqual({ size: 'must be at most 50' });
  });
});

describe('unwrap', () => {
  test('returns the data of a success', () => {
    const data = unwrap({ data: { ok: true }, response: new Response(null, { status: 200 }) });

    expect(data).toEqual({ ok: true });
  });

  test('throws the error of a failure as an ApiProblem', () => {
    const call = () =>
      unwrap({
        error: { type: 'urn:memoirist:problem:entry-sealed', detail: 'The entry is sealed.' },
        response: new Response(null, { status: 409 }),
      });

    expect(call).toThrow(ApiProblem);
    expect(call).toThrow('The entry is sealed.');
  });
});

describe('retries and messages', () => {
  test('only a missing answer or a server failure is worth retrying', () => {
    expect(isRetryable(networkProblem())).toBe(true);
    expect(isRetryable(new ApiProblem('internal-error', 500, 'x'))).toBe(true);
    expect(isRetryable(new ApiProblem('storage-unavailable', 503, 'x'))).toBe(true);
    expect(isRetryable(new ApiProblem('not-found', 404, 'x'))).toBe(false);
    expect(isRetryable(new ApiProblem('entry-sealed', 409, 'x'))).toBe(false);
    expect(isRetryable(new Error('a bug in our own code'))).toBe(false);
  });

  test('a sealed entry gets a friendly sentence, not the server detail', () => {
    const problem = toApiProblem(
      { type: 'urn:memoirist:problem:entry-sealed', detail: 'Entry is sealed until unlockDate.' },
      409,
    );

    expect(problemMessage(problem)).toBe(
      'This Loot Drop is sealed. Its opening date can only move later.',
    );
  });

  test('too large names neither cause, since a file and entry text both send it', () => {
    const problem = toApiProblem(
      { type: 'urn:memoirist:problem:payload-too-large', detail: 'Content is over 200 KB.' },
      413,
    );

    expect(problem.kind).toBe('payload-too-large');
    expect(problemMessage(problem)).toBe('That’s too large to save.');
  });

  test('anything that is not an ApiProblem gets the general sentence', () => {
    expect(problemMessage(new TypeError('x is undefined'))).toBe(
      'Something went wrong. Try again.',
    );
  });
});

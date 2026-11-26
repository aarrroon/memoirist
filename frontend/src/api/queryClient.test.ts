import { expect, test } from 'vitest';
import { ApiProblem, networkProblem } from './problem';
import { createQueryClient, STALE_TIME_MS } from './queryClient';

const queries = () => createQueryClient().getDefaultOptions().queries ?? {};

test('cached data goes stale within 30 minutes, well before a 60-minute presigned URL expires', () => {
  expect(queries().staleTime).toBe(STALE_TIME_MS);
  expect(STALE_TIME_MS).toBeLessThanOrEqual(30 * 60 * 1000);
});

test('queries retry a network failure or a 5xx at most twice, and never a 4xx', () => {
  const retry = queries().retry as (failureCount: number, error: unknown) => boolean;

  expect(retry(0, networkProblem())).toBe(true);
  expect(retry(1, new ApiProblem('internal-error', 500, 'x'))).toBe(true);
  expect(retry(2, new ApiProblem('internal-error', 500, 'x'))).toBe(false);
  expect(retry(0, new ApiProblem('not-found', 404, 'x'))).toBe(false);
  expect(retry(0, new ApiProblem('entry-sealed', 409, 'x'))).toBe(false);
});

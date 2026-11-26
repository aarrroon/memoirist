import { http, HttpResponse } from 'msw';
import { afterEach, expect, test, vi } from 'vitest';
import { server } from '../test/msw/server';
import { api, DEV_USER_ID } from './client';
import { ApiProblem } from './problem';

afterEach(() => {
  vi.unstubAllEnvs();
});

function captureDevHeader() {
  const seen: (string | null)[] = [];
  server.use(
    http.get('*/api/v1/users/me', ({ request }) => {
      seen.push(request.headers.get('X-Dev-User-Id'));
      return HttpResponse.json({});
    }),
  );
  return seen;
}

test('in development every request names the seeded dev user', async () => {
  vi.stubEnv('DEV', true);
  const seen = captureDevHeader();

  await api.GET('/api/v1/users/me');

  expect(seen).toEqual([DEV_USER_ID]);
});

test('outside development no request carries X-Dev-User-Id (D-11)', async () => {
  vi.stubEnv('DEV', false);
  const seen = captureDevHeader();

  await api.GET('/api/v1/users/me');

  expect(seen).toEqual([null]);
});

test('a request that gets no answer fails with a network problem', async () => {
  server.use(http.get('*/api/v1/users/me', () => HttpResponse.error()));

  const failure: unknown = await api.GET('/api/v1/users/me').then(
    () => null,
    (error: unknown) => error,
  );

  expect(failure).toBeInstanceOf(ApiProblem);
  expect((failure as ApiProblem).kind).toBe('network');
  expect((failure as ApiProblem).status).toBe(0);
});

test('a request cancelled on purpose stays an AbortError, so TanStack Query can tell it apart', async () => {
  server.use(
    http.get('*/api/v1/users/me', async () => {
      await new Promise((resolve) => setTimeout(resolve, 1000));
      return HttpResponse.json({});
    }),
  );
  const controller = new AbortController();

  const pending = api.GET('/api/v1/users/me', { signal: controller.signal });
  controller.abort();
  const failure: unknown = await pending.then(
    () => null,
    (error: unknown) => error,
  );

  expect(failure).not.toBeInstanceOf(ApiProblem);
  expect((failure as Error).name).toBe('AbortError');
});

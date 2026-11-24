import { http, HttpResponse } from 'msw';
import { expect, test, vi } from 'vitest';
import { server } from './msw/server';

// These prove the mock server is wired up as setup.ts says, before any feature test depends on it.
// The /api/v1/example path is made up: it stands for any endpoint a component would call.

test('a handler added by a test answers its request', async () => {
  server.use(http.get('http://localhost/api/v1/example', () => HttpResponse.json({ ok: true })));

  const response = await fetch('http://localhost/api/v1/example');

  expect(await response.json()).toEqual({ ok: true });
});

test('a request with no handler is stopped by MSW, not sent to the network', async () => {
  const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

  const failure: unknown = await fetch('http://localhost/api/v1/example').then(
    () => null,
    (error: unknown) => error,
  );

  // Nothing listens on localhost:80 here, so a request let through would fail too. Checking the cause and
  // MSW's message proves it was the 'error' policy in setup.ts that stopped it.
  expect(failure).toBeInstanceOf(TypeError);
  expect(String((failure as TypeError).cause)).toContain('[MSW]');
  expect(consoleError).toHaveBeenCalledWith(
    expect.stringContaining('without a matching request handler'),
  );
  consoleError.mockRestore();
});

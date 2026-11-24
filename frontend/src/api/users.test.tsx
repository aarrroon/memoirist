import { renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { expect, test } from 'vitest';
import { devUser } from '../test/msw/handlers';
import { server } from '../test/msw/server';
import { ApiProblem } from './problem';
import { createQueryClient } from './queryClient';
import { queryKeys } from './queryKeys';
import { useMe } from './users';

function setup() {
  const queryClient = createQueryClient();
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { queryClient, ...renderHook(() => useMe(), { wrapper }) };
}

test('useMe returns the current user, typed from the generated schema', async () => {
  const { result } = setup();

  await waitFor(() => expect(result.current.isSuccess).toBe(true));

  expect(result.current.data).toEqual(devUser);
});

test('useMe caches the user under the me key', async () => {
  const { result, queryClient } = setup();

  await waitFor(() => expect(result.current.isSuccess).toBe(true));

  expect(queryClient.getQueryData(queryKeys.me())).toEqual(devUser);
});

test('a 404 for a user who is not there fails at once, as an ApiProblem, without retrying', async () => {
  let calls = 0;
  server.use(
    http.get('*/api/v1/users/me', () => {
      calls += 1;
      return HttpResponse.json(
        {
          type: 'urn:memoirist:problem:not-found',
          title: 'Not found',
          status: 404,
          detail: 'User not found.',
          instance: '/api/v1/users/me',
          timestamp: '2026-11-24T11:53:09Z',
        },
        { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
      );
    }),
  );
  const { result } = setup();

  await waitFor(() => expect(result.current.isError).toBe(true));

  expect(result.current.error).toBeInstanceOf(ApiProblem);
  expect((result.current.error as ApiProblem).kind).toBe('not-found');
  expect(calls).toBe(1);
});

test('every key starts with the root, so one invalidation reaches them all', () => {
  expect(queryKeys.me().slice(0, queryKeys.all.length)).toEqual([...queryKeys.all]);
});

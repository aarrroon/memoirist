import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { expect, test } from 'vitest';
import App from './App';
import { server } from './test/msw/server';
import { renderWithQuery } from './test/render';

test('the starter page names the app', () => {
  renderWithQuery(<App />);

  expect(screen.getByRole('heading', { level: 1, name: 'memoirist' })).toBeInTheDocument();
});

test('it says it is loading until the user arrives, then greets them by name', async () => {
  renderWithQuery(<App />);

  expect(screen.getByRole('status')).toHaveTextContent('Opening your journal…');
  expect(await screen.findByText('Welcome back, Dev User.')).toBeInTheDocument();
  expect(screen.queryByRole('status')).not.toBeInTheDocument();
});

test('a failed request shows a friendly message, never the server detail', async () => {
  server.use(
    http.get('*/api/v1/users/me', () =>
      HttpResponse.json(
        {
          type: 'urn:memoirist:problem:not-found',
          title: 'Not found',
          status: 404,
          detail: 'User not found.',
          instance: '/api/v1/users/me',
          timestamp: '2026-11-24T11:53:09Z',
        },
        { status: 404 },
      ),
    ),
  );

  renderWithQuery(<App />);

  expect(await screen.findByRole('alert')).toHaveTextContent('This isn’t here any more.');
  expect(screen.queryByText(/User not found/)).not.toBeInTheDocument();
});

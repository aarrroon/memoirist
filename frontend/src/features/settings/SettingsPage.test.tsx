import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import type { Me } from '../../api/users';
import { APPEARANCE_KEY } from '../../lib/appearance';
import { devUser } from '../../test/msw/handlers';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';

// Task 4.9: You (settings), against MSW. GET /users/me answers with whatever the last PATCH saved, like the server.

// 18:30 on Wednesday 25 November 2026 in Melbourne, 07:30 the same morning in London.
const NOW = new Date('2026-11-25T07:30:00Z');
let me: Me;
let patches: unknown[];

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
  me = { ...devUser };
  patches = [];
  window.localStorage.clear();
  server.use(
    http.get('*/api/v1/users/me', () => HttpResponse.json(me)),
    http.patch('*/api/v1/users/me', async ({ request }) => {
      const change = (await request.json()) as Partial<Me>;
      patches.push(change);
      me = { ...me, ...change };
      return HttpResponse.json(me);
    }),
    http.get('*/api/v1/tags', () => HttpResponse.json([])),
    http.get('*/api/v1/templates', () => HttpResponse.json([])),
  );
});

afterEach(() => {
  vi.useRealTimers();
});

describe('You', () => {
  test('shows the name, the timezone as a place, and the birthday', async () => {
    renderApp('/settings');
    expect(
      await screen.findByRole('button', { name: 'Display name: Dev User. Change' }),
    ).toBeVisible();
    expect(screen.getByRole('button', { name: 'Timezone: Melbourne. Change' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Birthday: Not set. Change' })).toBeVisible();
  });

  test('changing the timezone changes the times on screen', async () => {
    const user = userEvent.setup();
    renderApp('/settings');
    // The moon line greets in the user's zone: 18:30 in Melbourne.
    // (The phone's top bar and the laptop's side nav both carry the line.)
    expect(await screen.findAllByText('Wednesday at dusk')).not.toHaveLength(0);
    await user.click(screen.getByRole('button', { name: /^Timezone/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Timezone' });
    await user.selectOptions(within(dialog).getByLabelText('Timezone'), 'Europe/London');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(patches).toEqual([{ timezone: 'Europe/London' }]));
    // 07:30 in London.
    expect(await screen.findAllByText('Wednesday morning')).not.toHaveLength(0);
    expect(screen.queryByText('Wednesday at dusk')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Timezone: London. Change' })).toBeVisible();
  });

  test('a new display name is trimmed; an empty one is refused before sending', async () => {
    const user = userEvent.setup();
    renderApp('/settings');
    await user.click(await screen.findByRole('button', { name: /^Display name/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Display name' });
    const field = within(dialog).getByLabelText('Display name');
    await user.clear(field);
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(within(dialog).getByText('A name is 1 to 100 characters.')).toBeVisible();
    expect(patches).toHaveLength(0);
    await user.type(field, '  Sam ');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(patches).toEqual([{ displayName: 'Sam' }]));
    expect(await screen.findByRole('button', { name: 'Display name: Sam. Change' })).toHaveFocus();
  });

  test('a birthday after today is refused; one is saved, shown without its year, and can be removed', async () => {
    const user = userEvent.setup();
    renderApp('/settings');
    await user.click(await screen.findByRole('button', { name: /^Birthday/ }));
    let dialog = await screen.findByRole('dialog', { name: 'Birthday' });
    await user.type(within(dialog).getByLabelText('Birthday'), '2026-11-26');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(within(dialog).getByText('Pick a day from 1900 to today.')).toBeVisible();
    await user.clear(within(dialog).getByLabelText('Birthday'));
    await user.type(within(dialog).getByLabelText('Birthday'), '1994-08-12');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(
      await screen.findByRole('button', { name: 'Birthday: 12 August. Change' }),
    ).toBeVisible();

    await user.click(screen.getByRole('button', { name: /^Birthday/ }));
    dialog = await screen.findByRole('dialog', { name: 'Birthday' });
    await user.click(within(dialog).getByRole('button', { name: 'Remove' }));
    await waitFor(() => expect(patches.at(-1)).toEqual({ birthday: null }));
    expect(await screen.findByRole('button', { name: 'Birthday: Not set. Change' })).toBeVisible();
  });

  test('Appearance switches the whole app at once and is kept on the device', async () => {
    const user = userEvent.setup();
    renderApp('/settings');
    const group = await screen.findByRole('radiogroup', { name: 'Appearance' });
    expect(within(group).getByRole('radio', { name: 'Match phone' })).toBeChecked();
    await user.click(within(group).getByRole('radio', { name: 'Dark' }));
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(window.localStorage.getItem(APPEARANCE_KEY)).toBe('dark');
    // Arrow keys move the choice, as with any radio group.
    await user.keyboard('{ArrowLeft}');
    expect(within(group).getByRole('radio', { name: 'Light' })).toBeChecked();
    expect(document.documentElement.dataset.theme).toBe('light');
    await user.click(within(group).getByRole('radio', { name: 'Match phone' }));
    expect(window.localStorage.getItem(APPEARANCE_KEY)).toBeNull();
  });
});

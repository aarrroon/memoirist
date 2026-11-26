import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, test } from 'vitest';
import type { Template } from '../../api/templates';
import { template } from '../../test/msw/eras';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';

// Task 4.9: the user's templates and the starter gallery (D-06), against MSW. The list is a little server: POST
// adds to it, PUT replaces, DELETE removes, so each test sees the list refetch after a change.
let own: Template[];
let requests: { method: string; body: unknown }[];

beforeEach(() => {
  own = [template('t-trip', 'Trip day', ['Best moment', 'What I want to remember'])];
  requests = [];
  server.use(
    http.get('*/api/v1/templates', () => HttpResponse.json(own)),
    http.post('*/api/v1/templates', async ({ request }) => {
      const body = (await request.json()) as { id: string; name: string; subheadings: string[] };
      requests.push({ method: 'POST', body });
      const made = template(body.id, body.name, body.subheadings);
      own = [...own, made];
      return HttpResponse.json(made, { status: 201 });
    }),
    http.put('*/api/v1/templates/:id', async ({ request, params }) => {
      const body = (await request.json()) as { name: string; subheadings: string[] };
      requests.push({ method: 'PUT', body });
      own = own.map((t) => (t.id === params.id ? { ...t, ...body, updatedAt: 'later' } : t));
      return HttpResponse.json(own.find((t) => t.id === params.id));
    }),
    http.delete('*/api/v1/templates/:id', ({ params }) => {
      requests.push({ method: 'DELETE', body: params.id });
      own = own.filter((t) => t.id !== params.id);
      return new HttpResponse(null, { status: 204 });
    }),
  );
});

describe('Templates', () => {
  test('lists yours, and offers only the starters you don’t have', async () => {
    renderApp('/templates');
    const yours = await screen.findByRole('region', { name: 'Yours' });
    expect(within(yours).getByRole('button', { name: /Trip day/ })).toHaveTextContent(
      'Best moment · What I want to remember',
    );
    const gallery = screen.getByRole('region', { name: 'Starter gallery' });
    expect(
      within(gallery)
        .getAllByRole('button')
        .map((b) => b.getAttribute('aria-label')),
    ).toEqual([
      'Add Spiralling check-in',
      'Add Night out',
      'Add Letter to future me',
      'Add My week',
    ]);
  });

  test('Add makes an editable copy in one tap', async () => {
    const user = userEvent.setup();
    renderApp('/templates');
    await user.click(await screen.findByRole('button', { name: 'Add Spiralling check-in' }));
    expect(await screen.findByText('Spiralling check-in is in your templates now.')).toBeVisible();
    expect(requests[0]).toMatchObject({
      method: 'POST',
      body: {
        name: 'Spiralling check-in',
        subheadings: ['What is on my mind', 'What I can control', 'One kind thing for tomorrow'],
      },
    });
    // It is the user's own now: listed under Yours, gone from the gallery, and it opens to be edited.
    const yours = screen.getByRole('region', { name: 'Yours' });
    await user.click(await within(yours).findByRole('button', { name: /Spiralling check-in/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit template' });
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Spiralling check-in');
    expect(
      screen.queryByRole('button', { name: 'Add Spiralling check-in' }),
    ).not.toBeInTheDocument();
  });

  test('editing sends the whole list, in order, without empty prompts', async () => {
    const user = userEvent.setup();
    renderApp('/templates');
    await user.click(await screen.findByRole('button', { name: /Trip day/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit template' });
    await user.click(within(dialog).getByRole('button', { name: 'Remove prompt 1' }));
    await user.click(within(dialog).getByRole('button', { name: 'Add a prompt' }));
    await user.type(within(dialog).getByLabelText('Prompt 2'), 'Who I met');
    await user.click(within(dialog).getByRole('button', { name: 'Add a prompt' }));
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(requests).toHaveLength(1));
    expect(requests[0]).toEqual({
      method: 'PUT',
      body: { name: 'Trip day', subheadings: ['What I want to remember', 'Who I met'] },
    });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: /Trip day/ })).toHaveTextContent(
      'What I want to remember · Who I met',
    );
  });

  test('a new template needs a name, and Delete asks first', async () => {
    const user = userEvent.setup();
    renderApp('/templates');
    await user.click(await screen.findByRole('button', { name: 'New' }));
    const dialog = await screen.findByRole('dialog', { name: 'New template' });
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(
      within(dialog).getByText('Give the template a name of at most 100 characters.'),
    ).toBeVisible();
    expect(requests).toHaveLength(0);
    await user.keyboard('{Escape}');

    await user.click(await screen.findByRole('button', { name: /Trip day/ }));
    await user.click(await screen.findByRole('button', { name: 'Delete' }));
    const ask = await screen.findByRole('alertdialog', { name: 'Delete this template?' });
    await user.click(within(ask).getByRole('button', { name: 'Delete template' }));
    await waitFor(() => expect(requests).toEqual([{ method: 'DELETE', body: 't-trip' }]));
    // Trip day is a starter, so with the user's copy gone the gallery offers it again.
    expect(await screen.findByRole('button', { name: 'Add Trip day' })).toBeVisible();
  });
});

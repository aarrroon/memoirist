import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { brunswick, detailOf, eraOf, eraWeek, grampians, sampleEras } from '../../test/msw/eras';
import { photo, withCopies } from '../../test/msw/feed';
import { feedQuery } from '../../api/feed';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';

// Task 4.9: the Eras list, an Era page and the Era form, against MSW. The weeks an Era page shows come from the
// server already filtered (D-22): sealed entries, entries in Recently deleted and entries outside its dates are
// neither listed nor counted there (backend EraWeeksTest). The client must only show what it was sent.

// 18:30 on Wednesday 25 November 2026 in Melbourne. Only Date is faked, so MSW's and React's timers still run.
const NOW = new Date('2026-11-25T07:30:00Z');

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
  server.use(http.get('*/api/v1/eras', () => HttpResponse.json(sampleEras)));
});

afterEach(() => {
  vi.useRealTimers();
});

const weeks = Array.from({ length: 25 }, (_, i) => {
  const start = new Date(Date.UTC(2026, 10, 16) - i * 7 * 86_400_000);
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  return eraWeek(`w${i}`, iso(start), iso(new Date(start.getTime() + 6 * 86_400_000)), (i % 3) + 1);
});

describe('Eras list', () => {
  test('lists each Era with its dates and length, Eras inside another under it', async () => {
    renderApp('/eras');
    const rows = await screen.findAllByRole('link', { name: /Years|Weekend|Japan|Uni/ });
    expect(rows.map((row) => row.textContent)).toEqual([
      'TThe Brunswick Years2 Mar 2024 – now · 2 years, 8 months',
      'GGrampians Long Weekend23–25 Oct 2026 · 3 days',
      'JJapan, finally5–19 Apr 2025 · 15 days',
      'UUni Days25 Feb 2019 – 24 Nov 2023 · 4 years, 9 months',
    ]);
    // Grampians and Japan sit inside Brunswick: indented, the others not.
    expect(rows[1]).toHaveClass('pl-6');
    expect(rows[2]).toHaveClass('pl-6');
    expect(rows[0]).not.toHaveClass('pl-6');
  });

  test('New Era checks the dates, then creates it with a client id and opens it', async () => {
    const user = userEvent.setup();
    const bodies: unknown[] = [];
    server.use(
      http.post('*/api/v1/eras', async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        bodies.push(body);
        return HttpResponse.json(eraOf(String(body.id), 'Tasmania', '2026-11-03', '2026-11-07'), {
          status: 201,
        });
      }),
      http.get('*/api/v1/eras/:id', ({ params }) =>
        HttpResponse.json(
          detailOf(eraOf(String(params.id), 'Tasmania', '2026-11-03', '2026-11-07'), []),
        ),
      ),
    );
    const { router } = renderApp('/eras');
    await user.click(await screen.findByRole('button', { name: 'New Era' }));
    const dialog = await screen.findByRole('dialog', { name: 'New Era' });
    await user.type(within(dialog).getByLabelText('Name'), 'Tasmania');
    await user.type(within(dialog).getByLabelText('First day'), '2026-11-07');
    await user.type(within(dialog).getByLabelText('Last day'), '2026-11-03');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(
      await within(dialog).findByText('The last day can’t be before the first.'),
    ).toBeVisible();
    expect(bodies).toHaveLength(0);

    await user.clear(within(dialog).getByLabelText('First day'));
    await user.type(within(dialog).getByLabelText('First day'), '2026-11-03');
    await user.clear(within(dialog).getByLabelText('Last day'));
    await user.type(within(dialog).getByLabelText('Last day'), '2026-11-07');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0]).toMatchObject({
      title: 'Tasmania',
      startDate: '2026-11-03',
      endDate: '2026-11-07',
      pinnedInFeed: false,
    });
    expect((bodies[0] as { id: string }).id).toMatch(/^[0-9a-f-]{36}$/);
    await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/eras\/[0-9a-f-]{36}$/));
  });
});

describe('Era covers use the smaller copies (D-43, task 4.18)', () => {
  test('the list draws a cover from its thumbnail, and an Era with no cover keeps its initial', async () => {
    const cover = withCopies(photo('media-cover', 'entry-cover'));
    server.use(
      http.get('*/api/v1/eras', () =>
        HttpResponse.json([
          { ...grampians, cover },
          // The server sends cover: null while the cover's entry is sealed or in Recently deleted (D-36).
          { ...brunswick, cover: null },
        ]),
      ),
    );
    renderApp('/eras');

    const gramp = await screen.findByRole('link', { name: /Grampians Long Weekend/ });
    const tile = gramp.querySelector('img')!;
    expect(tile).toHaveAttribute('src', cover.thumbnailUrl);
    expect(tile).toHaveAttribute('loading', 'lazy');
    expect(tile).toHaveAttribute('alt', '');
    const bru = screen.getByRole('link', { name: /The Brunswick Years/ });
    expect(bru.querySelector('img')).toBeNull();
    expect(bru).toHaveTextContent(/^TThe Brunswick Years/);
  });

  test('a cover without a thumbnail falls back to the next size up, never down', async () => {
    const screenOnly = withCopies(photo('media-cover', 'entry-cover'), ['screen']);
    server.use(
      http.get('*/api/v1/eras', () => HttpResponse.json([{ ...grampians, cover: screenOnly }])),
    );
    renderApp('/eras');
    const gramp = await screen.findByRole('link', { name: /Grampians Long Weekend/ });
    expect(gramp.querySelector('img')).toHaveAttribute('src', screenOnly.screenUrl);
  });

  test('the Era page draws its cover from the screen copy, or the original when it has none', async () => {
    const cover = withCopies(photo('media-cover', 'entry-cover'));
    server.use(
      http.get('*/api/v1/eras/era-bru', () =>
        HttpResponse.json(detailOf({ ...brunswick, cover }, weeks)),
      ),
    );
    const { unmount } = renderApp('/eras/era-bru');
    await screen.findByRole('heading', { level: 1, name: 'The Brunswick Years' });
    expect(document.querySelector('img[fetchpriority="high"]')).toHaveAttribute(
      'src',
      cover.screenUrl,
    );
    unmount();

    const old = photo('media-old', 'entry-old');
    server.use(
      http.get('*/api/v1/eras/era-bru', () =>
        HttpResponse.json(detailOf({ ...brunswick, cover: old }, weeks)),
      ),
    );
    renderApp('/eras/era-bru');
    await screen.findByRole('heading', { level: 1, name: 'The Brunswick Years' });
    await waitFor(() =>
      expect(document.querySelector('img[fetchpriority="high"]')).toHaveAttribute(
        'src',
        old.fileUrl,
      ),
    );
  });
});

describe('Era page', () => {
  beforeEach(() => {
    server.use(
      http.get('*/api/v1/eras/era-bru', () => HttpResponse.json(detailOf(brunswick, weeks))),
    );
  });

  test('shows the band, its weeks as a contents list and the Eras inside it', async () => {
    const user = userEvent.setup();
    renderApp('/eras/era-bru');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'The Brunswick Years' }),
    ).toBeVisible();
    expect(screen.getByText('2 Mar 2024 – now · 2 years, 8 months')).toBeVisible();
    expect(
      screen.getByText('Five of us, one bathroom, a lemon tree that never fruits.'),
    ).toBeVisible();
    // 25 weeks, and their counts added up: 9 weeks of 1, 8 of 2 and 8 of 3.
    expect(screen.getByText('25').nextSibling).toHaveTextContent('weeks');
    expect(screen.getByText('49').nextSibling).toHaveTextContent('entries');
    const contents = screen.getByRole('region', { name: 'Its weeks' });
    expect(within(contents).getAllByRole('link')).toHaveLength(20);
    expect(within(contents).getByRole('link', { name: '16–22 November, 1 entry' })).toHaveAttribute(
      'href',
      '/weeks/w0',
    );
    await user.click(within(contents).getByRole('button', { name: 'Show 5 earlier weeks' }));
    expect(within(contents).getAllByRole('link')).toHaveLength(25);
    const inside = screen.getByRole('region', { name: 'Eras inside this one' });
    expect(
      within(inside)
        .getAllByRole('link')
        .map((link) => link.getAttribute('href')),
    ).toEqual(['/eras/era-gra', '/eras/era-jap']);
  });

  test('an Era with no week to show says so instead of listing empty weeks', async () => {
    server.use(http.get('*/api/v1/eras/era-gra', () => HttpResponse.json(detailOf(grampians, []))));
    renderApp('/eras/era-gra');
    expect(await screen.findByText(/No entries in these dates yet/)).toBeVisible();
    expect(screen.getAllByText('0').map((figure) => figure.nextSibling?.textContent)).toEqual([
      'weeks',
      'entries',
    ]);
  });

  test('Edit sends a full replace, keeps the soundtrack, and marks the feed for refetching', async () => {
    const user = userEvent.setup();
    let put: Record<string, unknown> | null = null;
    let feedCalls = 0;
    server.use(
      http.get('*/api/v1/feed', () => {
        feedCalls += 1;
        return HttpResponse.json({ items: [], page: 0, size: 10, totalItems: 0, totalPages: 0 });
      }),
      http.put('*/api/v1/eras/era-bru', async ({ request }) => {
        put = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...brunswick, ...put });
      }),
    );
    const { queryClient } = renderApp('/eras/era-bru');
    // Load the feed once, as the Journal tab would have, so there is something to refetch.
    await queryClient.prefetchInfiniteQuery(feedQuery());
    const before = feedCalls;
    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Era' });
    await user.clear(within(dialog).getByLabelText('First day'));
    await user.type(within(dialog).getByLabelText('First day'), '2024-01-15');
    await user.click(within(dialog).getByRole('checkbox', { name: /Show first in the feed/ }));
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(put).not.toBeNull());
    expect(put).toEqual({
      title: 'The Brunswick Years',
      description: 'Five of us, one bathroom, a lemon tree that never fruits.',
      startDate: '2024-01-15',
      pinnedInFeed: true,
      soundtrack: 'spotify:track:4uLU6hMCjMI75M1A2tKUQC',
    });
    // The feed isn't on screen, so it is marked stale rather than fetched now: opening the Journal refetches it,
    // and the server regroups the weeks by the new dates and pin (D-22).
    await waitFor(() =>
      expect(queryClient.getQueryState(feedQuery().queryKey)?.isInvalidated).toBe(true),
    );
    expect(feedCalls).toBe(before);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  test('a field the server refuses is named in the app’s own words', async () => {
    const user = userEvent.setup();
    server.use(
      http.put('*/api/v1/eras/era-bru', () =>
        HttpResponse.json(
          {
            type: 'urn:memoirist:problem:validation-failed',
            title: 'Bad',
            status: 400,
            fieldErrors: { endDate: 'must not be before startDate' },
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderApp('/eras/era-bru');
    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Era' });
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(
      await within(dialog).findByText('The last day can’t be before the first.'),
    ).toBeVisible();
    expect(within(dialog).queryByText('must not be before startDate')).not.toBeInTheDocument();
  });

  test('Delete asks first, then deletes only the Era and goes back to the list', async () => {
    const user = userEvent.setup();
    let deleted = false;
    server.use(
      http.delete('*/api/v1/eras/era-bru', () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { router } = renderApp('/eras/era-bru');
    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    await user.click(await screen.findByRole('button', { name: 'Delete' }));
    const ask = await screen.findByRole('alertdialog', { name: 'Delete this Era?' });
    expect(within(ask).getByText(/Its weeks and their entries stay/)).toBeVisible();
    expect(deleted).toBe(false);
    await user.click(within(ask).getByRole('button', { name: 'Delete Era' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/eras'));
    expect(deleted).toBe(true);
  });

  test('Escape closes the form without saving', async () => {
    const user = userEvent.setup();
    renderApp('/eras/era-bru');
    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    await screen.findByRole('dialog', { name: 'Edit Era' });
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Edit' })).toHaveFocus();
  });
});

import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { act, renderHook, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import type { Week } from '../../api/feed';
import { useDeleteEntry, useSeal } from '../../api/lootDrops';
import type { ReactNode } from 'react';
import { entry, photo, sealedEntry, week } from '../../test/msw/feed';
import { server } from '../../test/msw/server';
import { grampiansWeek } from '../../test/msw/week';
import { renderApp } from '../../test/render';

// Task 4.10: the week summary at /weeks/:weekId against MSW (D-25, F-15). The week holds a sealed Loot Drop the
// server has already redacted (D-10). Entries in Recently deleted and other users' entries never reach the client:
// GET /weekly-logs/{id} leaves them out (backend WeeklyLog tests, task 3.7), so the client is tested with what the
// server sends.

// 18:30 on Wednesday 25 November 2026 in Melbourne. Only Date is faked, so MSW's and React's timers still run.
const NOW = new Date('2026-11-25T07:30:00Z');

/** Serves `data` as the week, and records every PUT and POST the page sends. */
function serveWeek(data: Week) {
  const sent: { method: string; path: string; body: unknown }[] = [];
  let current = data;
  server.use(
    http.get('*/api/v1/weekly-logs/:id', ({ params }) =>
      params.id === current.id
        ? HttpResponse.json(current)
        : HttpResponse.json(
            {
              type: 'urn:memoirist:problem:not-found',
              title: 'Not found',
              status: 404,
              detail: 'Server detail that must never be shown.',
            },
            { status: 404 },
          ),
    ),
    http.put('*/api/v1/weekly-logs/:id', async ({ request }) => {
      const body = (await request.json()) as { title?: string; soundtrack?: string };
      sent.push({ method: 'PUT', path: new URL(request.url).pathname, body });
      current = { ...current, title: body.title ?? null, soundtrack: body.soundtrack ?? null };
      return HttpResponse.json(current);
    }),
    http.post('*/api/v1/weekly-logs/:id/:action', ({ request, params }) => {
      sent.push({ method: 'POST', path: new URL(request.url).pathname, body: null });
      current = { ...current, isClosed: params.action === 'close' };
      return HttpResponse.json(current);
    }),
  );
  return sent;
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
});

afterEach(() => {
  vi.useRealTimers();
});

const bars = async () => {
  const list = await screen.findByRole('heading', { name: 'Entries' });
  return within(list.closest('section')!).getAllByRole('link');
};

describe('week summary', () => {
  test('a mixed week: name, dates and Eras, stats, thumbnails, tag counts, then one bar per entry in day order', async () => {
    serveWeek(grampiansWeek);
    renderApp('/weeks/week-gramp');

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Grampians Trip Week' }),
    ).toBeVisible();
    expect(screen.getByText(/^19–25 Oct 2026, in/)).toBeVisible();
    expect(screen.getByRole('link', { name: 'Grampians Long Weekend' })).toHaveAttribute(
      'href',
      '/eras/era-gramp',
    );
    expect(screen.getByRole('link', { name: 'The Brunswick Years' })).toHaveAttribute(
      'href',
      '/eras/era-bruns',
    );

    // The sealed Loot Drop counts for nothing (D-10): 5 entries of the 6.
    const stats = screen.getAllByRole('definition')[0]!.closest('dl')!;
    expect(stats.textContent).toBe('entries5photos9voice notes2');

    const thumbnails = within(screen.getByRole('list', { name: 'Photos this week' })).getAllByRole(
      'img',
    );
    expect(thumbnails).toHaveLength(9);
    expect(thumbnails[0]).toHaveAttribute('alt', 'Photo from Packing list, round two');
    expect(thumbnails[0]).toHaveAttribute('fetchpriority', 'high');
    expect(thumbnails[1]).toHaveAttribute('loading', 'lazy');
    expect(thumbnails[8]).toHaveAttribute('alt', 'Diary page from Diary page');

    const tags = within(screen.getByRole('list', { name: 'Tags this week' })).getAllByRole('link');
    expect(tags.map((tag) => tag.getAttribute('aria-label'))).toEqual([
      'Grampians, 4 entries',
      'Friends, 2 entries',
      'Outdoors, 2 entries',
    ]);
    expect(tags[0]).toHaveAttribute('href', '/entries?tag=tag-grampians');

    expect((await bars()).map((bar) => bar.getAttribute('aria-label'))).toEqual([
      'Packing list, round two, Monday 19 October, 1 photo',
      'Halls Gap, finally, Friday 23 October, 3 photos',
      'Loot Drop, Saturday 24 October, Opens 25 Oct 2027',
      'Sunrise at the Pinnacle, Saturday 24 October, 4 photos, 1 voice note',
      'Diary page, Saturday 24 October, 1 photo',
      'Voice note, Sunday 25 October, 1 voice note',
    ]);
  });

  test('display titles fall back from the title to the first non-heading line, then to the media', async () => {
    serveWeek(grampiansWeek);
    renderApp('/weeks/week-gramp');
    const titles = (await bars()).map((bar) => bar.textContent);
    expect(titles).toEqual([
      'Mon 19Packing list, round two1',
      // "Highlights" is the template's prompt, a heading: the line under it names the entry (D-24).
      'Fri 23Halls Gap, finally3',
      'Sat 24Loot DropOpens 25 Oct 2027',
      'Sat 24Sunrise at the Pinnacle41',
      'Sat 24Diary page1',
      'Sun 25Voice note1',
    ]);
    expect(screen.queryByText('Highlights')).toBeNull();
  });

  test('a sealed entry’s bar shows only the star, the day and the opening date', async () => {
    // Even if a sealed entry arrived with content (a server bug), none of it may show (D-10).
    const leaky = {
      ...sealedEntry('e-sealed', '2026-10-24', '2027-10-24T22:00:00Z'),
      title: 'The secret title',
      media: [photo('secret-photo', 'e-sealed')],
      tags: [{ id: 'tag-secret', name: 'Secret', source: 'USER' }],
    };
    serveWeek(week('week-sealed', '2026-10-19', '2026-10-25', [], [leaky]));
    renderApp('/weeks/week-sealed');

    const [bar] = await bars();
    expect(bar).toHaveAttribute('href', '/entries/e-sealed');
    expect(bar).toHaveAttribute('aria-label', 'Loot Drop, Saturday 24 October, Opens 25 Oct 2027');
    expect(bar!.textContent).toBe('Sat 24Loot DropOpens 25 Oct 2027');
    // The star mark is drawn (aria-hidden); there is no padlock icon, and nothing private anywhere on the page.
    expect(bar!.querySelector('svg use')).not.toBeNull();
    expect(screen.queryByText(/secret/i)).toBeNull();
    expect(document.querySelector('img')).toBeNull();
    expect(screen.queryByRole('list', { name: 'Tags this week' })).toBeNull();
    // No readable entry, so no stats row: a sealed Loot Drop is never counted.
    expect(screen.queryByRole('definition')).toBeNull();
  });

  test('tapping a bar opens the entry', async () => {
    const user = userEvent.setup();
    serveWeek(grampiansWeek);
    server.use(http.get('*/api/v1/entries/:id', () => HttpResponse.json(grampiansWeek.entries[1])));
    const { router } = renderApp('/weeks/week-gramp');
    await user.click((await bars())[1]!);
    // The entry page is a lazy route (task 4.12): the navigation commits once its chunk has loaded.
    await waitFor(() => expect(router.state.location.pathname).toBe('/entries/e-halls'));
  });

  test('a week without a title is named by its dates, said once', async () => {
    serveWeek(
      week(
        'week-plain',
        '2026-11-02',
        '2026-11-08',
        [],
        [entry('a', '2026-11-03', { title: 'Hobart' })],
      ),
    );
    renderApp('/weeks/week-plain');
    expect(await screen.findByRole('heading', { level: 1, name: '2–8 Nov 2026' })).toBeVisible();
    expect(screen.queryAllByText(/2–8 Nov 2026/)).toHaveLength(1);
  });

  test('the current week is "This week"', async () => {
    serveWeek(
      week(
        'week-now',
        '2026-11-23',
        '2026-11-29',
        [],
        [entry('a', '2026-11-24', { title: 'Quiet day' })],
      ),
    );
    renderApp('/weeks/week-now');
    expect(await screen.findByRole('heading', { level: 1, name: 'This week' })).toBeVisible();
    expect(screen.getByText('23–29 Nov 2026')).toBeVisible();
  });

  test('Edit week sends the whole week back: the new title and the soundtrack it had (D-26)', async () => {
    const user = userEvent.setup();
    const sent = serveWeek({
      ...grampiansWeek,
      soundtrack: 'spotify:track:4uLU6hMCjMI75M1A2tKUQC',
    });
    renderApp('/weeks/week-gramp');
    await user.click(await screen.findByRole('button', { name: 'Edit week' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit week' });
    const field = within(dialog).getByLabelText('Name');
    expect(field).toHaveValue('Grampians Trip Week');
    await user.clear(field);
    await user.type(field, '  Halls Gap weekend ');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Halls Gap weekend' }),
    ).toBeVisible();
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(sent).toEqual([
      {
        method: 'PUT',
        path: '/api/v1/weekly-logs/week-gramp',
        body: { title: 'Halls Gap weekend', soundtrack: 'spotify:track:4uLU6hMCjMI75M1A2tKUQC' },
      },
    ]);
    // Focus goes back to the button that opened the dialog.
    expect(screen.getByRole('button', { name: 'Edit week' })).toHaveFocus();
  });

  test('a week with every entry sealed still shows its own soundtrack, and Edit week changes it (D-35)', async () => {
    const user = userEvent.setup();
    const allSealed: Week = {
      ...grampiansWeek,
      soundtrack: 'spotify:album:4uLU6hMCjMI75M1A2tKUQC',
      entries: grampiansWeek.entries.filter((e) => e.isLocked),
    };
    expect(allSealed.entries.length).toBeGreaterThan(0);
    const sent = serveWeek(allSealed);
    renderApp('/weeks/week-gramp');
    expect(await screen.findByRole('button', { name: 'Play the album on Spotify' })).toBeVisible();
    expect(document.querySelector('iframe')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Edit week' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit week' });
    await user.click(
      within(dialog).getByRole('button', { name: 'Remove the soundtrack, album on Spotify' }),
    );
    await user.click(within(dialog).getByLabelText('Soundtrack'));
    await user.paste('https://open.spotify.com/track/0000000000000000000001?si=x');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('button', { name: 'Play the song on Spotify' })).toBeVisible();
    expect(sent[0]!.body).toEqual({
      title: 'Grampians Trip Week',
      soundtrack: 'spotify:track:0000000000000000000001',
    });
  });

  test('an empty name clears the title, and the week is called by its dates again', async () => {
    const user = userEvent.setup();
    const sent = serveWeek(grampiansWeek);
    renderApp('/weeks/week-gramp');
    await user.click(await screen.findByRole('button', { name: 'Edit week' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit week' });
    await user.clear(within(dialog).getByLabelText('Name'));
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('heading', { level: 1, name: '19–25 Oct 2026' })).toBeVisible();
    expect(sent[0]!.body).toEqual({});
  });

  test('Close week marks it closed, and Reopen week undoes it (F-12)', async () => {
    const user = userEvent.setup();
    const sent = serveWeek(grampiansWeek);
    renderApp('/weeks/week-gramp');
    await user.click(await screen.findByRole('button', { name: 'Close week' }));
    expect(await screen.findByRole('button', { name: 'Reopen week' })).toBeVisible();
    expect(screen.getByText(/· Closed$/)).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Reopen week' }));
    expect(await screen.findByRole('button', { name: 'Close week' })).toBeVisible();
    expect(sent.map((call) => call.path)).toEqual([
      '/api/v1/weekly-logs/week-gramp/close',
      '/api/v1/weekly-logs/week-gramp/reopen',
    ]);
  });

  test('a week whose entries were all deleted sends the user back to the feed (F-15)', async () => {
    serveWeek(week('week-gone', '2026-10-12', '2026-10-18', [], []));
    const { router } = renderApp('/weeks/week-gone');
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
  });

  test('a week that isn’t the user’s says so, without the server’s words', async () => {
    serveWeek(grampiansWeek);
    renderApp('/weeks/someone-elses');
    expect(await screen.findByText('This week isn’t in your journal.')).toBeVisible();
    expect(screen.queryByText(/Server detail/)).toBeNull();
    expect(screen.getByRole('link', { name: 'Go to your journal' })).toHaveAttribute('href', '/');
  });

  // Review fix (task 4.10): the week's cache lives up to the 30-minute staleTime, so every entry write that refreshes
  // the feed must refresh the weeks too, or Back shows a sealed entry whole (D-10) or a deleted one (D-30).
  describe('entry writes refresh a cached week', () => {
    const withClient = (queryClient: QueryClient) =>
      function Wrapper({ children }: { children: ReactNode }) {
        return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
      };

    /** Loads the week, then leaves it, so its data stays in the cache but no page is watching it. */
    async function loadThenLeave() {
      const app = renderApp('/weeks/week-gramp');
      expect(await bars()).toHaveLength(6);
      await act(() => app.router.navigate('/eras'));
      return app;
    }

    test('sealing one of its entries: Back shows only the star, the day and the opening date', async () => {
      const sealedHalls = sealedEntry('e-halls', '2026-10-23', '2027-03-01T22:00:00Z');
      serveWeek(grampiansWeek);
      const { router, queryClient } = await loadThenLeave();

      // The server now holds e-halls sealed, and answers the seal with the redacted entry.
      serveWeek({
        ...grampiansWeek,
        entries: grampiansWeek.entries.map((e) => (e.id === 'e-halls' ? sealedHalls : e)),
      });
      server.use(http.post('*/api/v1/entries/:id/seal', () => HttpResponse.json(sealedHalls)));
      const seal = renderHook(() => useSeal('e-halls'), { wrapper: withClient(queryClient) });
      await act(() => seal.result.current.mutateAsync('2027-03-01T22:00:00Z'));

      await act(() => router.navigate('/weeks/week-gramp'));
      // The first render after Back, before any refetch lands: the cached whole copy must not be drawn (review 2).
      expect(screen.queryByText('Halls Gap, finally')).toBeNull();
      await waitFor(async () =>
        expect((await bars())[1]!.textContent).toBe('Fri 23Loot DropOpens 2 Mar 2027'),
      );
      expect(screen.queryByText('Halls Gap, finally')).toBeNull();
      expect(screen.getAllByRole('definition')[0]!.closest('dl')!.textContent).toBe(
        'entries4photos6voice notes2',
      );
    });

    test('deleting one of its entries: Back no longer lists it', async () => {
      serveWeek(grampiansWeek);
      const { router, queryClient } = await loadThenLeave();

      serveWeek({
        ...grampiansWeek,
        entries: grampiansWeek.entries.filter((e) => e.id !== 'e-halls'),
      });
      server.use(
        http.delete('*/api/v1/entries/:id', () => new HttpResponse(null, { status: 204 })),
      );
      const remove = renderHook(() => useDeleteEntry('e-halls'), {
        wrapper: withClient(queryClient),
      });
      await act(() => remove.result.current.mutateAsync());

      await act(() => router.navigate('/weeks/week-gramp'));
      expect(screen.queryByText('Halls Gap, finally')).toBeNull();
      await waitFor(async () => expect(await bars()).toHaveLength(5));
      expect(screen.queryByText('Halls Gap, finally')).toBeNull();
    });
  });
});

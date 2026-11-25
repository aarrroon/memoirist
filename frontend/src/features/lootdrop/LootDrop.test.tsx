import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { afterEach, beforeAll, beforeEach, describe, expect, test, vi } from 'vitest';
import type { EntrySummary } from '../../api/lootDrops';
import { doc, entry, page, sampleWeeks, sealedEntry, week } from '../../test/msw/feed';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';

// Loot Drops in the UI (task 4.8, D-10, F-06, F-07) against MSW: the Loot Drop page and Extend, a 409 that reads
// kindly, Delete, the Opened row and its dismiss, opening an opened Loot Drop, and a card that opens itself. The data
// is shaped as the server sends it: a sealed entry is already redacted (EntryMapper); Recently deleted and other
// users' entries never reach the client (the backend's FeedEndpointTest and OpenedEndpointTest prove that).

// 23:47 on Tuesday 24 November 2026 in Melbourne. Only Date is faked, so MSW's and React's timers still run.
const NOW = new Date('2026-11-24T12:47:00Z');

// The seal panel is loaded with React.lazy; under a full parallel run its first import can take longer than
// findBy's 1 s default (capture's own sheet test waits the same way).
const LAZY = { timeout: 10_000 };
// Each test may wait that long for the lazy panel, so it needs more than Vitest's 5 s default to do so.
const SLOW = { timeout: 15_000 };

// Importing the lazy seal panel here first means React.lazy finds it already loaded (task 4.8 close-out).
beforeAll(async () => {
  await import('./SealPanel');
}, 60_000);

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
});

afterEach(() => {
  vi.useRealTimers();
});

const problem = (status: number, suffix: string) =>
  HttpResponse.json(
    {
      type: `urn:memoirist:problem:${suffix}`,
      title: 'Problem',
      status,
      detail: 'Server detail that must never be shown.',
    },
    { status },
  );

// Sealed on Thursday 19 November 2026, opening at 9:00 am on Monday 25 January 2027 in Melbourne.
const sealed = sealedEntry('entry-sealed', '2026-11-19', '2027-01-24T22:00:00Z');

/** Answers for one entry, and a record of the seal and delete requests. */
function entryApi(current = sealed) {
  const sent: { method: string; path: string; body: unknown }[] = [];
  let latest = current;
  server.use(
    http.get('*/api/v1/entries/:id', ({ params }) =>
      params.id === latest.id ? HttpResponse.json(latest) : problem(404, 'not-found'),
    ),
    http.post('*/api/v1/entries/:id/seal', async ({ request, params }) => {
      const body = (await request.json()) as { unlockDate: string };
      sent.push({ method: 'POST', path: `/entries/${String(params.id)}/seal`, body });
      latest = { ...latest, unlockDate: body.unlockDate, version: latest.version + 1 };
      return HttpResponse.json(latest);
    }),
    http.delete('*/api/v1/entries/:id', ({ params }) => {
      sent.push({ method: 'DELETE', path: `/entries/${String(params.id)}`, body: null });
      return new HttpResponse(null, { status: 204 });
    }),
    http.post('*/api/v1/entries/:id/mark-seen', ({ params }) => {
      sent.push({ method: 'POST', path: `/entries/${String(params.id)}/mark-seen`, body: null });
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return sent;
}

describe('the Loot Drop page', SLOW, () => {
  test('shows the star, the countdown and the opening moment once, and nothing of what it holds', async () => {
    entryApi();
    renderApp('/entries/entry-sealed');

    expect(await screen.findByText('in 62 days')).toBeInTheDocument();
    expect(screen.getByText(/^Opens Monday 25 January 2027 at/)).toHaveTextContent(
      'Opens Monday 25 January 2027 at 9:00 am',
    );
    // 19 November to 24 November is 5 days travelled; 19 November to 25 January is 67 days in all.
    expect(
      screen.getByRole('img', {
        name: 'A star whose light set out on Thursday 19 November 2026. It has travelled 5 days of 67.',
      }),
    ).toBeInTheDocument();
    expect(screen.getByText('Written 19 Nov 2026')).toBeInTheDocument();
    // The opening moment is said once on the page (design.md: no fact repeated).
    expect(screen.getAllByText(/25 January 2027/)).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Extend the seal' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument();
  });

  test('extends the seal to a later day, opening at 9:00 am in the user’s zone, after a confirmation', async () => {
    const sent = entryApi();
    const user = userEvent.setup();
    renderApp('/entries/entry-sealed');
    await user.click(await screen.findByRole('button', { name: 'Extend the seal' }));

    const panel = await screen.findByRole('dialog', { name: 'Extend the seal' }, LAZY);
    await user.click(within(panel).getByRole('button', { name: 'Pick a date' }));
    // The current opening day and every day before it can't be picked: a seal only moves later (D-10).
    expect(await screen.findByRole('button', { name: /January 25th, 2027/ })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: /January 28th, 2027/ }));
    expect(within(panel).getByText(/Thursday 28 January 2027 at/)).toHaveTextContent(
      'Thursday 28 January 2027 at 9:00 am',
    );

    await user.click(within(panel).getByRole('button', { name: 'Extend the seal' }));
    const confirm = await screen.findByRole('alertdialog', { name: 'Seal this Loot Drop?' });
    expect(confirm).toHaveTextContent('Opens Thursday 28 January 2027 at 9:00 am, in 65 days.');
    await user.click(within(confirm).getByRole('button', { name: 'Extend the seal' }));

    // 9:00 am on 28 January in Melbourne (daylight time, +11) is 22:00 UTC on the 27th.
    await waitFor(() =>
      expect(sent).toContainEqual({
        method: 'POST',
        path: '/entries/entry-sealed/seal',
        body: { unlockDate: '2027-01-27T22:00:00.000Z' },
      }),
    );
    expect(await screen.findByText('in 65 days')).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: 'Extend the seal' })).not.toBeInTheDocument();
  });

  test('a 409 entry-sealed reads as a friendly message, never the server’s words', async () => {
    entryApi();
    server.use(http.post('*/api/v1/entries/:id/seal', () => problem(409, 'entry-sealed')));
    const user = userEvent.setup();
    renderApp('/entries/entry-sealed');
    await user.click(await screen.findByRole('button', { name: 'Extend the seal' }));
    const panel = await screen.findByRole('dialog', { name: 'Extend the seal' }, LAZY);
    await user.click(within(panel).getByRole('button', { name: 'Pick a date' }));
    await user.click(await screen.findByRole('button', { name: /January 28th, 2027/ }));
    await user.click(within(panel).getByRole('button', { name: 'Extend the seal' }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Extend the seal',
      }),
    );

    expect(await within(panel).findByRole('alert')).toHaveTextContent(
      'This Loot Drop is sealed. Its opening date can only move later.',
    );
    expect(screen.queryByText(/Server detail/)).not.toBeInTheDocument();
    // Nothing changed on the page.
    expect(screen.getByText('in 62 days')).toBeInTheDocument();
  });

  test('a failed request says the seal didn’t change', async () => {
    entryApi();
    server.use(http.post('*/api/v1/entries/:id/seal', () => HttpResponse.error()));
    const user = userEvent.setup();
    renderApp('/entries/entry-sealed');
    await user.click(await screen.findByRole('button', { name: 'Extend the seal' }));
    const panel = await screen.findByRole('dialog', { name: 'Extend the seal' }, LAZY);
    await user.click(within(panel).getByRole('button', { name: 'Pick a date' }));
    await user.click(await screen.findByRole('button', { name: /January 30th, 2027/ }));
    await user.click(within(panel).getByRole('button', { name: 'Extend the seal' }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Extend the seal',
      }),
    );
    expect(await within(panel).findByRole('alert')).toHaveTextContent(
      'The seal didn’t change. Try again.',
    );
  });

  test('Escape closes the panel and Go back leaves the seal as it was', async () => {
    const sent = entryApi();
    const user = userEvent.setup();
    renderApp('/entries/entry-sealed');
    await user.click(await screen.findByRole('button', { name: 'Extend the seal' }));
    const panel = await screen.findByRole('dialog', { name: 'Extend the seal' }, LAZY);
    await user.click(within(panel).getByRole('button', { name: 'Pick a date' }));
    await user.click(await screen.findByRole('button', { name: /January 28th, 2027/ }));
    await user.click(within(panel).getByRole('button', { name: 'Extend the seal' }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Go back' }),
    );
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    // Neither opens from a Radix Trigger, so each hands focus back by hand (DESIGN.md §8).
    await waitFor(() =>
      expect(within(panel).getByRole('button', { name: 'Extend the seal' })).toHaveFocus(),
    );
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Extend the seal' })).toHaveFocus(),
    );
    expect(sent).toEqual([]);
  });

  test('Escape on the delete question keeps the Loot Drop and puts focus back on Delete', async () => {
    const sent = entryApi();
    const user = userEvent.setup();
    renderApp('/entries/entry-sealed');
    await user.click(await screen.findByRole('button', { name: 'Delete' }));
    await screen.findByRole('alertdialog', { name: 'Delete this Loot Drop?' });
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    await waitFor(() => expect(screen.getByRole('button', { name: 'Delete' })).toHaveFocus());
    expect(sent).toEqual([]);
  });

  test('Delete asks first, says it stays sealed in Recently deleted, then goes back to the Journal', async () => {
    const sent = entryApi();
    const user = userEvent.setup();
    const { router } = renderApp('/entries/entry-sealed');
    await user.click(await screen.findByRole('button', { name: 'Delete' }));
    const confirm = await screen.findByRole('alertdialog', { name: 'Delete this Loot Drop?' });
    expect(confirm).toHaveTextContent('It moves to Recently deleted, still sealed.');
    await user.click(within(confirm).getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(sent).toEqual([{ method: 'DELETE', path: '/entries/entry-sealed', body: null }]);
  });

  test('an entry that isn’t there says so', async () => {
    entryApi();
    renderApp('/entries/someone-elses');
    expect(await screen.findByRole('heading', { name: 'Not here' })).toBeInTheDocument();
  });
});

describe('an opened Loot Drop', SLOW, () => {
  const opened = entry('entry-letter', '2025-10-27', {
    title: 'Letter to future me',
    contentJson: doc('Tomorrow the big room is mine.'),
    unlockDate: '2026-11-23T22:00:00Z', // 9:00 am today, Tuesday 24 November, in Melbourne
  });

  test('shows the whole entry with the trail lit, and opening it marks it seen', async () => {
    const sent = entryApi(opened);
    renderApp('/entries/entry-letter');

    expect(await screen.findByRole('heading', { name: 'Letter to future me' })).toBeInTheDocument();
    expect(screen.getByText('Tomorrow the big room is mine.')).toBeInTheDocument();
    expect(screen.getByText('Written 27 Oct 2025')).toBeInTheDocument();
    expect(screen.getByText('Opened today')).toBeInTheDocument();
    await waitFor(() =>
      expect(sent).toContainEqual({
        method: 'POST',
        path: '/entries/entry-letter/mark-seen',
        body: null,
      }),
    );
    expect(sent.filter((call) => call.path.endsWith('mark-seen'))).toHaveLength(1);
  });

  test('an entry that was never sealed is not marked seen', async () => {
    const sent = entryApi(entry('entry-plain', '2026-11-20', { title: 'Plain' }));
    renderApp('/entries/entry-plain');
    expect(await screen.findByRole('heading', { name: 'Plain' })).toBeInTheDocument();
    expect(screen.queryByText(/Opened/)).not.toBeInTheDocument();
    expect(sent).toEqual([]);
  });
});

/** A row of the Opened strip as the server sends it: whole, because it has opened. */
const openedRow = (
  id: string,
  title: string | null,
  previewLine: string | null = null,
): EntrySummary => ({
  id,
  weeklyLogId: 'week-old',
  entryDate: '2025-10-27',
  title,
  previewLine,
  photoCount: 0,
  voiceNoteCount: 0,
  firstMediaKind: null,
  tags: [],
  createdAt: '2025-10-27T09:00:00Z',
  unlockDate: '2026-11-23T22:00:00Z',
  isLocked: false,
  dateUnknown: false,
  deletedAt: null,
});

describe('the Opened row (D-10, D-41)', SLOW, () => {
  test('is not there when nothing has opened', async () => {
    renderApp('/');
    await screen.findByRole('heading', { name: 'Quiet day' });
    expect(screen.queryByText('A Loot Drop opened')).not.toBeInTheDocument();
  });

  test('takes the top of the feed while a Loot Drop has opened, linking to it', async () => {
    server.use(
      http.get('*/api/v1/entries/opened', () =>
        HttpResponse.json([
          openedRow('entry-letter', 'Letter to future me'),
          openedRow('entry-other', null, 'Second one'),
        ]),
      ),
    );
    renderApp('/');
    const row = await screen.findByRole('region', { name: 'Opened Loot Drops' });
    expect(row).toHaveTextContent('A Loot Drop opened');
    expect(row).toHaveTextContent('Letter to future me, and 1 more');
    expect(within(row).getByRole('link')).toHaveAttribute('href', '/entries/entry-letter');
    // It sits above the first week.
    const firstWeek = screen.getAllByRole('heading', { level: 2 })[0] as HTMLElement;
    expect(row.compareDocumentPosition(firstWeek) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  test('dismissing it takes it away at once, before the server answers, and marks it seen', async () => {
    const asked: string[] = [];
    let rows = [openedRow('entry-letter', 'Letter to future me')];
    server.use(
      http.get('*/api/v1/entries/opened', () => HttpResponse.json(rows)),
      http.post('*/api/v1/entries/:id/mark-seen', async ({ params }) => {
        asked.push(String(params.id));
        await delay(200);
        rows = [];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderApp('/');
    await user.click(await screen.findByRole('button', { name: 'Dismiss: Letter to future me' }));

    expect(screen.queryByText('A Loot Drop opened')).not.toBeInTheDocument();
    await waitFor(() => expect(asked).toEqual(['entry-letter']));
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(screen.queryByText('A Loot Drop opened')).not.toBeInTheDocument();
  });

  test('a dismiss that fails puts the row back and says why', async () => {
    server.use(
      http.get('*/api/v1/entries/opened', () =>
        HttpResponse.json([openedRow('entry-letter', 'Letter to future me')]),
      ),
      http.post('*/api/v1/entries/:id/mark-seen', () => problem(500, 'internal-error')),
    );
    const user = userEvent.setup();
    renderApp('/');
    await user.click(await screen.findByRole('button', { name: 'Dismiss: Letter to future me' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Something went wrong on our side. Try again soon.',
    );
    expect(screen.getByText('A Loot Drop opened')).toBeInTheDocument();
  });

  test('a sealed card on screen opens itself when its moment comes (F-07)', async () => {
    // Opens half a second from now. Until then the feed holds it sealed; afterwards the server sends it whole.
    const unlockDate = new Date(NOW.getTime() + 500).toISOString();
    const soon = sealedEntry('entry-soon', '2026-11-24', unlockDate);
    let open = false;
    const weekWith = (e: typeof soon) =>
      week('week-this', '2026-11-23', '2026-11-29', [], [e], null);
    server.use(
      http.get('*/api/v1/feed', () => {
        const shown = open ? { ...soon, isLocked: false, title: 'A note to tonight' } : soon;
        return HttpResponse.json(page([weekWith(shown), ...sampleWeeks.slice(1)]));
      }),
      http.get('*/api/v1/entries/opened', () =>
        HttpResponse.json(open ? [openedRow('entry-soon', 'A note to tonight')] : []),
      ),
    );
    renderApp('/');
    expect(await screen.findByText('today')).toBeInTheDocument();

    open = true;
    vi.setSystemTime(new Date(NOW.getTime() + 1000));
    expect(
      await screen.findByRole('heading', { name: 'A note to tonight' }, { timeout: 4000 }),
    ).toBeInTheDocument();
    expect(await screen.findByText('A Loot Drop opened')).toBeInTheDocument();
  });
});

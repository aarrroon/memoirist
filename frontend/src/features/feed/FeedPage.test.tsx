import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { useUiStore } from '../../stores/ui';
import {
  doc,
  entry,
  page,
  photo,
  sampleWeeks,
  sealedEntry,
  uniDays,
  week,
} from '../../test/msw/feed';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';

// The feed (task 4.4) against MSW. The sample data holds a sealed Loot Drop as the server sends it (D-10); entries
// in Recently deleted and other users' entries never reach the client (the backend's FeedEndpointTest proves that).

// 23:47 on Tuesday 24 November 2026 in Melbourne. Only Date is faked, so MSW's and React's timers still run.
const NOW = new Date('2026-11-24T12:47:00Z');

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
});

afterEach(() => {
  vi.useRealTimers();
  useUiStore.setState({ capture: null });
});

const problem = (status: number, suffix: string) =>
  HttpResponse.json(
    {
      type: `urn:memoirist:problem:${suffix}`,
      title: 'Problem',
      status,
      detail: 'Server detail that must never be shown.',
      instance: '/api/v1/feed',
      timestamp: '2026-11-24T12:47:00Z',
    },
    { status },
  );

/** The week line's link, found by the text it shows. */
const weekLink = (name: string) => screen.getByRole('link', { name: `${name}` });

describe('the sample journal', () => {
  test('is named for screen readers and in the browser tab', async () => {
    renderApp('/');

    expect(screen.getByRole('heading', { level: 1, name: 'Journal' })).toBeInTheDocument();
    expect(document.title).toBe('Journal · memoirist');
    expect(await screen.findByRole('heading', { name: 'Quiet day' })).toBeInTheDocument();
  });

  test('shows each week once, newest first, with its line naming the week and its entries', async () => {
    renderApp('/');
    await screen.findByRole('heading', { name: 'Quiet day' });

    const weeks = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent);
    expect(weeks).toEqual([
      'This week · 1 entry',
      '16–22 November · 2 entries',
      'The trip · 1 entry',
      '12–18 October · 1 entry',
    ]);
    expect(weekLink('This week · 1 entry')).toHaveAttribute('href', '/weeks/week-this');
  });

  test('files weeks under eras[0], nests the trip in Uni Days, and shows a partial overlap as a chip', async () => {
    renderApp('/');
    await screen.findByRole('heading', { name: 'Quiet day' });

    // Two runs of weeks: New job, then Uni Days with Tasmania nested one level inside it (D-22).
    const newJob = screen.getByRole('region', { name: 'New job' });
    const uni = screen.getByRole('region', { name: 'Uni Days' });
    const trip = within(uni).getByRole('region', { name: 'Tasmania, inside Uni Days' });
    expect(within(trip).getByRole('heading', { name: 'Day one in Hobart' })).toBeInTheDocument();
    expect(within(trip).getByText('in Uni Days')).toBeInTheDocument();

    // Last week is in New job and in Uni Days, which only partly overlap: Uni Days is a chip on it.
    const lastWeek = within(newJob).getByRole('region', { name: '16–22 November · 2 entries' });
    expect(within(lastWeek).getByText('Also in')).toBeInTheDocument();
    expect(within(lastWeek).getByRole('link', { name: 'Uni Days' })).toBeInTheDocument();
    // This week is in New job alone: no chips.
    const thisWeek = within(newJob).getByRole('region', { name: 'This week · 1 entry' });
    expect(within(thisWeek).queryByText('Also in')).not.toBeInTheDocument();
  });

  test('an entry shows its day, title, caption and tags, each tag a link to the timeline', async () => {
    renderApp('/');
    const today = (await screen.findByRole('heading', { name: 'Quiet day' })).closest('article')!;

    // Twice in the DOM: the phone's day line and the laptop's margin; CSS shows one (jsdom has no layout).
    expect(within(today).getAllByText('Today')).toHaveLength(2);
    expect(within(today).getByText('Nothing happened, and that was lovely.')).toBeInTheDocument();
    expect(within(today).getByRole('link', { name: 'Family' })).toHaveAttribute(
      'href',
      '/entries?tag=tag-family',
    );
  });

  test('an entry with nothing written names its first media item (D-24)', async () => {
    renderApp('/');
    await screen.findByRole('heading', { name: 'Quiet day' });

    const photoOnly = screen.getByRole('img', { name: 'Photo 1 of 2' }).closest('article')!;
    expect(within(photoOnly).getByText('Photo')).toBeInTheDocument();
    expect(within(photoOnly).getByText('Tuesday 17 November')).toBeInTheDocument();
    expect(within(photoOnly).getByText('17 Nov')).toBeInTheDocument();
  });

  test('only the first photo loads at once; every other photo is lazy', async () => {
    renderApp('/');
    const first = await screen.findByRole('img', { name: 'Photo 1 of 2' });
    const later = screen.getByRole('img', { name: 'Photo' });

    expect(first).toHaveAttribute('loading', 'eager');
    expect(first).toHaveAttribute('fetchpriority', 'high');
    expect(later).toHaveAttribute('loading', 'lazy');
    expect(later).not.toHaveAttribute('fetchpriority');
    // Only the first photo of an entry is loaded: the second waits for the swipe (task 4.5).
    expect(screen.getAllByRole('img', { name: /^Photo/ })).toHaveLength(2);
  });

  test('asks for one page of ten weeks, inside the endpoint’s limit (D-42)', async () => {
    const asked: string[] = [];
    server.use(
      http.get('*/api/v1/feed', ({ request }) => {
        asked.push(new URL(request.url).search);
        return HttpResponse.json(page(sampleWeeks));
      }),
    );
    renderApp('/');
    await screen.findByRole('heading', { name: 'Quiet day' });

    expect(asked).toEqual(['?page=0&size=10']);
  });
});

describe('a sealed Loot Drop (D-10)', () => {
  test('shows only its opening date and countdown, under the day line', async () => {
    renderApp('/');
    const card = (await screen.findByText('Opens Mon 25 Jan 2027')).closest('article')!;

    expect(card).toHaveAccessibleName('Sealed Loot Drop');
    expect(within(card).getByText('in 62 days')).toBeInTheDocument();
    expect(within(card).getByText('Thursday 19 November')).toBeInTheDocument();
    expect(within(card).queryByRole('img')).not.toBeInTheDocument();
    // Task 4.8: the card is one link to the Loot Drop page, named with what it shows and nothing more. The day
    // line above already names the memory date, so the link leaves it out (D-10, D-41).
    const link = within(card).getByRole('link');
    expect(link).toHaveAccessibleName('Sealed Loot Drop. Opens Mon 25 Jan 2027, in 62 days.');
    expect(link).toHaveAttribute('href', '/entries/entry-sealed');
  });

  test('is never counted: its week says 2 entries, not 3', async () => {
    renderApp('/');
    await screen.findByText('Opens Mon 25 Jan 2027');

    expect(weekLink('16–22 November · 2 entries')).toBeInTheDocument();
  });

  test('a week of only sealed entries names no count at all', async () => {
    server.use(
      http.get('*/api/v1/feed', () =>
        HttpResponse.json(
          page([
            week(
              'w',
              '2026-11-23',
              '2026-11-29',
              [],
              [sealedEntry('s', '2026-11-24', '2027-01-24T22:00:00Z')],
            ),
          ]),
        ),
      ),
    );
    renderApp('/');
    await screen.findByText('Opens Mon 25 Jan 2027');

    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(/^This week$/);
    expect(screen.getByRole('region', { name: 'Weeks in no Era' })).toBeInTheDocument();
  });

  test('shows nothing private even if a locked entry arrived with its content', async () => {
    // The server's EntryMapper removes all of this (D-10). The card reads only unlockDate, so a server mistake
    // still couldn't put the title, caption, tags or photo on the screen.
    const leaky = {
      ...sealedEntry('leak', '2026-11-24', '2027-01-24T22:00:00Z'),
      title: 'Secret title',
      contentJson: doc('Secret caption'),
      contentText: 'Secret caption',
      tags: [{ id: 'tag-secret', name: 'Secret tag', source: 'USER' }],
      media: [photo('secret-photo', 'leak')],
    };
    server.use(
      http.get('*/api/v1/feed', () =>
        HttpResponse.json(page([week('w', '2026-11-23', '2026-11-29', [uniDays], [leaky])])),
      ),
    );
    renderApp('/');
    await screen.findByText('Opens Mon 25 Jan 2027');

    expect(screen.queryByText(/Secret/)).not.toBeInTheDocument();
    expect(within(screen.getByRole('main')).queryByRole('img')).not.toBeInTheDocument();
  });
});

describe('states', () => {
  test('first run: an invitation to write, whose buttons open capture', async () => {
    server.use(http.get('*/api/v1/feed', () => HttpResponse.json(page([], 0, 0))));
    const user = userEvent.setup();
    renderApp('/');

    expect(
      await screen.findByRole('heading', { name: 'Your journal starts here' }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Write your first entry' }));
    expect(useUiStore.getState().capture).toBe('entry');
    await user.click(screen.getByRole('button', { name: 'Photo dump' }));
    expect(useUiStore.getState().capture).toBe('dump');
  });

  // The answer waits until the test lets it go. A fixed delay raced the 300 ms timer: with the whole suite running
  // in parallel (tasks 4.6 to 4.8 added heavy lazy-panel tests), a 3 s answer plus the waits outran the test's own
  // limit or beat a starved timer (task 4.8 close-out). Holding the answer leaves nothing to race.
  test(
    'loading: a status for screen readers at once, and the shapes only after 300 ms',
    { timeout: 20_000 },
    async () => {
      let release: () => void = () => undefined;
      const answered = new Promise<void>((resolve) => {
        release = resolve;
      });
      server.use(
        http.get('*/api/v1/feed', async () => {
          await answered;
          return HttpResponse.json(page(sampleWeeks));
        }),
      );
      renderApp('/');

      expect(screen.getByRole('status')).toHaveTextContent('Opening your journal…');
      expect(screen.queryByTestId('feed-skeleton')).not.toBeInTheDocument();
      expect(
        await screen.findByTestId('feed-skeleton', {}, { timeout: 10_000 }),
      ).toBeInTheDocument();
      release();
      expect(
        await screen.findByRole('heading', { name: 'Quiet day' }, { timeout: 8000 }),
      ).toBeInTheDocument();
    },
  );

  test('an error: Can’t reach memoirist with a friendly sentence, never the server’s detail; Try again works', async () => {
    let calls = 0;
    server.use(
      http.get('*/api/v1/feed', () => {
        calls += 1;
        return calls === 1 ? problem(404, 'not-found') : HttpResponse.json(page(sampleWeeks));
      }),
    );
    const user = userEvent.setup();
    renderApp('/');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Can’t reach memoirist');
    expect(alert).toHaveTextContent('This isn’t here any more.');
    expect(screen.queryByText(/Server detail/)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('heading', { name: 'Quiet day' })).toBeInTheDocument();
  });

  test('one entry that can’t be drawn shows a small error, and the rest of the feed carries on (F-08)', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    server.use(
      http.get('*/api/v1/feed', () =>
        HttpResponse.json(
          page([
            week(
              'w',
              '2026-11-23',
              '2026-11-29',
              [uniDays],
              [
                entry('broken', 'not-a-date', { title: 'Broken' }),
                entry('fine', '2026-11-24', { title: 'Still here' }),
              ],
            ),
          ]),
        ),
      ),
    );
    renderApp('/');

    expect(await screen.findByText('This entry couldn’t be shown.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Still here' })).toBeInTheDocument();
    vi.mocked(console.error).mockRestore();
  });
});

describe('more weeks', () => {
  const older = week(
    'week-older',
    '2026-09-07',
    '2026-09-13',
    [uniDays],
    [entry('entry-older', '2026-09-08', { title: 'From September' })],
  );

  test('the foot loads the next page, and the earlier weeks join the feed', async () => {
    const asked: string[] = [];
    server.use(
      http.get('*/api/v1/feed', ({ request }) => {
        const pageNumber = new URL(request.url).searchParams.get('page');
        asked.push(pageNumber ?? '');
        return HttpResponse.json(
          pageNumber === '0' ? page(sampleWeeks, 0, 2) : page([older], 1, 2),
        );
      }),
    );
    const user = userEvent.setup();
    renderApp('/');
    await screen.findByRole('heading', { name: 'Quiet day' });

    await user.click(screen.getByRole('button', { name: 'Show earlier weeks' }));

    expect(await screen.findByRole('heading', { name: 'From September' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Quiet day' })).toBeInTheDocument();
    expect(asked).toEqual(['0', '1']);
    // That was the last page: nothing more to ask for.
    expect(screen.queryByRole('button', { name: 'Show earlier weeks' })).not.toBeInTheDocument();
  });

  test('a failed next page keeps the weeks already loaded and offers Try again', async () => {
    let failures = 1;
    server.use(
      http.get('*/api/v1/feed', ({ request }) => {
        if (new URL(request.url).searchParams.get('page') === '0') {
          return HttpResponse.json(page(sampleWeeks, 0, 2));
        }
        if (failures > 0) {
          failures -= 1;
          return problem(400, 'validation-failed');
        }
        return HttpResponse.json(page([older], 1, 2));
      }),
    );
    const user = userEvent.setup();
    renderApp('/');
    await screen.findByRole('heading', { name: 'Quiet day' });

    await user.click(screen.getByRole('button', { name: 'Show earlier weeks' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Some details need another look.');
    expect(screen.getByRole('heading', { name: 'Quiet day' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('heading', { name: 'From September' })).toBeInTheDocument();
  });
});

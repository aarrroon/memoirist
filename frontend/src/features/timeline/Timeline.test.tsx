import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import type { TimelinePage } from '../../api/timeline';
import { server } from '../../test/msw/server';
import { timelineHandlers, timelineJournal, type SeenRequest } from '../../test/msw/timeline';
import { renderApp } from '../../test/render';

// Task 4.11: the Timeline at /entries against a stand-in server (test/msw/timeline.ts) whose data holds a sealed Loot
// Drop with a title and two tags, an entry in Recently deleted and another user's entry (CLAUDE.md, "Before saying a
// task is done" 3). The stand-in applies the server's rules; these tests show the screen adds nothing to them: no
// sealed title, count or tag anywhere, and a tag filter that lists only open entries (D-10, D-26, D-30).

const SECRETS = /sealed secret|deleted entry|Bob/i;

function serve() {
  const journal = timelineJournal();
  const seen: SeenRequest[] = [];
  server.use(...timelineHandlers(journal, seen));
  const listRequests = () =>
    seen.filter((r) => r.method === 'GET' && r.url.pathname.endsWith('/entries'));
  return { journal, seen, listRequests };
}

const rowNames = () =>
  screen
    .getAllByRole('link')
    .filter((link) => link.getAttribute('href')?.startsWith('/entries/'))
    .map((link) => link.getAttribute('aria-label'));

describe('the Timeline (F-16)', () => {
  test('lists every entry newest first under month headings, with display titles, counts and tags', async () => {
    const { listRequests } = serve();
    renderApp('/entries');

    expect(await screen.findByRole('heading', { level: 1, name: 'Timeline' })).toBeVisible();
    await screen.findByText('Post-trip blues');
    expect(
      screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent),
    ).toEqual(['November 2026', 'October 2026', 'October 2025']);

    // D-24: the title, else the preview line, else a label for the first media item.
    expect(rowNames()).toEqual([
      'Post-trip blues, Friday 20 November 2026, tagged Work',
      'Walked home the long way through Princes Park., Tuesday 3 November 2026, 1 photo, 1 voice note, tagged Walks, Work',
      'Loot Drop, Sunday 25 October 2026, Opens 25 Oct 2027',
      // Same day, same instant: the server breaks the tie on the id, and the client keeps its order.
      'Diary page, Saturday 24 October 2026, 1 photo',
      'Mackenzie Falls on the way out, Saturday 24 October 2026, 1 photo, tagged Grampians',
      'Voice note, Thursday 30 October 2025, 1 voice note, tagged Walks',
    ]);
    // The client asks for 50 rows at most and no filter (D-42: GET /entries allows 100).
    expect(listRequests()).toHaveLength(1);
    expect(listRequests()[0]!.url.searchParams.get('size')).toBe('50');
    expect(listRequests()[0]!.url.searchParams.has('tagId')).toBe(false);
  });

  test('a sealed row shows the star, its memory date and the opening date, and nothing private (D-10)', async () => {
    serve();
    renderApp('/entries');
    const sealed = await screen.findByRole('link', { name: /^Loot Drop/ });

    expect(within(sealed).getByText('Loot Drop')).toBeVisible();
    expect(within(sealed).getByText('Opens 25 Oct 2027')).toBeVisible();
    expect(within(sealed).getByText('25 Oct')).toBeVisible();
    // Its tags (Work, Surprise) and its photo count never show on the row.
    expect(within(sealed).queryByText(/Work|Surprise/)).toBeNull();
    // The row holds exactly its date, the star's words and the opening date.
    expect(sealed.textContent).toBe('25 OctSunLoot DropOpens 25 Oct 2027');
    expect(sealed).toHaveAttribute('href', '/entries/entry-sealed');
    // Nothing of the sealed, the deleted or the other user's entry is anywhere on the screen.
    expect(document.body.textContent).not.toMatch(SECRETS);
  });

  test('a tag chip filters to that tag’s open entries only, and All entries clears it (F-16, D-10)', async () => {
    const user = userEvent.setup();
    const { listRequests } = serve();
    const { router } = renderApp('/entries');
    await screen.findByText('Post-trip blues');

    const filter = screen.getByRole('group', { name: 'Filter by tag' });
    expect(within(filter).getByRole('button', { name: 'All entries' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await user.click(within(filter).getByRole('button', { name: 'Work' }));

    // The sealed Loot Drop also carries Work; it is not listed while filtering.
    await waitFor(() => expect(screen.queryByText('Loot Drop')).toBeNull());
    await screen.findByText('Post-trip blues');
    expect(rowNames()).toEqual([
      'Post-trip blues, Friday 20 November 2026, tagged Work',
      'Walked home the long way through Princes Park., Tuesday 3 November 2026, 1 photo, 1 voice note, tagged Walks, Work',
    ]);
    expect(router.state.location.search).toBe('?tag=tag-work');
    expect(listRequests().at(-1)!.url.searchParams.get('tagId')).toBe('tag-work');
    expect(within(filter).getByRole('button', { name: 'Work' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(document.body.textContent).not.toMatch(SECRETS);

    await user.click(within(filter).getByRole('button', { name: 'All entries' }));
    expect(await screen.findByText('Loot Drop')).toBeVisible();
    expect(router.state.location.search).toBe('');
  });

  test('a tag only sealed entries carry answers like an unused one: No entries with this tag yet', async () => {
    const user = userEvent.setup();
    serve();
    const { router } = renderApp('/entries?tag=tag-secret');

    expect(await screen.findByText('No entries with this tag yet.')).toBeVisible();
    expect(screen.queryByText('Loot Drop')).toBeNull();
    expect(screen.getByRole('button', { name: 'Surprise' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );

    await user.click(screen.getByRole('button', { name: 'Clear the filter' }));
    expect(await screen.findByText('Post-trip blues')).toBeVisible();
    expect(router.state.location.search).toBe('');
  });

  test('a tag link from another screen opens the Timeline filtered', async () => {
    serve();
    renderApp('/entries?tag=tag-walks');
    expect(await screen.findByText('Voice note')).toBeVisible();
    expect(screen.queryByText('Post-trip blues')).toBeNull();
  });

  test('a tag that is not in the journal (404) offers to clear the filter, never the server’s words', async () => {
    serve();
    renderApp('/entries?tag=tag-bob');
    expect(await screen.findByText('This tag isn’t in your journal any more.')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Clear the filter' })).toBeVisible();
    expect(document.body.textContent).not.toMatch(/sentence for the caller/);
  });

  test('loads the next page from the foot and keeps a month that spans pages as one section', async () => {
    const user = userEvent.setup();
    serve();
    const pages: TimelinePage[] = [
      {
        items: [row('p1', '2026-10-28', 'First page')],
        page: 0,
        size: 50,
        totalItems: 2,
        totalPages: 2,
      },
      {
        items: [row('p2', '2026-10-02', 'Second page')],
        page: 1,
        size: 50,
        totalItems: 2,
        totalPages: 2,
      },
    ];
    const asked: string[] = [];
    server.use(
      http.get('*/api/v1/entries', ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page'));
        asked.push(String(page));
        return HttpResponse.json(pages[page]);
      }),
    );
    renderApp('/entries');
    await screen.findByText('First page');

    await user.click(screen.getByRole('button', { name: 'Show earlier entries' }));
    expect(await screen.findByText('Second page')).toBeVisible();
    expect(asked).toEqual(['0', '1']);
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(1);
    expect(screen.queryByRole('button', { name: 'Show earlier entries' })).toBeNull();
  });

  test('an empty journal invites the first entry', async () => {
    server.use(
      http.get('*/api/v1/entries', () =>
        HttpResponse.json({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 }),
      ),
      http.get('*/api/v1/tags', () => HttpResponse.json([])),
    );
    renderApp('/entries');
    expect(await screen.findByRole('button', { name: 'Write your first entry' })).toBeVisible();
  });
});

function row(id: string, entryDate: string, title: string) {
  return {
    id,
    weeklyLogId: 'week',
    entryDate,
    title,
    previewLine: null,
    photoCount: 0,
    voiceNoteCount: 0,
    firstMediaKind: null,
    tags: [],
    createdAt: `${entryDate}T00:00:00Z`,
    unlockDate: null,
    isLocked: false,
    dateUnknown: false,
    deletedAt: null,
  } satisfies TimelinePage['items'][number];
}

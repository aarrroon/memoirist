import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import { erasureDay, formatDayMonth } from '../../lib/time';
import { deletedHandlers } from '../../test/msw/deleted';
import { server } from '../../test/msw/server';
import { timelineHandlers, timelineJournal, type SeenRequest } from '../../test/msw/timeline';
import { renderApp } from '../../test/render';

// Task 4.12: Recently deleted at /recently-deleted, the delete question, and the way an entry travels between the
// feed and the list, against a stand-in server (test/msw/deleted.ts) whose journal holds an entry in Recently deleted,
// a sealed Loot Drop in Recently deleted with a title and a tag, another user's deleted entry and a live sealed Loot
// Drop (CLAUDE.md, "Before saying a task is done" 3). Nothing sealed or someone else's may show (D-10, D-30).

const SECRETS = /secret|Bob/i;
const MELBOURNE = 'Australia/Melbourne';

function serve() {
  const journal = timelineJournal();
  const seen: SeenRequest[] = [];
  // deletedHandlers first: its GET /feed and DELETE answer from the journal; the timeline's give the tags.
  server.use(
    ...deletedHandlers(journal, seen),
    ...timelineHandlers(journal, seen),
    // You lists the templates' count too.
    http.get('*/api/v1/templates', () => HttpResponse.json([])),
  );
  const posts = (suffix: string) =>
    seen.filter((r) => r.method === 'POST' && r.url.pathname.endsWith(suffix));
  return { journal, seen, posts };
}

/** The rows, by the heading each one is labelled with. */
const rows = () =>
  screen
    .queryAllByRole('article')
    .map(
      (row) => row.getAttribute('aria-labelledby') && within(row).getByRole('heading').textContent,
    );

/** A paragraph by its whole text: the row's dates are spans inside it, kept from breaking across lines. */
const paragraph = (text: string) => (_: string, element: Element | null) =>
  element?.tagName === 'P' && element.textContent === text;

async function openList() {
  const result = renderApp('/recently-deleted');
  await screen.findByRole('heading', { level: 1, name: 'Recently deleted' });
  await screen.findAllByRole('article');
  return result;
}

describe('the list (F-18)', () => {
  test('shows a deleted entry and a sealed one, the most recently deleted first, and nothing private', async () => {
    const { seen } = serve();
    await openList();

    expect(rows()).toEqual(['Loot Drop, 11 Oct 2026', 'The deleted entry']);
    const [sealed, plain] = screen.getAllByRole('article');
    // Deleted 05:00 UTC on 22 Nov: due 05:00 UTC on 22 Dec, swept 16:00 UTC that day, 03:00 on 23 Dec in Melbourne.
    expect(
      within(sealed!).getByText(paragraph('Opens 11 Oct 2027. Erased unopened on 23 Dec')),
    ).toBeVisible();
    expect(within(sealed!).getByRole('button', { name: /^Restore, still sealed/ })).toBeVisible();
    expect(within(plain!).getByText(paragraph('20 Oct 2026. Erased on 21 Dec'))).toBeVisible();
    expect(
      within(plain!).getByRole('button', { name: 'Restore The deleted entry' }),
    ).toHaveTextContent(/^Restore$/);
    expect(
      within(plain!).getByRole('button', { name: 'Erase now The deleted entry' }),
    ).toBeVisible();
    // The sealed row's title, preview, photo and tag never arrive, and other users' and live entries aren't listed.
    expect(document.body).not.toHaveTextContent(SECRETS);
    expect(screen.queryByText('Post-trip blues')).not.toBeInTheDocument();
    // Never more than the endpoint's 50 (D-42).
    const list = seen.find((r) => r.url.pathname.endsWith('/entries/deleted'));
    expect(list!.url.searchParams.get('size')).toBe('50');
  });

  test('says when nothing is waiting', async () => {
    const { journal } = serve();
    for (const entry of journal.entries) entry.deletedAt = null;
    renderApp('/recently-deleted');

    expect(await screen.findByText('Nothing is waiting here.')).toBeVisible();
    expect(screen.getByText(/Entries wait here for 30 days/)).toBeVisible();
  });

  test('You shows how many wait, and leads here; so does the foot of the Timeline', async () => {
    serve();
    const user = userEvent.setup();
    const { router } = renderApp('/settings');

    // The side nav has a Recently deleted link too: the row is the one in the screen.
    const row = await within(screen.getByRole('main')).findByRole('link', {
      name: /^Recently deleted/,
    });
    await waitFor(() => expect(row).toHaveTextContent('Recently deleted2'));
    await user.click(row);
    await waitFor(() => expect(router.state.location.pathname).toBe('/recently-deleted'));

    await act(() => router.navigate('/entries'));
    await screen.findByText('Post-trip blues');
    await user.click(
      within(screen.getByRole('main')).getByRole('link', { name: 'Recently deleted' }),
    );
    await waitFor(() => expect(router.state.location.pathname).toBe('/recently-deleted'));
  });
});

describe('restore', () => {
  test('puts the entry back in its week in the feed', async () => {
    const { posts } = serve();
    const user = userEvent.setup();
    // The feed is read first and cached (staleTime 30 minutes), so only the restore's own refetch can bring the
    // entry back into it.
    const { router } = renderApp('/');
    await screen.findByRole('heading', { name: 'Post-trip blues' });
    expect(screen.queryByRole('heading', { name: 'The deleted entry' })).not.toBeInTheDocument();
    await act(() => router.navigate('/recently-deleted'));
    await screen.findAllByRole('article');

    await user.click(screen.getByRole('button', { name: 'Restore The deleted entry' }));

    const notice = await screen.findByText(/^Restored to the week of 19–25 October/);
    expect(posts('/restore')).toHaveLength(1);
    // Focus goes to the notice, since the pressed button left with its row.
    await waitFor(() => expect(notice.parentElement).toHaveFocus());
    expect(rows()).toEqual(['Loot Drop, 11 Oct 2026']);
    expect(screen.getByRole('link', { name: 'Go there' })).toHaveAttribute(
      'href',
      '/weeks/week-2026-10-19',
    );

    await act(() => router.navigate('/'));
    const weekLine = await screen.findByRole('link', { name: /19–25 October/ });
    const weekSection = weekLine.closest('section')!;
    expect(within(weekSection).getByRole('heading', { name: 'The deleted entry' })).toBeVisible();
  });

  test('a sealed Loot Drop comes back still sealed, and shows nothing private', async () => {
    serve();
    const user = userEvent.setup();
    const { router } = await openList();

    await user.click(screen.getByRole('button', { name: /^Restore, still sealed/ }));

    expect(
      await screen.findByText(/^Restored, still sealed, to the week of 5–11 October/),
    ).toBeVisible();
    await act(() => router.navigate('/'));
    await screen.findByText('Post-trip blues');
    expect(screen.getAllByRole('article', { name: 'Sealed Loot Drop' })).toHaveLength(2);
    expect(document.body).not.toHaveTextContent(SECRETS);
  });

  test('an entry restored or erased elsewhere first says so, and leaves the list', async () => {
    const { journal } = serve();
    const user = userEvent.setup();
    await openList();
    // Another device erases it after this list was read.
    journal.entries = journal.entries.filter((e) => e.id !== 'entry-deleted');

    await user.click(screen.getByRole('button', { name: 'Restore The deleted entry' }));

    expect(await screen.findByText('This isn’t here any more.')).toBeVisible();
    await waitFor(() => expect(rows()).toEqual(['Loot Drop, 11 Oct 2026']));
  });
});

describe('erase now', () => {
  test('asks first; Keep it erases nothing, Erase erases it at once', async () => {
    const { posts } = serve();
    const user = userEvent.setup();
    await openList();

    const erase = screen.getByRole('button', { name: 'Erase now The deleted entry' });
    await user.click(erase);
    let question = await screen.findByRole('alertdialog', { name: 'Erase this entry now?' });
    expect(question).toHaveTextContent('This can’t be undone.');
    await user.click(within(question).getByRole('button', { name: 'Keep it' }));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(posts('/erase')).toHaveLength(0);
    expect(erase).toHaveFocus();

    await user.click(erase);
    question = await screen.findByRole('alertdialog', { name: 'Erase this entry now?' });
    await user.click(within(question).getByRole('button', { name: 'Erase' }));

    expect(await screen.findByText('Erased.')).toBeVisible();
    expect(posts('/erase')).toHaveLength(1);
    expect(rows()).toEqual(['Loot Drop, 11 Oct 2026']);
  });

  test('a sealed Loot Drop can be erased too, unopened (D-30)', async () => {
    const { posts } = serve();
    const user = userEvent.setup();
    await openList();

    await user.click(screen.getByRole('button', { name: /^Erase now Loot Drop/ }));
    const question = await screen.findByRole('alertdialog', { name: 'Erase this Loot Drop now?' });
    expect(question).toHaveTextContent('It is erased unopened, at once.');
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(posts('/erase')).toHaveLength(0);
  });
});

describe('delete from the feed (F-09)', () => {
  test('asks, says it can be restored for 30 days, and the entry shows up in Recently deleted', async () => {
    const { journal } = serve();
    const user = userEvent.setup();
    // The list is read first and cached, so only the delete's own refetch can add the entry to it.
    const { router } = await openList();
    await act(() => router.navigate('/'));

    expect(await screen.findByRole('heading', { name: 'Post-trip blues' })).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'More actions for Post-trip blues' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Delete' }));
    const question = await screen.findByRole('alertdialog', { name: 'Delete this entry?' });
    expect(question).toHaveTextContent(
      'It moves to Recently deleted. You can restore it there for 30 days.',
    );
    await user.click(within(question).getByRole('button', { name: 'Delete' }));

    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Post-trip blues' })).not.toBeInTheDocument(),
    );
    const deletedAt = journal.entries.find((e) => e.id === 'entry-blues')!.deletedAt!;

    await act(() => router.navigate('/recently-deleted'));
    // The cached list shows first, marked stale by the delete, and the refetch adds the entry at the top.
    await waitFor(() => expect(rows()[0]).toBe('Post-trip blues'));
    expect(
      screen.getByText(
        paragraph(`20 Nov 2026. Erased on ${formatDayMonth(erasureDay(deletedAt, MELBOURNE))}`),
      ),
    ).toBeVisible();
  });

  test('an entry deleted elsewhere first answers 404, and its stale card leaves the feed', async () => {
    const { journal, seen } = serve();
    const user = userEvent.setup();
    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Post-trip blues' })).toBeVisible();
    // Another device deletes it while this feed still shows it.
    journal.entries.find((e) => e.id === 'entry-blues')!.deletedAt = new Date().toISOString();
    await user.click(screen.getByRole('button', { name: 'More actions for Post-trip blues' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Delete' }));
    const question = await screen.findByRole('alertdialog', { name: 'Delete this entry?' });
    await user.click(within(question).getByRole('button', { name: 'Delete' }));

    // hidden: true, because the open dialog hides the feed from the accessibility tree; the card itself must go.
    await waitFor(() =>
      expect(
        screen.queryByRole('heading', { name: 'Post-trip blues', hidden: true }),
      ).not.toBeInTheDocument(),
    );
    expect(seen.filter((r) => r.method === 'DELETE')).toHaveLength(1);
  });
});

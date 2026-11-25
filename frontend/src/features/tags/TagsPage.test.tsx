import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, test } from 'vitest';
import { server } from '../../test/msw/server';
import { timelineHandlers, timelineJournal, type SeenRequest } from '../../test/msw/timeline';
import { renderApp } from '../../test/render';

// Task 4.11: the Tags screen (F-17 step 4, D-26) against the stand-in server of test/msw/timeline.ts, whose data holds
// a sealed Loot Drop carrying Work and Surprise, an entry in Recently deleted carrying Work, and another user's entry
// carrying Work and a tag of their own.

function serve() {
  const journal = timelineJournal();
  const seen: SeenRequest[] = [];
  server.use(...timelineHandlers(journal, seen));
  const writes = () => seen.filter((r) => r.method !== 'GET');
  return { journal, writes };
}

const tagRows = () =>
  within(screen.getByRole('main'))
    .getAllByRole('listitem')
    .map((item) => item.textContent?.replace(/RenameDelete$/, ''));

describe('the Tags screen (F-17)', () => {
  test('lists the user’s tags by name with counts that leave out sealed, deleted and foreign entries', async () => {
    serve();
    renderApp('/tags');
    await screen.findByText('Grampians');

    // Work is on 5 stored entries; only 2 are the user's, live and open (D-10, D-30). Surprise is only on the sealed
    // Loot Drop: listed, with 0. The other user's tag is not listed.
    expect(tagRows()).toEqual([
      'Grampians1 entry',
      'Surprise0 entries',
      'Walks2 entries',
      'Work2 entries',
    ]);
    expect(screen.queryByText(/Bob/)).toBeNull();
  });

  test('renaming a tag updates it everywhere: the list, the Timeline’s chips and its rows', async () => {
    const user = userEvent.setup();
    const { writes } = serve();
    const { router } = renderApp('/entries');
    // The Timeline is read first, so its rows are in the cache when the rename happens.
    expect(
      await screen.findByRole('link', { name: /^Post-trip blues, .*tagged Work$/ }),
    ).toBeVisible();

    await act(() => router.navigate('/tags'));
    await user.click(await screen.findByRole('button', { name: 'Rename Work' }));
    const dialog = await screen.findByRole('dialog', { name: 'Rename tag' });
    const field = within(dialog).getByRole('textbox', { name: 'Name' });
    await user.clear(field);
    await user.type(field, '  Office ');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(await screen.findByText('Office')).toBeVisible();
    expect(screen.getByRole('status')).toHaveTextContent('Renamed to Office.');
    expect(writes().map((r) => `${r.method} ${r.url.pathname}`)).toEqual([
      'PUT /api/v1/tags/tag-work',
    ]);
    // Focus goes back to the button that opened the dialog (DESIGN.md §8).
    expect(screen.getByRole('button', { name: 'Rename Office' })).toHaveFocus();

    await act(() => router.navigate('/entries'));
    expect(
      await screen.findByRole('link', { name: /^Post-trip blues, .*tagged Office$/ }),
    ).toBeVisible();
    expect(screen.getByRole('button', { name: 'Office' })).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Work' })).toBeNull();
  });

  // TODO(human) PRACTICE: Write the test for a rename that clashes with another tag (F-17 edge case). Rename Work to
  // "walks": the server answers 409 conflict (names are unique ignoring case), the dialog stays open with the message
  // naming the existing tag in its own spelling ("Walks"), the server's own words never show, and nothing changes.
  // Hint: the stand-in server already answers 409 (test/msw/timeline.ts), so you only drive the screen. Find things
  // by role and accessible name (getByRole('dialog', { name }), textbox 'Name'), as a user would. Assert what the user
  // sees AND what must not happen: the row still says Work. To see the test can fail, break conflictMessage.
  // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
  test('renaming to a name another tag has keeps the dialog open and names that tag (409 conflict)', async () => {
    const user = userEvent.setup();
    serve();
    renderApp('/tags');

    await user.click(await screen.findByRole('button', { name: 'Rename Work' }));
    const dialog = await screen.findByRole('dialog', { name: 'Rename tag' });
    const field = within(dialog).getByRole('textbox', { name: 'Name' });
    await user.clear(field);
    await user.type(field, 'walks');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    expect(
      await within(dialog).findByText(
        'You already have a tag called Walks. Use that one, or pick another name.',
      ),
    ).toBeVisible();
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(document.body.textContent).not.toMatch(/sentence for the caller/);

    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(screen.getByRole('button', { name: 'Rename Work' })).toBeVisible();
    expect(screen.queryByText('walks')).toBeNull();
  });
  // └── END ANSWER ▲ ──

  test('an empty name is caught before anything is sent', async () => {
    const user = userEvent.setup();
    const { writes } = serve();
    renderApp('/tags');

    await user.click(await screen.findByRole('button', { name: 'Rename Grampians' }));
    const dialog = await screen.findByRole('dialog', { name: 'Rename tag' });
    await user.clear(within(dialog).getByRole('textbox', { name: 'Name' }));
    await user.type(within(dialog).getByRole('textbox', { name: 'Name' }), '   ');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    expect(within(dialog).getByText('A tag name is 1 to 50 characters.')).toBeVisible();
    expect(writes()).toEqual([]);
  });

  test('deleting asks first; Keep it sends nothing, Delete tag takes it off every entry', async () => {
    const user = userEvent.setup();
    const { writes } = serve();
    const { router } = renderApp('/tags');

    await user.click(await screen.findByRole('button', { name: 'Delete Walks' }));
    let question = await screen.findByRole('alertdialog', { name: 'Delete Walks?' });
    expect(question).toHaveTextContent(
      'Entries tagged Walks lose the tag and keep everything else.',
    );
    await user.click(within(question).getByRole('button', { name: 'Keep it' }));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull());
    expect(writes()).toEqual([]);
    expect(screen.getByRole('button', { name: 'Delete Walks' })).toHaveFocus();

    await user.click(screen.getByRole('button', { name: 'Delete Walks' }));
    question = await screen.findByRole('alertdialog', { name: 'Delete Walks?' });
    await user.click(within(question).getByRole('button', { name: 'Delete tag' }));

    await waitFor(() => expect(screen.queryByText('Walks')).toBeNull());
    expect(screen.getByRole('status')).toHaveTextContent('Walks is deleted.');
    expect(writes().map((r) => `${r.method} ${r.url.pathname}`)).toEqual([
      'DELETE /api/v1/tags/tag-walks',
    ]);

    // The entries stay, without the tag.
    await act(() => router.navigate('/entries'));
    expect(
      await screen.findByRole('link', {
        name: /^Voice note, Thursday 30 October 2025, 1 voice note$/,
      }),
    ).toBeVisible();
  });
});

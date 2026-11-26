import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeAll, beforeEach, describe, expect, test, vi } from 'vitest';
import { useUiStore } from '../../stores/ui';
import { fakeBackend } from '../../test/msw/capture';
import { doc, entry, page, sealedEntry } from '../../test/msw/feed';
import { devUser } from '../../test/msw/handlers';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';

// Loot Drop extras (task 4.16, D-33, F-01, F-06, F-07) against MSW: the seal panel's quick dates, Reply to past you
// and the first letter. Each describe names the Done-when item of SPRINT_PLAN 4.16 it proves. Sealed entries come
// already redacted, as the server's EntryMapper sends them; Recently deleted and other users' entries never reach the
// client (the backend's EntryReplyTest proves a reply can't point at either: both are 404).

// Shrinking needs a canvas, which jsdom lacks: a photo goes up as it was picked.
vi.mock('browser-image-compression', () => ({ default: (file: File) => Promise.resolve(file) }));

// 23:47 on Tuesday 24 November 2026 in Melbourne, as in the other capture and Loot Drop tests. Only Date is faked.
const NOW = new Date('2026-11-24T12:47:00Z');
const LAZY = { timeout: 10_000 };
const SLOW = { timeout: 20_000 };

let backend: ReturnType<typeof fakeBackend>;

beforeAll(async () => {
  await Promise.all([import('../capture/CaptureView'), import('./SealPanel')]);
}, 60_000);

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
  backend = fakeBackend();
  server.use(
    ...backend.handlers,
    http.post('*/api/v1/entries/:id/mark-seen', () => new HttpResponse(null, { status: 204 })),
  );
});

afterEach(() => {
  vi.useRealTimers();
  useUiStore.setState({ capture: null, replyTo: null });
  localStorage.clear();
});

const withBirthday = (birthday: string | null) =>
  server.use(http.get('*/api/v1/users/me', () => HttpResponse.json({ ...devUser, birthday })));

async function openSealPanel() {
  const user = userEvent.setup();
  renderApp('/?write=entry');
  const sheet = await screen.findByRole('dialog', { name: 'New entry' }, LAZY);
  await user.type(
    await within(sheet).findByRole('textbox', { name: 'Title (optional)' }),
    'For later',
  );
  await user.click(within(sheet).getByRole('button', { name: 'Seal' }));
  const panel = await screen.findByRole('dialog', { name: 'Seal as Loot Drop' }, LAZY);
  return { user, panel };
}

const radioNames = (panel: HTMLElement) =>
  within(panel)
    .getAllByRole('radio')
    .map((radio) => radio.closest('label')?.textContent);

describe('suggested unlock dates (D-33, F-06)', SLOW, () => {
  // Melbourne is +11 until April and +10 from April: 12 August opens at 23:00 UTC the day before, not 22:00.
  test.each([
    ['In a month', 'Thursday 24 December 2026', '2026-12-23T22:00:00.000Z'],
    ['In a year', 'Wednesday 24 November 2027', '2027-11-23T22:00:00.000Z'],
    ['On my birthday', 'Thursday 12 August 2027', '2027-08-11T23:00:00.000Z'],
  ])('%s opens at 9:00 am on %s in the user’s zone', async (choice, day, instant) => {
    withBirthday('1994-08-12');
    const { user, panel } = await openSealPanel();

    await user.click(within(panel).getByRole('radio', { name: new RegExp(`^${choice}`) }));
    expect(within(panel).getByText(new RegExp(`${day} at`))).toHaveTextContent(`${day} at 9:00 am`);
    await user.click(within(panel).getByRole('button', { name: 'Seal it' }));
    // A quick date still asks first, and says the moment in words (D-10, F-06).
    const confirm = await screen.findByRole('alertdialog', { name: 'Seal this Loot Drop?' });
    expect(confirm).toHaveTextContent(`Opens ${day} at 9:00 am`);
    await user.click(within(confirm).getByRole('button', { name: 'Seal it' }));

    await waitFor(() =>
      expect(backend.state.calls.at(-1)).toMatchObject({ body: { unlockDate: instant } }),
    );
    expect(backend.state.calls.at(-1)?.path).toMatch(/\/seal$/);
  });

  test('each row shows its own day, with the year', async () => {
    withBirthday('1994-08-12');
    const { panel } = await openSealPanel();
    expect(radioNames(panel)).toEqual([
      'In a monthThu 24 Dec 2026',
      'In a yearWed 24 Nov 2027',
      'On my birthdayThu 12 Aug 2027',
      'Pick a date',
    ]);
    // Nothing is chosen until the user taps a row, so Seal it waits.
    expect(within(panel).getByRole('button', { name: 'Seal it' })).toBeDisabled();
  });

  test('the birthday choice is hidden when no birthday is set', async () => {
    withBirthday(null);
    const { panel } = await openSealPanel();
    expect(within(panel).queryByRole('radio', { name: /On my birthday/ })).not.toBeInTheDocument();
    expect(radioNames(panel)).toEqual([
      'In a monthThu 24 Dec 2026',
      'In a yearWed 24 Nov 2027',
      'Pick a date',
    ]);
  });

  test('the radio rows work by keyboard: arrows choose, and Pick a date brings out the day chip', async () => {
    withBirthday(null);
    const { user, panel } = await openSealPanel();
    within(panel)
      .getByRole('radio', { name: /^In a month/ })
      .focus();
    await user.keyboard(' ');
    expect(within(panel).getByRole('radio', { name: /^In a month/ })).toBeChecked();
    await user.keyboard('{ArrowDown}{ArrowDown}');
    expect(within(panel).getByRole('radio', { name: 'Pick a date' })).toBeChecked();
    expect(within(panel).getByRole('button', { name: 'Choose the day' })).toBeInTheDocument();
    expect(within(panel).getByText('Pick the day it opens.')).toBeInTheDocument();
  });

  test('extending a seal offers only the quick dates after the current opening (D-10)', async () => {
    withBirthday('1994-08-12');
    backend.state.entries.set(
      'entry-sealed',
      sealedEntry('entry-sealed', '2026-11-19', '2027-01-24T22:00:00Z'),
    );
    const user = userEvent.setup();
    renderApp('/entries/entry-sealed');
    await user.click(await screen.findByRole('button', { name: 'Extend the seal' }));
    const panel = await screen.findByRole('dialog', { name: 'Extend the seal' }, LAZY);
    // In a month (24 December) is before 25 January, so it isn't offered.
    expect(radioNames(panel)).toEqual([
      'In a yearWed 24 Nov 2027',
      'On my birthdayThu 12 Aug 2027',
      'Pick a date',
    ]);
  });
});

// An opened Loot Drop: sealed on 27 October 2025, opened at 9:00 am today (D-10).
const letter = entry('entry-letter', '2025-10-27', {
  title: 'Letter to future me',
  contentJson: doc('Tomorrow the big room is mine.'),
  unlockDate: '2026-11-23T22:00:00Z',
});

describe('Reply to past you (D-33, F-07)', SLOW, () => {
  test('a reply is saved as a new entry that answers the Loot Drop, and each links to the other', async () => {
    backend.state.entries.set(letter.id, letter);
    const user = userEvent.setup();
    const { router } = renderApp('/entries/entry-letter');

    await user.click(await screen.findByRole('button', { name: 'Reply to past you' }));
    const sheet = await screen.findByRole('dialog', { name: 'Reply to past you' }, LAZY);
    // Capture says what the reply answers.
    expect(await within(sheet).findByText('Letter to future me')).toBeInTheDocument();
    expect(within(sheet).getByText(/In reply to/)).toHaveTextContent(
      'In reply to Letter to future me',
    );
    await user.type(
      await within(sheet).findByRole('textbox', { name: 'Title (optional)' }),
      'Dear past me',
    );
    await user.click(within(sheet).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    const created = backend.state.calls.find((c) => c.method === 'POST' && c.path === '/entries');
    expect(created?.body).toMatchObject({ replyToEntryId: 'entry-letter', title: 'Dear past me' });

    // The Loot Drop lists its reply, read again after the save.
    const replies = await screen.findByRole('region', { name: 'Your reply' });
    // The region appears with its loading row first; each reply arrives from its own GET, so wait for the link.
    const link = await within(replies).findByRole('link', { name: /Dear past me/ });
    const replyId = (created?.body as { id: string }).id;
    expect(link).toHaveAttribute('href', `/entries/${replyId}`);

    // The reply says what it answers, and links back.
    await user.click(link);
    await waitFor(() => expect(router.state.location.pathname).toBe(`/entries/${replyId}`));
    expect(await screen.findByRole('heading', { name: 'Dear past me' })).toBeInTheDocument();
    const back = await screen.findByRole('link', { name: 'Letter to future me' });
    expect(back).toHaveAttribute('href', '/entries/entry-letter');
    expect(back.closest('p')).toHaveTextContent('In reply to Letter to future me');
    // A reply is an ordinary entry: it has no Reply button of its own.
    expect(screen.queryByRole('button', { name: 'Reply to past you' })).not.toBeInTheDocument();
  });

  test('Escape closes the reply sheet and puts focus back on Reply to past you', async () => {
    backend.state.entries.set(letter.id, letter);
    const user = userEvent.setup();
    renderApp('/entries/entry-letter');
    await user.click(await screen.findByRole('button', { name: 'Reply to past you' }));
    const sheet = await screen.findByRole('dialog', { name: 'Reply to past you' }, LAZY);
    await within(sheet).findByText('Letter to future me');
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Reply to past you' })).toHaveFocus(),
    );
    expect(backend.state.calls.filter((c) => c.method !== 'GET')).toEqual([]);
  });

  test('an entry that was never sealed offers no reply', async () => {
    backend.state.entries.set(
      'entry-plain',
      entry('entry-plain', '2026-11-20', { title: 'Plain' }),
    );
    renderApp('/entries/entry-plain');
    expect(await screen.findByRole('heading', { name: 'Plain' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reply to past you' })).not.toBeInTheDocument();
  });

  test('Remove makes it an ordinary entry, sent with no link', async () => {
    backend.state.entries.set(letter.id, letter);
    const user = userEvent.setup();
    renderApp('/entries/entry-letter');
    await user.click(await screen.findByRole('button', { name: 'Reply to past you' }));
    const sheet = await screen.findByRole('dialog', { name: 'Reply to past you' }, LAZY);
    await user.click(
      await within(sheet).findByRole('button', {
        name: 'Remove the link to Letter to future me',
      }),
    );
    expect(within(sheet).getByRole('heading', { name: 'New entry' })).toBeInTheDocument();
    await user.type(within(sheet).getByRole('textbox', { name: 'Title (optional)' }), 'Just today');
    await user.click(within(sheet).getByRole('button', { name: 'Save' }));
    await waitFor(() =>
      expect(backend.state.calls.find((c) => c.path === '/entries')?.body).toMatchObject({
        title: 'Just today',
      }),
    );
    const body = backend.state.calls.find((c) => c.path === '/entries')?.body as Record<
      string,
      unknown
    >;
    expect(body.replyToEntryId).toBeUndefined();
  });

  test('a Loot Drop sealed again before the save gives a message in the app’s words', async () => {
    backend.state.entries.set(letter.id, letter);
    const user = userEvent.setup();
    renderApp('/entries/entry-letter');
    await user.click(await screen.findByRole('button', { name: 'Reply to past you' }));
    const sheet = await screen.findByRole('dialog', { name: 'Reply to past you' }, LAZY);
    await within(sheet).findByText('Letter to future me');
    // Sealed again in another tab while the reply was being written.
    backend.state.entries.set(
      letter.id,
      sealedEntry(letter.id, letter.entryDate, '2027-11-23T22:00:00Z'),
    );
    await user.type(within(sheet).getByRole('textbox', { name: 'Title (optional)' }), 'Too late');
    await user.click(within(sheet).getByRole('button', { name: 'Save' }));
    expect(
      await within(sheet).findByText(
        'That Loot Drop is sealed again, so this can’t answer it. Remove the link to save it.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/must be one of your Loot Drops/)).not.toBeInTheDocument();
  });

  test('a reply whose Loot Drop was deleted shows no link', async () => {
    backend.state.entries.set(
      'entry-reply',
      entry('entry-reply', '2026-11-24', { title: 'Dear past me', replyToEntryId: 'entry-gone' }),
    );
    renderApp('/entries/entry-reply');
    expect(await screen.findByRole('heading', { name: 'Dear past me' })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText(/In reply to/)).not.toBeInTheDocument());
    expect(screen.queryByRole('link', { name: /Loot Drop/ })).not.toBeInTheDocument();
  });

  // TODO(human) PRACTICE: Write the test for a reply whose Loot Drop was sealed again (extended after it opened). The
  // reply is an ordinary entry, so its page shows; the line about what it answers must name no title, because the
  // server sends none for a sealed entry (D-10). Prove that nothing the Loot Drop held reaches the screen.
  // Hint: put a redacted Loot Drop in the fake backend with sealedEntry(), exactly as EntryMapper would send it. Then
  // check both sides: what is shown ("a Loot Drop, sealed again", linking to its page) and what must not be (its old
  // title). Break EntryPage's AnswersLink on purpose (show `answered.title ?? 'untitled'`) and watch which assertion
  // goes red.
  // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
  test('a reply whose Loot Drop was sealed again names no title (D-10)', async () => {
    backend.state.entries.set(
      'entry-reply',
      entry('entry-reply', '2026-11-24', { title: 'Dear past me', replyToEntryId: letter.id }),
    );
    backend.state.entries.set(
      letter.id,
      sealedEntry(letter.id, letter.entryDate, '2027-11-23T22:00:00Z'),
    );
    renderApp('/entries/entry-reply');

    const link = await screen.findByRole('link', { name: 'a Loot Drop, sealed again' });
    expect(link).toHaveAttribute('href', '/entries/entry-letter');
    expect(link.closest('p')).toHaveTextContent('In reply to a Loot Drop, sealed again');
    expect(screen.queryByText(/Letter to future me/)).not.toBeInTheDocument();
    expect(screen.queryByText(/big room/)).not.toBeInTheDocument();
  });
  // └── END ANSWER ▲ ──
});

describe('the first letter (D-33, F-01)', SLOW, () => {
  beforeEach(() => {
    // A journal with no entries: the first-run screen.
    server.use(http.get('*/api/v1/feed', () => HttpResponse.json(page([]))));
  });

  test('ends the first-run screen, opening a month from today', async () => {
    renderApp('/');
    const card = await screen.findByRole('region', { name: 'A letter to open in a month' });
    expect(card).toHaveTextContent(
      'Write to the person you will be on 24 December 2026. It stays sealed until then, even from you.',
    );
    expect(within(card).getByRole('button', { name: 'Write the letter' })).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Skip' })).toBeInTheDocument();
  });

  test('Skip hides it, moves focus to Write your first entry, and it isn’t offered again', async () => {
    const user = userEvent.setup();
    const first = renderApp('/');
    const card = await screen.findByRole('region', { name: 'A letter to open in a month' });
    await user.click(within(card).getByRole('button', { name: 'Skip' }));

    expect(
      screen.queryByRole('region', { name: 'A letter to open in a month' }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Write your first entry' })).toHaveFocus();
    // Nothing was written or sealed.
    expect(backend.state.calls).toEqual([]);

    // Coming back to the first-run screen later: still not offered (F-01).
    first.unmount();
    renderApp('/');
    expect(await screen.findByRole('heading', { name: 'Your journal starts here' })).toBeVisible();
    expect(
      screen.queryByRole('region', { name: 'A letter to open in a month' }),
    ).not.toBeInTheDocument();
  });

  test('Write the letter saves it and seals it for a month, after the usual question', async () => {
    const user = userEvent.setup();
    renderApp('/');
    const card = await screen.findByRole('region', { name: 'A letter to open in a month' });
    await user.click(within(card).getByRole('button', { name: 'Write the letter' }));

    const sheet = await screen.findByRole('dialog', { name: 'Letter to future you' }, LAZY);
    expect(
      await within(sheet).findByText(/It opens on 24 December 2026 at 9:00 am/),
    ).toBeInTheDocument();
    // Its only seal is the top bar's Seal it: no seal panel to choose another day from.
    expect(within(sheet).queryByRole('button', { name: 'Seal' })).not.toBeInTheDocument();
    const sealIt = within(sheet).getByRole('button', { name: 'Seal it' });
    expect(sealIt).toBeDisabled();

    await user.type(within(sheet).getByRole('textbox', { name: 'Title (optional)' }), 'Hello you');
    await user.click(sealIt);
    const confirm = await screen.findByRole('alertdialog', { name: 'Seal this Loot Drop?' });
    expect(confirm).toHaveTextContent('Opens Thursday 24 December 2026 at 9:00 am');
    await user.click(within(confirm).getByRole('button', { name: 'Seal it' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    const writes = backend.state.calls.filter((c) => c.method !== 'GET');
    expect(writes.map((c) => c.path)).toEqual([
      '/entries',
      `/entries/${(writes[0]?.body as { id: string }).id}/seal`,
    ]);
    expect(writes[1]?.body).toEqual({ unlockDate: '2026-12-23T22:00:00.000Z' });
  });

  test('Go back on the question leaves the letter unsaved, with focus on Seal it', async () => {
    const user = userEvent.setup();
    renderApp('/?write=letter');
    const sheet = await screen.findByRole('dialog', { name: 'Letter to future you' }, LAZY);
    await user.type(
      await within(sheet).findByRole('textbox', { name: 'Title (optional)' }),
      'Not yet',
    );
    await user.click(within(sheet).getByRole('button', { name: 'Seal it' }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Go back' }),
    );
    await waitFor(() =>
      expect(within(sheet).getByRole('button', { name: 'Seal it' })).toHaveFocus(),
    );
    expect(backend.state.calls.filter((c) => c.method !== 'GET')).toEqual([]);
  });
});

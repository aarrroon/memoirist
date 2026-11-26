import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { afterEach, beforeAll, beforeEach, describe, expect, test, vi } from 'vitest';
import { useUiStore } from '../../stores/ui';
import { fakeBackend } from '../../test/msw/capture';
import { entry, sealedEntry } from '../../test/msw/feed';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';
import { datedPhoto } from '../../test/exif';
import { readDraft, writeDraft, type Draft } from './draft';

// Capture (task 4.6) against a fake entry API (MSW), driven the way a user would: through the sheet, by keyboard and
// pointer. Each test names the Done-when item of SPRINT_PLAN 4.6 it proves. jsdom has no layout, so capture is the
// phone sheet here (no media query matches); the laptop dialog is checked in the browser (audit.mjs).

// Shrinking needs a canvas, which jsdom lacks: the file goes up as it was picked.
// A spy, so a test can see which setting a photo was shrunk with (task 4.13).
const compress = vi.hoisted(() => vi.fn((file: File) => Promise.resolve(file)));
vi.mock('browser-image-compression', () => ({ default: compress }));

// 23:47 on Tuesday 24 November 2026 in Melbourne, as in the feed's tests. Only Date is faked.
const NOW = new Date('2026-11-24T12:47:00Z');

// The seal panel is loaded with React.lazy (task 4.8): its first import can outlast findBy's 1 s default.
const LAZY = { timeout: 10_000 };

let backend: ReturnType<typeof fakeBackend>;

// Capture and the seal panel are React.lazy. Importing them here first means each lazy import finds its module
// already loaded, so no test races a first import under a full parallel run (task 4.8 close-out).
beforeAll(async () => {
  await Promise.all([import('./CaptureView'), import('../lootdrop/SealPanel')]);
}, 60_000);

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
  backend = fakeBackend();
  server.use(...backend.handlers);
});

afterEach(() => {
  vi.useRealTimers();
  useUiStore.setState({ capture: null });
  localStorage.clear();
});

const photoFile = (name: string) => new File([new Uint8Array(2048)], name, { type: 'image/jpeg' });

async function openCapture(path = '/?write=entry') {
  const user = userEvent.setup();
  const view = renderApp(path);
  // The sheet's code is fetched on first use (React.lazy), which takes a while the first time in a test run.
  const sheet = await screen.findByRole(
    'dialog',
    { name: /New entry|Diary page/ },
    { timeout: 10_000 },
  );
  // The form waits for GET /users/me (the zone decides "today"), then shows the date chip.
  await within(sheet).findByRole('button', { name: /Memory date/ });
  return { user, sheet, ...view };
}

const saveButton = (sheet: HTMLElement) =>
  within(sheet).getByRole('button', { name: /^(Save|Try again)$/ });
const sentEntry = () =>
  backend.state.calls.find((c) => c.method === 'POST' && c.path === '/entries');

// 15 s each: the first test to open capture also loads its lazy chunk (TipTap, dnd-kit, Vaul), slow in a busy run.
describe('capture', { timeout: 15_000 }, () => {
  test('opens from the Write menu as a sheet named New entry, with today as the memory date', async () => {
    const user = userEvent.setup();
    renderApp('/');
    await user.click((await screen.findAllByRole('button', { name: 'Write' }))[0] as HTMLElement);
    await user.click(await screen.findByRole('menuitem', { name: 'New entry' }));

    const sheet = await screen.findByRole('dialog', { name: 'New entry' }, { timeout: 10_000 });
    expect(
      await within(sheet).findByRole('button', {
        name: 'Memory date: Today, Tue 24 Nov. Change it',
      }),
    ).toBeVisible();
  });

  test('keeps Save disabled for an empty entry (D-23)', async () => {
    const { user, sheet } = await openCapture();
    expect(saveButton(sheet)).toBeDisabled();

    await user.type(within(sheet).getByRole('textbox', { name: 'Title (optional)' }), '   ');
    expect(saveButton(sheet)).toBeDisabled();

    await user.type(within(sheet).getByRole('textbox', { name: 'Title (optional)' }), 'Quiet');
    expect(saveButton(sheet)).toBeEnabled();
  });

  test('saves a title, a tag and two photos, in the order shown, and the entry appears in this week of the feed', async () => {
    const { user, sheet } = await openCapture();

    await user.type(
      within(sheet).getByRole('textbox', { name: 'Title (optional)' }),
      'Dumplings on Lygon',
    );
    await user.click(within(sheet).getByRole('button', { name: 'Add a tag' }));
    await user.click(await screen.findByRole('button', { name: 'Family' }));
    await user.upload(within(sheet).getByLabelText('Add photos'), [
      photoFile('one.jpg'),
      photoFile('two.jpg'),
    ]);
    expect(
      await within(sheet).findByRole('button', { name: 'Photo 2 of 2. Options' }),
    ).toBeVisible();

    // Move the second photo first, from its menu (the keyboard's way to reorder).
    await user.click(within(sheet).getByRole('button', { name: 'Photo 2 of 2. Options' }));
    await user.click(await screen.findByRole('button', { name: 'Move earlier' }));

    await user.click(saveButton(sheet));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    expect(sentEntry()?.body).toMatchObject({
      title: 'Dumplings on Lygon',
      tagIds: ['tag-family'],
      entryDate: '2026-11-24',
    });
    const uploads = backend.state.calls.filter((c) => c.path.endsWith('/media'));
    expect(uploads.map((c) => (c.body as { name: string }).name)).toEqual(['two.jpg', 'one.jpg']);
    expect(backend.state.calls.at(-1)?.path).toMatch(/\/media\/order$/);

    const thisWeek = await screen.findByRole('region', { name: 'This week · 2 entries' });
    expect(within(thisWeek).getByRole('heading', { name: 'Dumplings on Lygon' })).toBeVisible();
    // The rest of the feed is as it was: the sealed Loot Drop still shows nothing but its dates (D-10).
    expect(screen.getByRole('article', { name: 'Sealed Loot Drop' })).toBeVisible();
    // Saved, so the draft is gone (D-23).
    expect(readDraft()).toBeNull();
  });

  test('Song brings the soundtrack field into focus; a pasted link is saved as its URI and kept in the draft (D-35)', async () => {
    const { user, sheet } = await openCapture();
    await user.type(
      within(sheet).getByRole('textbox', { name: 'Title (optional)' }),
      'Night drive',
    );

    await user.click(within(sheet).getByRole('button', { name: 'Song' }));
    const field = within(sheet).getByLabelText('Soundtrack');
    expect(field).toHaveFocus();
    await user.paste('https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=share');
    expect(
      await within(sheet).findByRole('button', {
        name: 'Remove the soundtrack, song on Spotify',
      }),
    ).toBeVisible();
    // The draft holds it, so a reload doesn't lose it (D-23).
    await waitFor(() =>
      expect(localStorage.getItem('memoirist.capture-draft')).toContain(
        'spotify:track:4uLU6hMCjMI75M1A2tKUQC',
      ),
    );

    await user.click(saveButton(sheet));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(sentEntry()?.body).toMatchObject({
      title: 'Night drive',
      soundtrack: 'spotify:track:4uLU6hMCjMI75M1A2tKUQC',
    });
  });

  test('does not save a template prompt left empty, and keeps one that was answered (D-06)', async () => {
    writeDraft({
      ...baseDraft,
      templateId: 'template-night',
      prompts: ['Who was there', 'The best bit'],
      content: {
        type: 'doc',
        content: [
          {
            type: 'heading',
            attrs: { level: 2 },
            content: [{ type: 'text', text: 'Who was there' }],
          },
          { type: 'paragraph', content: [{ type: 'text', text: 'Mei and Jonno' }] },
          {
            type: 'heading',
            attrs: { level: 2 },
            content: [{ type: 'text', text: 'The best bit' }],
          },
          { type: 'paragraph' },
        ],
      },
    });
    const { user, sheet } = await openCapture();
    await user.click(saveButton(sheet));
    await waitFor(() => expect(sentEntry()).toBeDefined());

    const sent = JSON.stringify((sentEntry()?.body as { contentJson: unknown }).contentJson);
    expect(sent).toContain('Who was there');
    expect(sent).toContain('Mei and Jonno');
    expect(sent).not.toContain('The best bit');
  });

  test('a chosen template with nothing written leaves Save disabled, so its empty prompts are never saved', async () => {
    const { user, sheet } = await openCapture();
    await user.click(within(sheet).getByRole('button', { name: 'Choose a template' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Night out' }));

    expect(
      await within(sheet).findByRole('button', { name: 'Template: Night out. Change it' }),
    ).toBeVisible();
    expect(within(sheet).getByRole('heading', { name: 'Who was there' })).toBeVisible();
    expect(saveButton(sheet)).toBeDisabled();
  });

  test('uploads a photo marked as a diary page as a page (D-20), and Diary page marks its photos itself', async () => {
    const { user, sheet } = await openCapture();
    await user.type(within(sheet).getByRole('textbox', { name: 'Title (optional)' }), 'Notebook');
    await user.upload(within(sheet).getByLabelText('Add photos'), photoFile('page.jpg'));
    await user.click(await within(sheet).findByRole('button', { name: 'Photo 1 of 1. Options' }));
    await user.click(await screen.findByRole('button', { name: 'Mark as a diary page' }));
    expect(within(sheet).getByRole('button', { name: 'Diary page 1 of 1. Options' })).toBeVisible();

    await user.click(saveButton(sheet));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(backend.state.calls.find((c) => c.path.endsWith('/media'))?.body).toMatchObject({
      purpose: 'PAGE_SCAN',
    });
  });

  test('opened as Diary page, every photo added is a page', async () => {
    compress.mockClear();
    const { user, sheet } = await openCapture('/?write=page');
    expect(within(sheet).getByRole('heading', { name: 'Diary page' })).toBeVisible();
    await user.upload(within(sheet).getByLabelText('Add photos'), photoFile('page.jpg'));
    expect(
      await within(sheet).findByRole('button', { name: 'Diary page 1 of 1. Options' }),
    ).toBeVisible();
  });

  test('a backdated save shows a toast that names its week and goes there (G-04)', async () => {
    const { user, sheet, router } = await openCapture();
    await user.type(within(sheet).getByRole('textbox', { name: 'Title (optional)' }), 'Friday');
    await user.click(within(sheet).getByRole('button', { name: /Memory date/ }));
    await user.click(await screen.findByRole('button', { name: /November 20th, 2026/ }));
    expect(
      within(sheet).getByRole('button', { name: 'Memory date: Fri 20 Nov. Change it' }),
    ).toBeVisible();
    // The future can't be picked (D-09).
    expect(screen.queryByRole('button', { name: /November 25th, 2026/ })).not.toBeInTheDocument();

    await user.click(saveButton(sheet));
    const go = await screen.findByRole('button', { name: 'Go there' });
    expect(screen.getByText('Saved in the week of 16–22 November')).toBeVisible();
    await user.click(go);
    // The week page is a lazy route (task 4.12): the navigation commits once its chunk has loaded.
    await waitFor(() => expect(router.state.location.pathname).toBe('/weeks/week-2026-11-16'));
  });

  test('a reload keeps the draft (D-23)', async () => {
    vi.useRealTimers();
    const first = await openCapture();
    await first.user.type(
      within(first.sheet).getByRole('textbox', { name: 'Title (optional)' }),
      'Half written',
    );
    await waitFor(() => expect(readDraft()?.title).toBe('Half written'));
    expect(await within(first.sheet).findByText(/^Draft saved \d+:\d\d (am|pm)$/)).toBeVisible();
    first.unmount();
    useUiStore.setState({ capture: null });

    const second = await openCapture();
    expect(within(second.sheet).getByRole('textbox', { name: 'Title (optional)' })).toHaveValue(
      'Half written',
    );
  });

  test('after a partial save and a reload, the uploaded photo is back in the tray and the next save keeps it (D-23)', async () => {
    vi.useRealTimers();
    backend.state.failUploads.add(2);
    const first = await openCapture();
    await first.user.upload(within(first.sheet).getByLabelText('Add photos'), [
      photoFile('one.jpg'),
      photoFile('two.jpg'),
    ]);
    await within(first.sheet).findByRole('button', { name: 'Photo 2 of 2. Options' });
    await first.user.click(saveButton(first.sheet));
    expect(await within(first.sheet).findByText(/Saved, but 1 photo didn’t upload/)).toBeVisible();
    await waitFor(() => expect(readDraft()?.entryId).toEqual(expect.any(String)));
    first.unmount();
    useUiStore.setState({ capture: null });

    // Only the photo that uploaded comes back: the failed file was never on the server, and a File can't be drafted.
    const second = await openCapture();
    expect(
      await within(second.sheet).findByRole('button', { name: 'Photo 1 of 1. Options' }),
    ).toBeVisible();
    await second.user.upload(within(second.sheet).getByLabelText('Add photos'), [
      photoFile('three.jpg'),
      photoFile('four.jpg'),
    ]);
    await within(second.sheet).findByRole('button', { name: 'Photo 3 of 3. Options' });
    await second.user.click(saveButton(second.sheet));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    expect(backend.state.calls.at(-1)?.body).toEqual({
      mediaIds: ['media-new-1', 'media-new-2', 'media-new-3'],
    });
    expect(backend.state.calls.some((c) => c.method === 'DELETE')).toBe(false);
    expect(backend.state.entries.size).toBe(1);
  });

  test('closing with unsaved changes asks Keep editing or Discard, and Discard clears the draft (D-23)', async () => {
    vi.useRealTimers();
    const { user, sheet } = await openCapture();
    await user.type(within(sheet).getByRole('textbox', { name: 'Title (optional)' }), 'Maybe');
    await waitFor(() => expect(readDraft()).not.toBeNull());

    await user.keyboard('{Escape}');
    const ask = await screen.findByRole('alertdialog', { name: 'Discard this entry?' });
    await user.click(within(ask).getByRole('button', { name: 'Keep editing' }));
    expect(screen.getByRole('dialog', { name: 'New entry' })).toBeVisible();

    await user.click(within(sheet).getByRole('button', { name: 'Cancel' }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Discard' }),
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(readDraft()).toBeNull();
  });

  test('shows the API’s validation errors on the fields they name, in the app’s words', async () => {
    server.use(
      http.post('*/api/v1/entries', () =>
        backend.problem(400, 'validation-failed', {
          title: 'size must be between 1 and 150',
          entryDate: 'must not be after today',
        }),
      ),
    );
    const { user, sheet } = await openCapture();
    await user.type(
      within(sheet).getByRole('textbox', { name: 'Title (optional)' }),
      'Too long, says the server',
    );
    await user.click(saveButton(sheet));

    expect(await within(sheet).findByText('A title is at most 150 characters.')).toBeVisible();
    expect(within(sheet).getByText('The memory date can’t be after today.')).toBeVisible();
    expect(within(sheet).getByRole('alert')).toHaveTextContent('Some details need another look.');
    expect(within(sheet).queryByText(/Server detail/)).not.toBeInTheDocument();
    expect(within(sheet).getByRole('textbox', { name: 'Title (optional)' })).toHaveAttribute(
      'aria-invalid',
      'true',
    );
  });

  test('a 409 entry-stale keeps the typed text and offers to load the latest version (D-23)', async () => {
    backend.state.entries.set(
      'entry-saved',
      entry('entry-saved', '2026-11-24', {
        title: 'Changed on the phone',
        version: 3,
        weeklyLogId: 'week-this',
      }),
    );
    writeDraft({
      ...baseDraft,
      clientId: 'entry-saved',
      entryId: 'entry-saved',
      entryVersion: 1,
      title: 'Typed on the laptop',
    });
    const { user, sheet } = await openCapture();

    // Save waits for the restored entry to be read back (its soundtrack and reply are sent on the PUT, D-26).
    await waitFor(() => expect(saveButton(sheet)).toBeEnabled());
    await user.click(saveButton(sheet));
    const alert = await within(sheet).findByRole('alert');
    expect(alert).toHaveTextContent(
      'This entry changed somewhere else. Everything you typed is still here.',
    );
    expect(within(sheet).getByRole('textbox', { name: 'Title (optional)' })).toHaveValue(
      'Typed on the laptop',
    );
    expect(backend.state.entries.get('entry-saved')?.title).toBe('Changed on the phone');

    await user.click(within(alert).getByRole('button', { name: 'Load the latest version' }));
    await waitFor(() =>
      expect(within(sheet).getByRole('textbox', { name: 'Title (optional)' })).toHaveValue(
        'Changed on the phone',
      ),
    );
    // With the latest version loaded, the next save goes through.
    await user.click(saveButton(sheet));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(backend.state.calls.filter((c) => c.method === 'PUT').at(-1)?.body).toMatchObject({
      version: 3,
      tagIds: [],
    });
  });

  test('a restored draft whose entry was deleted elsewhere saves as a new entry with a new id (D-12)', async () => {
    vi.useRealTimers();
    writeDraft({
      ...baseDraft,
      clientId: 'entry-gone',
      entryId: 'entry-gone',
      entryVersion: 2,
      title: 'Still here',
    });
    const { user, sheet } = await openCapture();

    await waitFor(() => expect(saveButton(sheet)).toBeEnabled());
    await user.click(saveButton(sheet));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    expect(backend.state.calls.some((c) => c.method === 'PUT')).toBe(false);
    const posted = sentEntry()?.body as { id: string; title: string };
    expect(posted.title).toBe('Still here');
    expect(posted.id).not.toBe('entry-gone');
  });

  test('a restored draft whose entry is now sealed shows the Loot Drop message and keeps Save off (D-10)', async () => {
    vi.useRealTimers();
    backend.state.entries.set(
      'entry-sealed',
      sealedEntry('entry-sealed', '2026-11-20', '2027-01-01T00:00:00Z'),
    );
    writeDraft({
      ...baseDraft,
      clientId: 'entry-sealed',
      entryId: 'entry-sealed',
      entryVersion: 1,
      title: 'Typed before the seal',
    });
    const { sheet } = await openCapture();

    expect(
      await within(sheet).findByText(
        'This entry is now a sealed Loot Drop, so it can’t be changed.',
      ),
    ).toBeVisible();
    expect(saveButton(sheet)).toBeDisabled();
    // Nothing of the sealed entry reaches the tray.
    expect(within(sheet).queryByRole('button', { name: /^Photo \d+ of/ })).not.toBeInTheDocument();
    expect(backend.state.calls.some((c) => c.method === 'PUT' || c.method === 'POST')).toBe(false);
  });

  test('refuses a file type the API won’t take, before sending anything (D-17)', async () => {
    const { sheet } = await openCapture();
    // A browser's picker can still hand over a file outside `accept` (drag and drop, "All files").
    const user = userEvent.setup({ applyAccept: false });
    const heic = new File([new Uint8Array(10)], 'IMG_1.heic', { type: 'image/heic' });
    await user.upload(within(sheet).getByLabelText('Add photos'), heic);
    expect(
      await within(sheet).findByText(/IMG_1.heic: This kind of file can’t be added/),
    ).toBeVisible();
    expect(within(sheet).queryByRole('button', { name: /Photo 1 of/ })).not.toBeInTheDocument();
  });
});

// Sealing from capture (task 4.8, F-06, D-10, D-23): Seal opens the panel, a day is picked, the opening moment is
// read back, a confirmation repeats it, then the entry is saved and sealed last.
describe('sealing from capture', { timeout: 15_000 }, () => {
  async function sealFor(sheet: HTMLElement, user: ReturnType<typeof userEvent.setup>) {
    await user.click(within(sheet).getByRole('button', { name: 'Seal' }));
    const panel = await screen.findByRole('dialog', { name: 'Seal as Loot Drop' }, LAZY);
    await user.click(within(panel).getByRole('button', { name: 'Pick a date' }));
    // Today can't be picked: a Loot Drop opens on a later day (F-06).
    expect(await screen.findByRole('button', { name: /November 24th, 2026/ })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: /November 30th, 2026/ }));
    expect(within(panel).getByText(/Monday 30 November 2026 at/)).toHaveTextContent(
      'Monday 30 November 2026 at 9:00 am',
    );
    await user.click(within(panel).getByRole('button', { name: 'Seal it' }));
    const confirm = await screen.findByRole('alertdialog', { name: 'Seal this Loot Drop?' });
    expect(confirm).toHaveTextContent('Opens Monday 30 November 2026 at 9:00 am, in 6 days.');
    await user.click(within(confirm).getByRole('button', { name: 'Seal it' }));
    return panel;
  }

  test('Seal is unavailable for an empty entry (D-23)', async () => {
    const { sheet } = await openCapture();
    expect(within(sheet).getByRole('button', { name: 'Seal' })).toBeDisabled();
  });

  test('saves the entry, then seals it to open at 9:00 am, and the feed shows only a sealed card', async () => {
    const { user, sheet } = await openCapture();
    await user.type(
      within(sheet).getByRole('textbox', { name: 'Title (optional)' }),
      'A letter to next week',
    );
    await sealFor(sheet, user);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // Created first, sealed last (D-10), at 9:00 am on 30 November in Melbourne: 22:00 UTC the day before.
    const writes = backend.state.calls.filter((c) => c.method !== 'GET').map((c) => c.path);
    expect(writes.at(0)).toBe('/entries');
    expect(writes.at(-1)).toMatch(/\/seal$/);
    expect(backend.state.calls.at(-1)?.body).toEqual({ unlockDate: '2026-11-29T22:00:00.000Z' });

    const thisWeek = await screen.findByRole('region', { name: 'This week · 1 entry' });
    const cards = within(thisWeek).getAllByRole('article', { name: 'Sealed Loot Drop' });
    expect(cards).toHaveLength(1);
    expect(within(cards[0] as HTMLElement).getByRole('link')).toHaveAccessibleName(
      'Sealed Loot Drop. Opens Mon 30 Nov 2026, in 6 days.',
    );
    expect(screen.queryByText('A letter to next week')).not.toBeInTheDocument();
    expect(readDraft()).toBeNull();
  });

  test('sealing a restored saved entry leaves no whole copy in the cache: its page shows only the Loot Drop (D-10)', async () => {
    backend.state.entries.set(
      'entry-saved',
      entry('entry-saved', '2026-11-24', {
        title: 'Read back when restored',
        version: 1,
        weeklyLogId: 'week-this',
      }),
    );
    writeDraft({
      ...baseDraft,
      clientId: 'entry-saved',
      entryId: 'entry-saved',
      entryVersion: 1,
      title: 'Typed before sealing',
    });
    const { user, sheet, router } = await openCapture();
    // Capture reads the saved entry back whole (GET /entries/{id}), which puts it in the entry's cache.
    await waitFor(() => expect(saveButton(sheet)).toBeEnabled());
    await sealFor(sheet, user);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(backend.state.entries.get('entry-saved')?.isLocked).toBe(true);

    // Within the cache's lifetime, the entry's page must draw the sealed answer, not the copy read on restore.
    await router.navigate('/entries/entry-saved');
    expect(await screen.findByText(/Its light is still on its way to you/)).toBeVisible();
    expect(screen.queryByText('Read back when restored')).not.toBeInTheDocument();
    expect(screen.queryByText('Typed before sealing')).not.toBeInTheDocument();
  });

  test('a seal that fails leaves the entry saved, says it isn’t sealed, and seals it on the next try', async () => {
    backend.state.failSeal = true;
    const { user, sheet } = await openCapture();
    await user.type(within(sheet).getByRole('textbox', { name: 'Title (optional)' }), 'Later');
    const panel = await sealFor(sheet, user);
    expect(await within(panel).findByRole('alert')).toHaveTextContent(
      'Saved, but not sealed. Try again.',
    );
    expect(backend.state.entries.size).toBe(1);

    backend.state.failSeal = false;
    // The day picked is kept, so trying again is one press and the confirmation.
    expect(
      within(panel).getByRole('button', { name: 'Opening day: Mon 30 Nov. Change it' }),
    ).toBeVisible();
    await user.click(within(panel).getByRole('button', { name: 'Seal it' }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Seal it' }),
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    // The retry replaced the saved entry (PUT with its version) instead of making a second one (D-12, D-23).
    expect(backend.state.entries.size).toBe(1);
    expect(
      backend.state.calls.filter((c) => c.method === 'PUT' && c.path.startsWith('/entries/')),
    ).toHaveLength(1);
    expect([...backend.state.entries.values()][0]?.isLocked).toBe(true);
  });
});

// Task 4.13 (D-34): a photo's own date, read before it is shrunk, offered as the memory date. The photos are real JPEG
// bytes with an EXIF date (test/exif.ts), read by the real exifr.
describe('photo dates in capture', { timeout: 15_000 }, () => {
  test('offers one photo’s own date as the memory date, and uses it when accepted', async () => {
    const { user, sheet } = await openCapture();
    await user.upload(within(sheet).getByLabelText('Add photos'), [
      datedPhoto('beach.jpg', '2026:11:17 10:00:00', '+11:00'),
    ]);
    const offer = await within(sheet).findByRole('group', { name: 'Photo date' });
    expect(within(offer).getByText('Taken Tue 17 Nov')).toBeVisible();

    await user.click(within(offer).getByRole('button', { name: 'Use this date' }));
    const chip = within(sheet).getByRole('button', { name: 'Memory date: Tue 17 Nov. Change it' });
    expect(within(sheet).queryByRole('group', { name: 'Photo date' })).not.toBeInTheDocument();
    await waitFor(() => expect(chip).toHaveFocus());

    await user.click(saveButton(sheet));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(sentEntry()?.body).toMatchObject({ entryDate: '2026-11-17' });
    expect(sentEntry()?.body).not.toHaveProperty('dateUnknown');
  });

  test('keeps the memory date when the offer is dismissed', async () => {
    const { user, sheet } = await openCapture();
    await user.upload(within(sheet).getByLabelText('Add photos'), [
      datedPhoto('beach.jpg', '2026:11:17 10:00:00'),
    ]);
    const offer = await within(sheet).findByRole('group', { name: 'Photo date' });
    await user.click(within(offer).getByRole('button', { name: 'Not now' }));
    expect(within(sheet).queryByRole('group', { name: 'Photo date' })).not.toBeInTheDocument();
    const chip = within(sheet).getByRole('button', {
      name: 'Memory date: Today, Tue 24 Nov. Change it',
    });
    await waitFor(() => expect(chip).toHaveFocus());

    await user.click(saveButton(sheet));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(sentEntry()?.body).toMatchObject({ entryDate: '2026-11-24' });
  });

  test('offers the dated photo’s day when an undated photo is picked with it (4.13 review fix)', async () => {
    const { user, sheet } = await openCapture();
    await user.upload(within(sheet).getByLabelText('Add photos'), [
      datedPhoto('beach.jpg', '2026:11:17 10:00:00'),
      datedPhoto('screenshot.jpg', null),
    ]);
    const offer = await within(sheet).findByRole('group', { name: 'Photo date' });
    expect(within(offer).getByText('Taken Tue 17 Nov')).toBeVisible();
  });

  test('offers nothing for a photo with no date, one taken today, or photos from two days', async () => {
    const { user, sheet } = await openCapture();
    const input = within(sheet).getByLabelText('Add photos');
    await user.upload(input, [datedPhoto('screenshot.jpg', null)]);
    await within(sheet).findByRole('button', { name: 'Photo 1 of 1. Options' });
    await user.upload(input, [datedPhoto('now.jpg', '2026:11:24 21:00:00')]);
    await within(sheet).findByRole('button', { name: 'Photo 2 of 2. Options' });
    await user.upload(input, [
      datedPhoto('a.jpg', '2026:11:17 10:00:00'),
      datedPhoto('b.jpg', '2026:11:18 10:00:00'),
    ]);
    await within(sheet).findByRole('button', { name: 'Photo 4 of 4. Options' });
    // A camera clock set to the future gives no offer either (D-09).
    await user.upload(input, [datedPhoto('future.jpg', '2031:01:01 10:00:00')]);
    await within(sheet).findByRole('button', { name: 'Photo 5 of 5. Options' });
    expect(within(sheet).queryByRole('group', { name: 'Photo date' })).not.toBeInTheDocument();
  });

  test('uploads a diary page with the gentler shrink, as a page scan (TECH_STACK §3 Media)', async () => {
    const { user, sheet } = await openCapture('/?write=page');
    await user.upload(within(sheet).getByLabelText('Add photos'), [photoFile('page.jpg')]);
    await within(sheet).findByRole('button', { name: 'Diary page 1 of 1. Options' });
    await user.click(saveButton(sheet));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    const upload = backend.state.calls.find((c) => c.path.endsWith('/media'));
    expect(upload?.body).toMatchObject({ purpose: 'PAGE_SCAN', name: 'page.jpg' });
    // Shrunk once, when it uploaded and its purpose was known, with the page setting.
    expect(compress).toHaveBeenCalledTimes(1);
    expect(compress).toHaveBeenLastCalledWith(
      expect.objectContaining({ name: 'page.jpg' }),
      expect.objectContaining({ maxWidthOrHeight: 4096 }),
    );
  });
});

const baseDraft: Draft = {
  kind: 'entry',
  clientId: 'client-draft',
  entryId: null,
  entryVersion: null,
  entryDate: '2026-11-24',
  templateId: null,
  prompts: [],
  title: '',
  content: null,
  tagIds: [],
  savedAt: '2026-11-24T12:40:00.000Z',
};

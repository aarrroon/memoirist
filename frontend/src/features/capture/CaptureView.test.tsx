import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { useUiStore } from '../../stores/ui';
import { fakeBackend } from '../../test/msw/capture';
import { entry, sealedEntry } from '../../test/msw/feed';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';
import { readDraft, writeDraft, type Draft } from './draft';

// Capture (task 4.6) against a fake entry API (MSW), driven the way a user would: through the sheet, by keyboard and
// pointer. Each test names the Done-when item of SPRINT_PLAN 4.6 it proves. jsdom has no layout, so capture is the
// phone sheet here (no media query matches); the laptop dialog is checked in the browser (audit.mjs).

// Shrinking needs a canvas, which jsdom lacks: the file goes up as it was picked.
vi.mock('browser-image-compression', () => ({ default: (file: File) => Promise.resolve(file) }));

// 23:47 on Tuesday 24 November 2026 in Melbourne, as in the feed's tests. Only Date is faked.
const NOW = new Date('2026-11-24T12:47:00Z');

let backend: ReturnType<typeof fakeBackend>;

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
    expect(router.state.location.pathname).toBe('/weeks/week-2026-11-16');
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

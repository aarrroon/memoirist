import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeAll, beforeEach, describe, expect, test, vi } from 'vitest';
import { useUiStore } from '../../stores/ui';
import { datedPhoto } from '../../test/exif';
import { fakeBackend } from '../../test/msw/capture';
import { entry, page, sampleWeeks, sealedEntry, week } from '../../test/msw/feed';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';
import { readDraft, writeDraft } from './draft';

// The photo dump (D-34, F-19, task 4.13) against the fake entry API, driven through the sheet as a user would. The
// photos are real JPEG bytes with an EXIF date (test/exif.ts), so the real exifr reads them, as in the browser. Each
// test names the Done-when item of SPRINT_PLAN 4.13 it proves.

// Shrinking needs a canvas, which jsdom lacks: the file goes up as it was picked.
vi.mock('browser-image-compression', () => ({ default: (file: File) => Promise.resolve(file) }));

// 23:47 on Tuesday 24 November 2026 in Melbourne, as in capture's tests. Only Date is faked.
const NOW = new Date('2026-11-24T12:47:00Z');

let backend: ReturnType<typeof fakeBackend>;

beforeAll(async () => {
  await Promise.all([import('./CaptureView'), import('../../components/MemoryDayPicker')]);
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

async function openDump() {
  const user = userEvent.setup();
  renderApp('/?write=dump');
  const sheet = await screen.findByRole('dialog', { name: 'Photo dump' }, { timeout: 10_000 });
  await within(sheet).findByRole('button', { name: 'Choose photos' });
  return { user, sheet };
}

const pick = (user: ReturnType<typeof userEvent.setup>, sheet: HTMLElement, files: File[]) =>
  user.upload(within(sheet).getByLabelText('Photos to file'), files);

const created = () =>
  backend.state.calls
    .filter((c) => c.method === 'POST' && c.path === '/entries')
    .map((c) => c.body as Record<string, unknown>);
const uploads = () => backend.state.calls.filter((c) => c.path.endsWith('/media'));

const fileThem = (sheet: HTMLElement) => within(sheet).getByRole('button', { name: 'File them' });

describe('photo dump', { timeout: 20_000 }, () => {
  test('opens from the Write menu', async () => {
    const user = userEvent.setup();
    renderApp('/');
    await user.click((await screen.findAllByRole('button', { name: 'Write' }))[0] as HTMLElement);
    await user.click(await screen.findByRole('menuitem', { name: 'Photo dump' }));
    const sheet = await screen.findByRole('dialog', { name: 'Photo dump' }, { timeout: 10_000 });
    expect(fileThem(sheet)).toBeDisabled();
  });

  test('photos from three days become three entries, each on its own day (Done when 1)', async () => {
    const { user, sheet } = await openDump();
    await pick(user, sheet, [
      datedPhoto('rocks.jpg', '2026:10:24 07:15:00'),
      datedPhoto('peak.jpg', '2026:10:23 16:00:00', '+11:00'),
      datedPhoto('fire.jpg', '2026:10:23 21:30:00'),
      datedPhoto('drive.jpg', '2026:10:26 12:00:00'),
    ]);

    // Grouped by day, oldest first, each with its count (F-19 step 2).
    const friday = await within(sheet).findByRole('region', { name: 'Friday 23 October' });
    expect(within(friday).getByText('2 photos')).toBeVisible();
    expect(within(sheet).getByRole('region', { name: 'Saturday 24 October' })).toBeVisible();
    expect(within(sheet).getByRole('region', { name: 'Monday 26 October' })).toBeVisible();
    expect(within(sheet).getByText('4 photos')).toBeVisible();

    await user.click(fileThem(sheet));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // One backdated entry per day, with no caption and the client's own id (D-12).
    expect(created().map((b) => b.entryDate)).toEqual(['2026-10-23', '2026-10-24', '2026-10-26']);
    expect(created().every((b) => typeof b.id === 'string' && b.title === undefined)).toBe(true);
    expect(created().some((b) => 'dateUnknown' in b)).toBe(false);
    const entries = [...backend.state.entries.values()];
    expect(entries.map((e) => e.media.length)).toEqual([2, 1, 1]);
    expect(uploads().map((c) => (c.body as { name: string }).name)).toEqual([
      'peak.jpg',
      'fire.jpg',
      'rocks.jpg',
      'drive.jpg',
    ]);
    // The toast names the weeks they landed in (F-19 step 4).
    expect(
      await screen.findByText('Filed 3 entries in 2 weeks, from the week of 19–25 October'),
    ).toBeVisible();
    expect(screen.getByRole('button', { name: 'Go there' })).toBeVisible();
  });

  test('a 25-photo day becomes two entries of 20 and 5 (Done when 2)', async () => {
    const { user, sheet } = await openDump();
    const many = Array.from({ length: 25 }, (_, i) =>
      datedPhoto(`p${i + 1}.jpg`, '2026:10:23 10:00:00'),
    );
    await pick(user, sheet, many);

    const friday = await within(sheet).findByRole('region', { name: 'Friday 23 October' });
    expect(within(friday).getByText('25 photos')).toBeVisible();
    expect(within(friday).getByText(/Filed as 2 entries/)).toBeVisible();

    await user.click(fileThem(sheet));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    expect(created().map((b) => b.entryDate)).toEqual(['2026-10-23', '2026-10-23']);
    expect(new Set(created().map((b) => b.id)).size).toBe(2);
    expect([...backend.state.entries.values()].map((e) => e.media.length)).toEqual([20, 5]);
    // Each entry's order names exactly its own photos (the fake answers 400 otherwise).
    expect(backend.state.calls.filter((c) => c.path.endsWith('/media/order'))).toHaveLength(2);
  });

  test('undated photos make one entry with the date unknown badge, which goes once the date is changed (Done when 3)', async () => {
    const { user, sheet } = await openDump();
    await pick(user, sheet, [
      datedPhoto('coffee.jpg', null),
      datedPhoto('screenshot.jpg', null),
      datedPhoto('peak.jpg', '2026:10:23 16:00:00'),
    ]);
    const unknown = await within(sheet).findByRole('region', { name: 'Date unknown' });
    expect(within(unknown).getByText('Filed under today until you change its date.')).toBeVisible();
    expect(within(unknown).getByRole('button', { name: 'Change date' })).toBeVisible();

    await user.click(fileThem(sheet));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // Dated today and marked (D-34): two entries in all, the undated one last.
    expect(created()).toHaveLength(2);
    expect(created()[1]).toMatchObject({ entryDate: '2026-11-24', dateUnknown: true });

    // The feed shows the badge on that entry only, never on the sealed Loot Drop beside it (D-10).
    const badge = await screen.findByRole('button', { name: 'Date unknown, fix it' });
    expect(screen.getAllByRole('button', { name: 'Date unknown, fix it' })).toHaveLength(1);
    const sealed = screen.getByRole('article', { name: 'Sealed Loot Drop' });
    expect(within(sealed).queryByRole('button', { name: /Date unknown/ })).not.toBeInTheDocument();

    await user.click(badge);
    await user.click(await screen.findByRole('button', { name: /November 20th, 2026/ }));

    // A full replace (D-26) with the new date and the version read; the mark itself is never sent on a PUT (D-34).
    await waitFor(() =>
      expect(
        backend.state.calls.some((c) => c.method === 'PUT' && !c.path.includes('/media')),
      ).toBe(true),
    );
    const put = backend.state.calls.find((c) => c.method === 'PUT' && !c.path.includes('/media'));
    expect(put?.body).toMatchObject({ entryDate: '2026-11-20', version: 0, tagIds: [] });
    expect(put?.body).not.toHaveProperty('dateUnknown');
    await waitFor(() =>
      expect(
        screen.queryByRole('button', { name: 'Date unknown, fix it' }),
      ).not.toBeInTheDocument(),
    );
  });

  test('Change date files the undated photos on the day picked, unmarked', async () => {
    const { user, sheet } = await openDump();
    await pick(user, sheet, [datedPhoto('coffee.jpg', null)]);
    await user.click(await within(sheet).findByRole('button', { name: 'Change date' }));
    await user.click(await screen.findByRole('button', { name: /November 20th, 2026/ }));
    expect(await within(sheet).findByRole('region', { name: 'Friday 20 November' })).toBeVisible();
    expect(within(sheet).queryByRole('region', { name: 'Date unknown' })).not.toBeInTheDocument();

    await user.click(fileThem(sheet));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(created()).toEqual([expect.objectContaining({ entryDate: '2026-11-20' })]);
    expect(created()[0]).not.toHaveProperty('dateUnknown');
  });

  test('a failed upload is retried without a duplicate entry (Done when 4)', async () => {
    const { user, sheet } = await openDump();
    backend.state.failUploads.add(2);
    await pick(user, sheet, [
      datedPhoto('a.jpg', '2026:10:23 10:00:00'),
      datedPhoto('b.jpg', '2026:10:23 11:00:00'),
      datedPhoto('c.jpg', '2026:10:23 12:00:00'),
    ]);
    await within(sheet).findByRole('region', { name: 'Friday 23 October' });

    await user.click(fileThem(sheet));
    expect(
      await within(sheet).findByText(
        '1 photo didn’t upload. The rest are filed. Try again to send it.',
      ),
    ).toBeVisible();
    // The server's own words never show; problemMessage's do.
    expect(within(sheet).queryByText(/Server detail/)).not.toBeInTheDocument();
    expect(within(sheet).getByText('Didn’t upload')).toBeInTheDocument();

    await user.click(within(sheet).getByRole('button', { name: 'Try again' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // The same client id twice: the second POST got the same entry back (D-12), and only b.jpg went up again.
    const ids = created().map((b) => b.id);
    expect(ids).toHaveLength(2);
    expect(ids[0]).toBe(ids[1]);
    expect(backend.state.entries.size).toBe(1);
    expect(uploads().map((c) => (c.body as { name: string }).name)).toEqual([
      'a.jpg',
      'b.jpg',
      'c.jpg',
      'b.jpg',
    ]);
    expect([...backend.state.entries.values()][0]?.media).toHaveLength(3);
  });

  test('marks a file it can’t take, and keeps the rest', async () => {
    const { user, sheet } = await openDump();
    // user.upload honours the input's accept list, so this goes past it, as a drop or an odd browser could.
    await userEvent
      .setup({ applyAccept: false })
      .upload(within(sheet).getByLabelText('Photos to file'), [
        new File([new Uint8Array(10)], 'IMG_1.heic', { type: 'image/heic' }),
        datedPhoto('peak.jpg', '2026:10:23 16:00:00'),
      ]);
    expect(await within(sheet).findByText(/IMG_1\.heic: not supported/)).toBeVisible();
    expect(within(sheet).getByRole('region', { name: 'Friday 23 October' })).toBeVisible();
    expect(user).toBeDefined();
  });

  test('cancelling with photos picked asks first, and leaves a New entry draft alone', async () => {
    writeDraft({
      kind: 'entry',
      clientId: '00000000-0000-4000-8000-000000000001',
      entryId: null,
      entryVersion: null,
      entryDate: '2026-11-24',
      templateId: null,
      prompts: [],
      title: 'Half written',
      content: null,
      tagIds: [],
      savedAt: NOW.toISOString(),
    });
    const { user, sheet } = await openDump();
    await pick(user, sheet, [datedPhoto('peak.jpg', '2026:10:23 16:00:00')]);
    await within(sheet).findByRole('region', { name: 'Friday 23 October' });

    await user.click(within(sheet).getByRole('button', { name: 'Cancel' }));
    const ask = await screen.findByRole('alertdialog', { name: 'Leave these photos out?' });
    await user.click(within(ask).getByRole('button', { name: 'Discard' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(created()).toHaveLength(0);
    expect(readDraft()?.title).toBe('Half written');
  });

  test('the badge never shows on a sealed entry, even one whose date is unknown (D-10)', async () => {
    const hidden = {
      ...sealedEntry('sealed-unknown', '2026-11-23', '2027-01-01T22:00:00Z'),
      dateUnknown: true,
    };
    const open = entry('open-unknown', '2026-11-24', { dateUnknown: true, title: 'Coffee' });
    server.use(
      http.get('*/api/v1/feed', () =>
        HttpResponse.json(
          page([
            week('week-now', '2026-11-23', '2026-11-29', [], [hidden, open]),
            ...sampleWeeks.slice(1),
          ]),
        ),
      ),
    );
    renderApp('/');
    expect(await screen.findByRole('heading', { name: 'Coffee' })).toBeVisible();
    expect(screen.getAllByRole('button', { name: 'Date unknown, fix it' })).toHaveLength(1);
    const sealedCards = screen.getAllByRole('article', { name: 'Sealed Loot Drop' });
    for (const card of sealedCards) {
      expect(within(card).queryByRole('button', { name: /Date unknown/ })).not.toBeInTheDocument();
    }
  });
});

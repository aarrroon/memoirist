import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { brunswick, detailOf, eraWeek, sampleEras } from '../../test/msw/eras';
import { entry, photo, sealedEntry, voiceNote, week } from '../../test/msw/feed';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';

// The cover picker in Edit Era (task 4.14, D-36), against MSW. The Era's weeks come from GET /weekly-logs/{id}, which
// holds a sealed Loot Drop as the server sends it (no media, D-10). Entries in Recently deleted and other users'
// entries never reach the client (backend WeeklyLog tests); a sealed one is the case the picker must handle.

const NOW = new Date('2026-11-25T07:30:00Z');

const cover = photo('media-cover', 'e-cover');
const brunswickWithCover = { ...brunswick, cover };

const weekA = week(
  'wk-a',
  '2026-11-16',
  '2026-11-22',
  [],
  [
    entry('e-cover', '2026-11-17', { media: [cover, photo('media-second', 'e-cover', 1)] }),
    entry('e-voice', '2026-11-18', { media: [voiceNote('media-voice', 'e-voice')] }),
    sealedEntry('e-sealed', '2026-11-19', '2027-01-24T22:00:00Z'),
  ],
);
const weekB = week(
  'wk-b',
  '2026-11-09',
  '2026-11-15',
  [],
  [
    entry('e-page', '2026-11-10', {
      media: [{ ...photo('media-page', 'e-page'), purpose: 'PAGE_SCAN' }],
    }),
  ],
);

let put: Record<string, unknown> | null;

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
  put = null;
  server.use(
    http.get('*/api/v1/eras', () => HttpResponse.json(sampleEras)),
    http.get('*/api/v1/weekly-logs/wk-a', () => HttpResponse.json(weekA)),
    http.get('*/api/v1/weekly-logs/wk-b', () => HttpResponse.json(weekB)),
    http.put('*/api/v1/eras/era-bru', async ({ request }) => {
      put = (await request.json()) as Record<string, unknown>;
      return HttpResponse.json({ ...brunswick, ...put });
    }),
  );
});

afterEach(() => {
  vi.useRealTimers();
});

function serveEra(era: typeof brunswick) {
  server.use(
    http.get('*/api/v1/eras/era-bru', () =>
      HttpResponse.json(
        detailOf(era, [
          eraWeek('wk-a', '2026-11-16', '2026-11-22', 2),
          eraWeek('wk-b', '2026-11-09', '2026-11-15', 1),
        ]),
      ),
    ),
  );
}

async function openEdit() {
  const user = userEvent.setup();
  renderApp('/eras/era-bru');
  await user.click(await screen.findByRole('button', { name: 'Edit' }));
  const dialog = await screen.findByRole('dialog', { name: 'Edit Era' });
  const group = within(dialog).getByRole('group', { name: 'Cover' });
  await waitFor(() => expect(within(group).queryByRole('status')).not.toBeInTheDocument());
  return { user, dialog, group };
}

describe('the cover picker', () => {
  test('offers the photos and diary pages of the Era’s open entries, never a voice note or a sealed entry', async () => {
    serveEra(brunswickWithCover);
    const { group } = await openEdit();

    const names = within(group)
      .getAllByRole('radio')
      .map((radio) => radio.getAttribute('aria-label'));
    expect(names).toEqual([
      'No cover',
      'Photo from Tuesday 17 November 2026',
      'Photo from Tuesday 17 November 2026',
      'Photo from Tuesday 10 November 2026',
    ]);
    expect(group.innerHTML).not.toContain('media-voice');
    expect(group.innerHTML).not.toContain('e-sealed');
    // The cover shown now starts selected.
    expect(within(group).getAllByRole('radio', { checked: true })).toHaveLength(1);
    expect(group.querySelector('input:checked + span img')).toHaveAttribute('src', cover.fileUrl);
  });

  test('choosing another photo sends its id, and keeps the soundtrack', async () => {
    serveEra(brunswickWithCover);
    const { user, dialog, group } = await openEdit();

    await user.click(within(group).getAllByRole('radio')[3]!);
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(put).not.toBeNull());
    expect(put).toMatchObject({
      coverMediaId: 'media-page',
      soundtrack: 'spotify:track:4uLU6hMCjMI75M1A2tKUQC',
    });
  });

  test('No cover leaves the field out, which removes a visible cover', async () => {
    serveEra(brunswickWithCover);
    const { user, dialog, group } = await openEdit();

    await user.click(within(group).getByRole('radio', { name: 'No cover' }));
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(put).not.toBeNull());
    expect(put).not.toHaveProperty('coverMediaId');
  });

  test('an Era whose cover is hidden starts on No cover and a save leaves the field out, so the server keeps it', async () => {
    // cover: null is all the client is told while the cover's entry is sealed or in Recently deleted (D-36).
    serveEra({ ...brunswick, cover: null });
    const { user, dialog, group } = await openEdit();

    expect(within(group).getByRole('radio', { name: 'No cover' })).toBeChecked();
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(put).not.toBeNull());
    expect(put).not.toHaveProperty('coverMediaId');
  });

  test('arrow keys move through the tiles, as in any radio group', async () => {
    serveEra(brunswickWithCover);
    const { user, group } = await openEdit();

    within(group).getByRole('radio', { name: 'No cover' }).focus();
    await user.keyboard('{ArrowRight}');

    expect(within(group).getAllByRole('radio')[1]).toHaveFocus();
    expect(within(group).getAllByRole('radio')[1]).toBeChecked();
  });
});

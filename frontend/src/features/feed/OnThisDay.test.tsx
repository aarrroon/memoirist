import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import type { EntrySummary } from '../../api/lootDrops';
import { eraOf } from '../../test/msw/eras';
import { photo, withCopies } from '../../test/msw/feed';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';

// Task 4.14 against MSW: the On this day strip, and Era covers on the feed's Era lines (D-32, D-36, D-41). The feed
// is the shared sample (msw/feed.ts), which holds a sealed Loot Drop as the server sends it. The server leaves
// sealed entries, entries in Recently deleted, other users' entries and guessed dates out of On this day, and hides a
// cover whose entry is sealed or deleted (backend OnThisDayEndpointTest, EraCoverTest), so these tests check that the
// client draws exactly what it is sent, and nothing more.

// 23:47 on Tuesday 24 November 2026 in Melbourne. Only Date is faked, so MSW's and React's timers still run.
const NOW = new Date('2026-11-24T12:47:00Z');

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
});

afterEach(() => {
  vi.useRealTimers();
});

function summary(
  id: string,
  entryDate: string,
  overrides: Partial<EntrySummary> = {},
): EntrySummary {
  return {
    id,
    weeklyLogId: `week-${id}`,
    entryDate,
    title: null,
    previewLine: null,
    photoCount: 0,
    voiceNoteCount: 0,
    firstMediaKind: null,
    tags: [],
    createdAt: '2025-11-24T00:00:00Z',
    unlockDate: null,
    isLocked: false,
    dateUnknown: false,
    deletedAt: null,
    ...overrides,
  };
}

const memories: EntrySummary[] = [
  summary('otd-1', '2025-11-24', {
    title: 'First night in the big room',
    photoCount: 1,
    firstMediaKind: 'PHOTO',
  }),
  summary('otd-2', '2022-11-24', { previewLine: 'Exams done. Pancakes.' }),
];

const serveMemories = (rows: EntrySummary[]) =>
  server.use(http.get('*/api/v1/entries/on-this-day', () => HttpResponse.json(rows)));

describe('On this day', () => {
  test('one row names the newest memory and counts the rest, and opens the list in place', async () => {
    const user = userEvent.setup();
    serveMemories(memories);
    renderApp('/');

    const strip = await screen.findByRole('region', { name: 'On this day' });
    const row = within(strip).getByRole('button', { name: /On this day/ });
    expect(row).toHaveTextContent('First night in the big room, and 1 more');
    expect(row).toHaveAttribute('aria-expanded', 'false');
    expect(within(strip).queryByRole('list')).not.toBeInTheDocument();

    await user.click(row);

    expect(row).toHaveAttribute('aria-expanded', 'true');
    const links = within(within(strip).getByRole('list')).getAllByRole('link');
    expect(links.map((link) => link.textContent)).toEqual([
      'First night in the big room1 year ago',
      'Exams done. Pancakes.4 years ago',
    ]);
    expect(links[0]).toHaveAttribute('href', '/entries/otd-1');

    // Enter on the row closes it again.
    row.focus();
    await user.keyboard('{Enter}');
    expect(row).toHaveAttribute('aria-expanded', 'false');
  });

  test('sits at the top of the feed, above the first week', async () => {
    serveMemories(memories);
    renderApp('/');

    const strip = await screen.findByRole('region', { name: 'On this day' });
    const firstEntry = screen.getByRole('heading', { name: 'Quiet day' });
    expect(
      strip.compareDocumentPosition(firstEntry) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  test('with no memories there is no row at all, even with a sealed Loot Drop in the feed', async () => {
    serveMemories([]);
    renderApp('/');

    await screen.findByRole('heading', { name: 'Quiet day' });
    expect(screen.queryByRole('region', { name: 'On this day' })).not.toBeInTheDocument();
    expect(screen.queryByText('On this day')).not.toBeInTheDocument();
  });

  test('a failed request counts as nothing to show and the feed still draws', async () => {
    server.use(
      http.get('*/api/v1/entries/on-this-day', () => HttpResponse.json({}, { status: 404 })),
    );
    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Quiet day' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'On this day' })).not.toBeInTheDocument();
  });

  test('moves below the first entry while an Opened row has its place (D-41)', async () => {
    serveMemories(memories);
    server.use(
      http.get('*/api/v1/entries/opened', () =>
        HttpResponse.json([
          summary('drop', '2025-10-01', {
            title: 'Letter to future me',
            unlockDate: '2026-11-23T22:00:00Z',
          }),
        ]),
      ),
    );
    renderApp('/');

    const opened = await screen.findByRole('region', { name: 'Opened Loot Drops' });
    const strip = screen.getByRole('region', { name: 'On this day' });
    const firstEntry = screen.getByRole('heading', { name: 'Quiet day' });
    const secondWeek = screen.getByRole('heading', { name: '16–22 November · 2 entries' });
    const follows = (a: Node, b: Node) =>
      Boolean(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);
    expect(follows(opened, firstEntry)).toBe(true);
    expect(follows(firstEntry, strip)).toBe(true);
    expect(follows(strip, secondWeek)).toBe(true);
    expect(screen.getAllByRole('region', { name: 'On this day' })).toHaveLength(1);
  });
});

describe('Era covers on the feed', () => {
  test('a cover shows on its Era line, which opens the Era; an Era whose cover is hidden shows its initial', async () => {
    const cover = photo('media-cover', 'entry-cover');
    server.use(
      http.get('*/api/v1/eras', () =>
        HttpResponse.json([
          eraOf('era-job', 'New job', '2026-11-09', null, { cover }),
          // The server sends cover: null while the cover's entry is sealed or in Recently deleted (D-36).
          eraOf('era-uni', 'Uni Days', '2025-10-17', '2026-11-18', { cover: null }),
        ]),
      ),
    );
    renderApp('/');
    await screen.findByRole('heading', { name: 'Quiet day' });

    const job = screen.getAllByRole('link', { name: 'New job' })[0]!;
    expect(job).toHaveAttribute('href', '/eras/era-job');
    const img = await within(job).findByRole('presentation');
    expect(img).toHaveAttribute('src', cover.fileUrl);
    expect(img).toHaveAttribute('loading', 'lazy');

    const uni = screen.getAllByRole('link', { name: 'Uni Days' })[0]!;
    expect(uni.querySelector('img')).toBeNull();
    expect(uni).toHaveTextContent('U');
    // No file URL for a hidden cover reaches the page at all.
    expect(document.querySelectorAll('img[src*="media-cover"]')).toHaveLength(
      screen.getAllByRole('link', { name: 'New job' }).length,
    );
  });

  test('the Era line draws the cover from its thumbnail, not the original (D-43)', async () => {
    const cover = withCopies(photo('media-cover', 'entry-cover'));
    server.use(
      http.get('*/api/v1/eras', () =>
        HttpResponse.json([eraOf('era-job', 'New job', '2026-11-09', null, { cover })]),
      ),
    );
    renderApp('/');
    await screen.findByRole('heading', { name: 'Quiet day' });

    const job = screen.getAllByRole('link', { name: 'New job' })[0]!;
    const img = await within(job).findByRole('presentation');
    expect(img).toHaveAttribute('src', cover.thumbnailUrl);
    // Neither the original nor the screen copy is asked for anywhere on the feed for this 32 px tile.
    expect(document.querySelectorAll('img[src*="media-cover.png"]')).toHaveLength(0);
    expect(document.querySelectorAll('img[src*="media-cover-screen"]')).toHaveLength(0);
  });

  test('until the Eras arrive the line shows the initial in the same tile', async () => {
    server.use(http.get('*/api/v1/eras', () => new Promise(() => {})));
    renderApp('/');
    await screen.findByRole('heading', { name: 'Quiet day' });

    const job = screen.getAllByRole('link', { name: 'New job' })[0]!;
    expect(job.querySelector('img')).toBeNull();
    expect(job).toHaveTextContent('N');
  });
});

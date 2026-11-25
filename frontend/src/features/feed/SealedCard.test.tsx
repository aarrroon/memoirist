import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, test } from 'vitest';
import type { Entry } from '../../api/feed';
import { frontAt, travelledPath } from '../../lib/starPath';
import { doc, entry, photo, sealedEntry } from '../../test/msw/feed';
import { FeedEntry } from './FeedEntry';
import { SealedCard } from './SealedCard';

// The sealed card (task 4.8, D-10, DESIGN.md §4 Loot Drops). The server redacts a sealed entry (EntryMapper, its own
// tests); these prove the card shows only its dates and countdown, and that even an entry that arrived with its
// private fields filled in (a server mistake) shows none of them.

const MELBOURNE = 'Australia/Melbourne';
// 23:47 on Tuesday 24 November 2026 in Melbourne; 07:47 the same Tuesday in New York.
const NOW = new Date('2026-11-24T12:47:00Z');

function inRouter(ui: React.ReactElement) {
  const router = createMemoryRouter([{ path: '*', element: ui }]);
  return render(<RouterProvider router={router} />);
}

/** A sealed entry whose private fields were left in, as if the server had forgotten to redact it. */
const leaky: Entry = {
  ...sealedEntry('entry-leaky', '2026-11-19', '2027-01-24T22:00:00Z'),
  title: 'The secret title',
  contentJson: doc('The secret caption'),
  contentText: 'The secret caption',
  tags: [{ id: 'tag-secret', name: 'SecretTag', source: 'USER' }],
  media: [photo('media-secret', 'entry-leaky')],
  soundtrack: 'spotify:track:secret',
};

describe('the sealed card', () => {
  test('under a day line it shows only the opening date and the countdown', () => {
    inRouter(<SealedCard entry={leaky} zone={MELBOURNE} now={NOW} />);
    const link = screen.getByRole('link');
    expect(link).toHaveAccessibleName('Sealed Loot Drop. Opens Mon 25 Jan 2027, in 62 days.');
    expect(link).toHaveAttribute('href', '/entries/entry-leaky');
    expect(link).toHaveTextContent(/^Opens Mon 25 Jan 2027in 62 days$/);
    expect(screen.queryByText(/Thursday 19 November/)).not.toBeInTheDocument();
  });

  test('anywhere else it shows the memory date first', () => {
    inRouter(<SealedCard entry={leaky} zone={MELBOURNE} now={NOW} showMemoryDate />);
    const link = screen.getByRole('link');
    expect(link).toHaveAccessibleName(
      'Sealed Loot Drop, Thursday 19 November. Opens Mon 25 Jan 2027, in 62 days.',
    );
    expect(link).toHaveTextContent(/^Thursday 19 NovemberOpens Mon 25 Jan 2027in 62 days$/);
  });

  test('a feed entry that is sealed shows no title, caption, tag, photo or song, even if they were sent', () => {
    const { container } = inRouter(
      <FeedEntry entry={leaky} showDay eagerPhotoId="media-secret" zone={MELBOURNE} now={NOW} />,
    );
    expect(container).not.toHaveTextContent(/secret/i);
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('audio')).toBeNull();
    expect(container.innerHTML).not.toContain('media-secret');
    // What it does show: the day line, the opening date and the countdown.
    expect(container).toHaveTextContent('Thursday 19 November');
    expect(screen.getByText('in 62 days')).toBeInTheDocument();
  });

  test('the countdown counts days in the user’s zone, not the browser’s', () => {
    // 9:00 am on Thursday 26 November in Melbourne is 5:00 pm on Wednesday the 25th in New York.
    const opens = sealedEntry('entry-soon', '2026-11-20', '2026-11-25T22:00:00Z');
    const { unmount } = inRouter(<SealedCard entry={opens} zone={MELBOURNE} now={NOW} />);
    expect(screen.getByRole('link')).toHaveAccessibleName(
      'Sealed Loot Drop. Opens Thu 26 Nov 2026, in 2 days.',
    );
    unmount();
    inRouter(<SealedCard entry={opens} zone="America/New_York" now={NOW} />);
    expect(screen.getByRole('link')).toHaveAccessibleName(
      'Sealed Loot Drop. Opens Wed 25 Nov 2026, tomorrow.',
    );
  });

  test('the star’s lit part and light front match the days travelled', () => {
    // Written 25 October 2026, opens 9:00 am on 25 October 2027: on 28 October it has come 3 days of 365.
    const opens = sealedEntry('entry-year', '2026-10-25', '2027-10-24T22:00:00Z');
    const { container } = inRouter(
      <SealedCard entry={opens} zone={MELBOURNE} now={new Date('2026-10-28T01:00:00Z')} />,
    );
    const fraction = 3 / 365;
    expect(container.querySelector('svg path')?.getAttribute('d')).toBe(travelledPath(fraction));
    const front = container.querySelector('use[href$="#front"]');
    expect(front?.getAttribute('x')).toBe(String(frontAt(fraction).x));
    expect(front?.getAttribute('y')).toBe(String(frontAt(fraction).y));
    // It is drawn from the one SVG file (design.md §5), part by part.
    const parts = [...container.querySelectorAll('use')].map((u) => u.getAttribute('href'));
    expect(parts.every((href) => href?.includes('travelling-star.svg'))).toBe(true);
  });

  test('a card that is not sealed is the entry, not a card', () => {
    const { container } = inRouter(
      <FeedEntry
        entry={entry('entry-open', '2026-11-19', { title: 'Open now' })}
        showDay
        eagerPhotoId={null}
        zone={MELBOURNE}
        now={NOW}
      />,
    );
    expect(container).toHaveTextContent('Open now');
    expect(container.querySelector('svg')).toBeNull();
  });
});

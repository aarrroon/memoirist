import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, test } from 'vitest';
import type { Entry } from '../../api/feed';
import { doc, entry, photo, sealedEntry, voiceNote } from '../../test/msw/feed';
import { FeedEntry } from './FeedEntry';

// One entry in the feed (task 4.5): the swipe, the caption drawn from its TipTap JSON and the voice note row,
// for each shape of entry the Done-when names. The whole feed, with a sealed Loot Drop among its entries, is
// FeedPage.test.tsx.

const NOW = new Date('2026-11-24T12:47:00Z');
const ZONE = 'Australia/Melbourne';

function show(value: Entry, eagerPhotoId: string | null = null) {
  return render(
    <MemoryRouter>
      <FeedEntry entry={value} showDay eagerPhotoId={eagerPhotoId} zone={ZONE} now={NOW} />
    </MemoryRouter>,
  );
}

/** The reserved frame's shape: the swipe's viewport carries it as --frame (aspect-(--frame)). */
function frameOf(img: HTMLElement): number {
  const frame = img.closest<HTMLElement>('[style*="--frame"]');
  return Number(frame?.style.getPropertyValue('--frame'));
}

describe('an entry with photos only', () => {
  test('shows its one photo whole-width, names it, and has no carousel, dots or arrows', () => {
    show(entry('e', '2026-11-24', { media: [photo('p', 'e')] }), 'p');

    const img = screen.getByRole('img', { name: 'Photo' });
    expect(img).toHaveAttribute('loading', 'eager');
    expect(img).toHaveAttribute('fetchpriority', 'high');
    expect(img).toHaveAttribute('decoding', 'async');
    expect(img).toHaveAttribute('width', '960');
    expect(img).toHaveAttribute('height', '640');
    expect(screen.queryByRole('group')).not.toBeInTheDocument();
    // No arrows: the only button is the entry's more-actions menu (task 4.12).
    expect(screen.getAllByRole('button').map((b) => b.getAttribute('aria-label'))).toEqual([
      'More actions for Photo',
    ]);
    // D-24: with nothing written, the entry is named by its first media item.
    expect(screen.getByText('Photo', { selector: 'p' })).toBeInTheDocument();
  });

  test('a diary page is named as one (D-20)', () => {
    show(entry('e', '2026-11-24', { media: [{ ...photo('p', 'e'), purpose: 'PAGE_SCAN' }] }));

    expect(screen.getByRole('img', { name: 'Diary page' })).toBeInTheDocument();
  });
});

describe('the frame is reserved from the first photo’s stored size (D-17)', () => {
  test('a wide photo keeps its own shape (960 × 640 is 1.5)', () => {
    show(entry('e', '2026-11-24', { media: [photo('p', 'e')] }));

    expect(frameOf(screen.getByRole('img'))).toBe(1.5);
  });

  test('a tall photo is held at 4:5', () => {
    show(entry('e', '2026-11-24', { media: [photo('p', 'e', 0, { width: 1080, height: 1920 })] }));

    expect(frameOf(screen.getByRole('img'))).toBe(0.8);
  });

  test('a panorama is held at 1.91:1', () => {
    show(entry('e', '2026-11-24', { media: [photo('p', 'e', 0, { width: 8000, height: 1000 })] }));

    expect(frameOf(screen.getByRole('img'))).toBe(1.91);
  });

  test('a photo with no size gets 4:5, and no width or height attributes', () => {
    show(entry('e', '2026-11-24', { media: [photo('p', 'e', 0, null)] }));

    const img = screen.getByRole('img');
    expect(frameOf(img)).toBe(0.8);
    expect(img).not.toHaveAttribute('width');
    expect(img).not.toHaveAttribute('height');
  });

  test('only the first photo sets the shape; the rest are cropped to it', () => {
    show(
      entry('e', '2026-11-24', {
        media: [
          photo('a', 'e', 0, { width: 1000, height: 1000 }),
          photo('b', 'e', 1, { width: 1080, height: 1920 }),
        ],
      }),
    );

    expect(frameOf(screen.getByRole('img', { name: 'Photo 1 of 2' }))).toBe(1);
  });
});

describe('an entry with a caption only', () => {
  test('shows the caption from its JSON, with no swipe and no media label', () => {
    show(entry('e', '2026-11-24', { contentJson: doc('First line.', 'Second line.') }));

    expect(screen.getByText('First line.')).toBeInTheDocument();
    expect(screen.getByText('Second line.')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.queryByRole('group')).not.toBeInTheDocument();
    // The caption is clamped to three lines (DESIGN.md §4 Feed).
    expect(screen.getByText('First line.').parentElement).toHaveClass('line-clamp-3', 'text-body');
  });

  test('a caption of empty paragraphs is no caption', () => {
    show(
      entry('e', '2026-11-24', {
        title: 'Only a title',
        contentJson: {
          type: 'doc',
          content: [{ type: 'paragraph' }],
        } as unknown as Entry['contentJson'],
      }),
    );

    expect(
      screen.getByRole('heading', { name: 'Only a title' }).parentElement?.children,
    ).toHaveLength(1);
  });
});

describe('an entry with only a voice note', () => {
  test('says "Voice note" once: the display title (D-24), not again in the row', () => {
    show(entry('e', '2026-11-24', { media: [voiceNote('v', 'e')] }));

    expect(screen.getAllByText(/voice note/i)).toHaveLength(1);
    expect(screen.getByText('Voice note', { selector: 'p' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Play voice note' })).toBeInTheDocument();
    expect(screen.queryByRole('group')).not.toBeInTheDocument();
  });
});

describe('a mixed entry: three photos, a title, a caption and a voice note', () => {
  const mixed = () =>
    entry('e', '2026-11-24', {
      title: 'Grampians',
      contentJson: doc('Up before the sun.'),
      media: [photo('a', 'e'), photo('b', 'e', 1), voiceNote('v', 'e', 2), photo('c', 'e', 3)],
    });

  test('puts the photos in a labelled swipe with dots, and the voice note in its own row', () => {
    show(mixed(), 'a');

    const swipe = screen.getByRole('group', { name: 'Photos, 3 photos' });
    expect(swipe).toHaveAttribute('aria-roledescription', 'carousel');
    expect(swipe).toHaveAttribute('tabindex', '0');
    expect(screen.getByRole('heading', { name: 'Grampians' })).toBeInTheDocument();
    expect(screen.getByText('Up before the sun.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Play voice note' })).toBeInTheDocument();
    // Three dots, decoration only, the first one current.
    const dots = swipe.parentElement!.nextElementSibling!;
    expect(dots).toHaveAttribute('aria-hidden', 'true');
    expect(dots.children).toHaveLength(3);
    expect(dots.children[0]).toHaveAttribute('data-current', 'true');
  });

  test('loads only the first photo until the reader turns to the swipe (design.md §6)', () => {
    const { container } = show(mixed(), 'a');

    expect(container.querySelectorAll('img')).toHaveLength(1);
  });

  test('moves with the arrow keys, Home and End, loading one photo ahead', async () => {
    const user = userEvent.setup();
    const { container } = show(mixed(), 'a');
    const swipe = screen.getByRole('group', { name: 'Photos, 3 photos' });

    await user.tab();
    expect(swipe).toHaveFocus();
    // Focus is interest: the next photo starts to load, still lazy.
    expect(container.querySelectorAll('img')).toHaveLength(2);
    expect(screen.getByRole('img', { name: 'Photo 1 of 3' })).toBeInTheDocument();

    await user.keyboard('{ArrowRight}');
    const second = screen.getByRole('img', { name: 'Photo 2 of 3' });
    expect(second).toHaveAttribute('loading', 'lazy');
    expect(screen.queryByRole('img', { name: 'Photo 1 of 3' })).not.toBeInTheDocument(); // hidden off screen
    expect(container.querySelectorAll('img')).toHaveLength(3);

    await user.keyboard('{End}');
    expect(screen.getByRole('img', { name: 'Photo 3 of 3' })).toBeInTheDocument();
    await user.keyboard('{ArrowRight}'); // already at the end: stays
    expect(screen.getByRole('img', { name: 'Photo 3 of 3' })).toBeInTheDocument();

    await user.keyboard('{Home}');
    expect(screen.getByRole('img', { name: 'Photo 1 of 3' })).toBeInTheDocument();
  });

  test('the laptop arrow that reaches the end hands focus to the swipe, so focus is never lost', async () => {
    const user = userEvent.setup();
    show(mixed(), 'a');

    await user.click(screen.getByRole('button', { name: 'Next photo' }));
    expect(screen.getByRole('img', { name: 'Photo 2 of 3' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Next photo' }));

    expect(screen.getByRole('img', { name: 'Photo 3 of 3' })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'Photos, 3 photos' })).toHaveFocus();
  });

  test('the voice note plays and pauses from its button, by keyboard too', async () => {
    const user = userEvent.setup();
    show(mixed());

    const play = screen.getByRole('button', { name: 'Play voice note' });
    // The swipe, then (jsdom applies no CSS, so the laptop-only arrow shows here too) Next photo, then Play.
    const stops: string[] = [];
    for (let i = 0; i < 3; i += 1) {
      await user.tab();
      const focused = document.activeElement as HTMLElement;
      stops.push(focused.getAttribute('aria-label') ?? focused.tagName);
    }
    expect(stops).toEqual(['Photos, 3 photos', 'Next photo', 'Play voice note']);
    expect(play).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(screen.getByRole('button', { name: 'Pause voice note' })).toBeInTheDocument();
    await user.keyboard(' ');
    expect(screen.getByRole('button', { name: 'Play voice note' })).toBeInTheDocument();
  });
});

describe('a caption drawn from JSON (non-negotiable 6)', () => {
  const rich = (content: unknown[]) =>
    entry('e', '2026-11-24', {
      contentJson: { type: 'doc', content } as unknown as Entry['contentJson'],
    });

  test('draws the allowed marks and nodes as elements', () => {
    const { container } = show(
      rich([
        { type: 'heading', attrs: { level: 2 }, content: [{ type: 'text', text: 'Best bit' }] },
        {
          type: 'paragraph',
          content: [
            { type: 'text', text: 'bold', marks: [{ type: 'bold' }] },
            { type: 'text', text: ' and ' },
            { type: 'text', text: 'both', marks: [{ type: 'bold' }, { type: 'italic' }] },
            { type: 'hardBreak' },
            {
              type: 'text',
              text: 'site',
              marks: [{ type: 'link', attrs: { href: 'https://example.com' } }],
            },
          ],
        },
        {
          type: 'bulletList',
          content: [
            {
              type: 'listItem',
              content: [{ type: 'paragraph', content: [{ type: 'text', text: 'one' }] }],
            },
          ],
        },
      ]),
    );

    // A template prompt is not a heading in the feed: the outline stays weeks and entries.
    expect(screen.queryByRole('heading', { name: 'Best bit' })).not.toBeInTheDocument();
    expect(screen.getByText('Best bit')).toHaveClass('font-strong');
    expect(screen.getByText('bold').tagName).toBe('STRONG');
    expect(screen.getByText('both').closest('strong')?.querySelector('em')).toHaveTextContent(
      'both',
    );
    expect(container.querySelector('br')).not.toBeNull();
    const link = screen.getByRole('link', { name: 'site' });
    expect(link).toHaveAttribute('href', 'https://example.com');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer nofollow');
    expect(within(screen.getByRole('list')).getByText('one')).toBeInTheDocument();
  });

  test('shows a link the screen won’t open as its text, and HTML in the text as text', () => {
    const { container } = show(
      rich([
        {
          type: 'paragraph',
          content: [
            {
              type: 'text',
              text: 'click me',
              marks: [{ type: 'link', attrs: { href: 'javascript:alert(1)' } }],
            },
            { type: 'text', text: ' <img src=x onerror=alert(1)>' },
          ],
        },
      ]),
    );

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(screen.getByText(/click me/)).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
    expect(container.textContent).toContain('<img src=x onerror=alert(1)>');
  });

  test('skips what it doesn’t know, keeping the text inside it', () => {
    show(
      rich([
        {
          type: 'mystery',
          content: [{ type: 'text', text: 'still here', marks: [{ type: 'sparkle' }] }],
        },
        'not a node',
        { content: [] },
      ]),
    );

    expect(screen.getByText('still here')).toBeInTheDocument();
  });
});

describe('a sealed Loot Drop (D-10)', () => {
  test('shows no swipe, caption or voice note even if its content arrived', () => {
    const { container } = show({
      ...sealedEntry('s', '2026-11-24', '2027-01-24T22:00:00Z'),
      contentJson: doc('Secret caption'),
      media: [photo('p', 's'), voiceNote('v', 's', 1)],
    });

    expect(screen.queryByText(/Secret/)).not.toBeInTheDocument();
    expect(container.querySelector('img, audio')).toBeNull();
    expect(screen.queryByRole('group')).not.toBeInTheDocument();
  });
});

describe('the soundtrack row (task 4.15, D-35)', () => {
  test('an open entry shows its soundtrack as a placeholder row, with nothing from Spotify on the page', () => {
    show(
      entry('e', '2026-11-24', {
        title: 'Night drive',
        soundtrack: 'spotify:track:4uLU6hMCjMI75M1A2tKUQC',
      }),
    );
    expect(screen.getByRole('button', { name: 'Play the song on Spotify' })).toBeVisible();
    expect(document.querySelector('iframe')).toBeNull();
  });

  test('a sealed Loot Drop shows no soundtrack row, even if one reached the client (D-10)', () => {
    show({
      ...sealedEntry('s', '2026-11-24', '2027-11-24T22:00:00Z'),
      soundtrack: 'spotify:track:4uLU6hMCjMI75M1A2tKUQC',
    });
    expect(screen.queryByRole('button', { name: /on Spotify/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/on Spotify/)).not.toBeInTheDocument();
  });
});

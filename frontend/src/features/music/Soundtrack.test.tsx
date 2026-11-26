import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { useState } from 'react';
import { describe, expect, test, vi } from 'vitest';
import type { MusicResult } from '../../api/music';
import { renderWithQuery } from '../../test/render';
import { server } from '../../test/msw/server';
import { SoundtrackField } from './SoundtrackField';
import { SoundtrackPlayer } from './SoundtrackPlayer';

// Task 4.15 (D-35): the soundtrack field and the soundtrack row. The four Done-when cases are pasting a link, picking
// a search result, search being unavailable (paste only), and nothing from Spotify before Play.

const ID = '4uLU6hMCjMI75M1A2tKUQC';
const HOLOCENE: MusicResult = {
  uri: 'spotify:track:0000000000000000000001',
  kind: 'TRACK',
  title: 'Holocene',
  subtitle: 'Bon Iver',
  imageUrl: 'https://i.scdn.co/image/holocene',
};
const ALBUM: MusicResult = {
  uri: 'spotify:album:0000000000000000000002',
  kind: 'ALBUM',
  title: 'For Emma, Forever Ago',
  subtitle: 'Bon Iver',
  imageUrl: null,
};

/** The parent owns the value, as capture, the week form and the Era form do; the last value is shown for asserting. */
function Harness({ initial = null }: { initial?: string | null }) {
  const [value, setValue] = useState<string | null>(initial);
  return (
    <>
      <SoundtrackField value={value} onChange={setValue} />
      <output data-testid="value">{value ?? 'none'}</output>
    </>
  );
}

/** Every POST /music/search body, so a test can say what was (and wasn't) asked. */
function serveSearch(answer: () => Response) {
  const bodies: unknown[] = [];
  server.use(
    http.post('*/api/v1/music/search', async ({ request }) => {
      bodies.push(await request.json());
      return answer();
    }),
  );
  return bodies;
}

const unavailable = () =>
  HttpResponse.json(
    {
      type: 'urn:memoirist:problem:music-search-unavailable',
      title: 'Service Unavailable',
      status: 503,
      detail: 'Music search isn’t set up on this server.',
    },
    { status: 503, headers: { 'Content-Type': 'application/problem+json' } },
  );

/** Nothing on the page points at Spotify: no frame, image, script or link to any of its hosts. */
function spotifyElements() {
  return [...document.querySelectorAll('iframe, img, script, link, source, embed, object')].filter(
    (element) =>
      /spotify|scdn/i.test(
        element.getAttribute('src') ??
          element.getAttribute('href') ??
          element.getAttribute('data') ??
          '',
      ),
  );
}

describe('SoundtrackField', () => {
  test('pasting a share link sets its URI at once, with nothing sent to the server', async () => {
    const user = userEvent.setup();
    const bodies = serveSearch(() => HttpResponse.json({ items: [] }));
    renderWithQuery(<Harness />);

    await user.click(screen.getByLabelText('Soundtrack'));
    await user.paste(`https://open.spotify.com/track/${ID}?si=abc123`);

    expect(screen.getByTestId('value')).toHaveTextContent(`spotify:track:${ID}`);
    expect(screen.getByText('on Spotify', { exact: false })).toHaveTextContent('Song on Spotify');
    // Focus moves to the field's one control, so the keyboard isn't lost when the text field goes.
    await vi.waitFor(() =>
      expect(screen.getByRole('button', { name: /Remove the soundtrack/ })).toHaveFocus(),
    );
    expect(bodies).toEqual([]);
  });

  test('a link to an artist or a short link is refused with a reason, and nothing is set', async () => {
    const user = userEvent.setup();
    const bodies = serveSearch(() => HttpResponse.json({ items: [] }));
    renderWithQuery(<Harness />);
    const field = screen.getByLabelText('Soundtrack');

    await user.type(field, `https://open.spotify.com/artist/${ID}{Enter}`);
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A soundtrack is a song, an album or a playlist.',
    );
    await user.clear(field);
    await user.type(field, 'https://spotify.link/abc{Enter}');
    expect(await screen.findByRole('alert')).toHaveTextContent('Short spotify.link addresses');
    expect(screen.getByTestId('value')).toHaveTextContent('none');
    expect(bodies).toEqual([]);
  });

  test('searching sends the words in the body, and picking a result sets its URI and names it', async () => {
    const user = userEvent.setup();
    const bodies = serveSearch(() => HttpResponse.json({ items: [HOLOCENE, ALBUM] }));
    renderWithQuery(<Harness />);

    await user.type(screen.getByLabelText('Soundtrack'), 'holocene{Enter}');
    const results = await screen.findByRole('list', { name: 'Search results' });
    expect(bodies).toEqual([{ query: 'holocene' }]);
    expect(
      within(results)
        .getAllByRole('button')
        .map((b) => b.textContent),
    ).toEqual(['HoloceneSong · Bon Iver', 'For Emma, Forever AgoAlbum · Bon Iver']);
    // Cover art lives on Spotify's servers; the results never load it (D-35, PRD Privacy).
    expect(spotifyElements()).toEqual([]);

    await user.click(within(results).getByRole('button', { name: /Holocene/ }));
    expect(screen.getByTestId('value')).toHaveTextContent(HOLOCENE.uri);
    expect(screen.getByText('Holocene').closest('p')).toHaveTextContent('Holocene, Bon Iver');

    // Remove clears it and gives focus back to the text field.
    await user.click(screen.getByRole('button', { name: 'Remove the soundtrack, Holocene' }));
    expect(screen.getByTestId('value')).toHaveTextContent('none');
    await vi.waitFor(() => expect(screen.getByLabelText('Soundtrack')).toHaveFocus());
  });

  test('when search is unavailable (503) the field is paste-only, asks once and never retries', async () => {
    const user = userEvent.setup();
    const bodies = serveSearch(unavailable);
    renderWithQuery(<Harness />);

    await user.type(screen.getByLabelText('Soundtrack'), 'holocene{Enter}');
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Song search isn’t available right now. Paste a Spotify link instead.',
    );
    expect(bodies).toHaveLength(1);
    expect(screen.queryByRole('button', { name: 'Search' })).not.toBeInTheDocument();
    const field = screen.getByLabelText('Soundtrack: Spotify link');

    // More words are not sent again: they are read as a link, and refused as one.
    await user.clear(field);
    await user.type(field, 'bon iver{Enter}');
    expect(await screen.findByText(/Paste a link from Spotify’s Share menu/)).toBeInTheDocument();
    expect(bodies).toHaveLength(1);

    // A link still works.
    await user.clear(field);
    await user.paste(`https://open.spotify.com/playlist/${ID}`);
    expect(screen.getByTestId('value')).toHaveTextContent(`spotify:playlist:${ID}`);
  });

  test('no results says so, and a network failure keeps search on', async () => {
    const user = userEvent.setup();
    serveSearch(() => HttpResponse.json({ items: [] }));
    renderWithQuery(<Harness />);
    await user.type(screen.getByLabelText('Soundtrack'), 'zzzz{Enter}');
    expect(
      await screen.findByText('Nothing found. Try other words, or paste a link.'),
    ).toBeVisible();

    serveSearch(() => HttpResponse.error());
    await user.clear(screen.getByLabelText('Soundtrack'));
    await user.type(screen.getByLabelText('Soundtrack'), 'other{Enter}');
    expect(await screen.findByRole('alert')).toHaveTextContent('Can’t reach memoirist.');
    expect(screen.getByRole('button', { name: 'Search' })).toBeInTheDocument();
  });

  test('a stored soundtrack shows as its kind, since only the URI is stored', () => {
    renderWithQuery(<Harness initial={`spotify:album:${ID}`} />);
    expect(
      screen.getByRole('button', { name: 'Remove the soundtrack, album on Spotify' }),
    ).toBeVisible();
    // The mark is decorative (aria-hidden); the row's words name Spotify once.
    expect(screen.getByText('on Spotify', { exact: false })).toHaveTextContent('Album on Spotify');
    expect(screen.queryByRole('img', { name: 'Spotify' })).not.toBeInTheDocument();
  });
});

describe('SoundtrackPlayer', () => {
  test('nothing from Spotify is on the page until Play is tapped; then the player loads and takes focus', async () => {
    const user = userEvent.setup();
    renderWithQuery(<SoundtrackPlayer uri={`spotify:playlist:${ID}`} />);

    expect(screen.getByText('on Spotify', { exact: false })).toHaveTextContent(
      'Playlist on Spotify',
    );
    expect(spotifyElements()).toEqual([]);
    expect(document.querySelector('iframe')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Play the playlist on Spotify' }));
    const player = screen.getByTitle('Spotify player for the playlist');
    expect(player).toHaveAttribute('src', `https://open.spotify.com/embed/playlist/${ID}`);
    expect(player).toHaveFocus();
    expect(screen.queryByRole('button', { name: /Play/ })).not.toBeInTheDocument();
  });

  test('a value the server would never store draws nothing', () => {
    const { container } = renderWithQuery(
      <SoundtrackPlayer uri="https://open.spotify.com/track/x" />,
    );
    expect(container).toBeEmptyDOMElement();
  });
});

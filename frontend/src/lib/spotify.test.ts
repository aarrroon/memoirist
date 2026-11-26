import { describe, expect, test } from 'vitest';
import { embedUrl, kindWord, spotifyUri, uriKind } from './spotify';

const ID = '4uLU6hMCjMI75M1A2tKUQC';

describe('spotifyUri (D-35)', () => {
  test.each([
    [`spotify:track:${ID}`, `spotify:track:${ID}`],
    [`  spotify:album:${ID}\n`, `spotify:album:${ID}`],
    [`https://open.spotify.com/track/${ID}`, `spotify:track:${ID}`],
    [`https://open.spotify.com/track/${ID}?si=abc123def456`, `spotify:track:${ID}`],
    [`open.spotify.com/album/${ID}`, `spotify:album:${ID}`],
    [`http://open.spotify.com/playlist/${ID}`, `spotify:playlist:${ID}`],
    [`https://open.spotify.com/intl-de/track/${ID}`, `spotify:track:${ID}`],
    [
      `https://open.spotify.com/embed/playlist/${ID}?utm_source=generator`,
      `spotify:playlist:${ID}`,
    ],
    [`https://open.spotify.com/user/someone/playlist/${ID}`, `spotify:playlist:${ID}`],
    [`https://play.spotify.com/track/${ID}`, `spotify:track:${ID}`],
    [`https://OPEN.SPOTIFY.COM/track/${ID}`, `spotify:track:${ID}`],
  ])('reads %s', (text, uri) => {
    expect(spotifyUri(text)).toMatchObject({ ok: true, uri });
  });

  test.each([
    ['Holocene Bon Iver', 'not-a-link'],
    ['', 'not-a-link'],
    [`https://evil.example/?open.spotify.com/track/${ID}`, 'not-a-link'],
    [`https://open.spotify.com.evil.example/track/${ID}`, 'not-a-link'],
    [`javascript:alert(1)//open.spotify.com/track/${ID}`, 'not-a-link'],
    [`ftp://open.spotify.com/track/${ID}`, 'not-a-link'],
    ['https://spotify.link/abc123', 'short-link'],
    [`https://open.spotify.com/artist/${ID}`, 'wrong-kind'],
    [`https://open.spotify.com/episode/${ID}`, 'wrong-kind'],
    [`spotify:artist:${ID}`, 'wrong-kind'],
    [`https://open.spotify.com/track/${ID.slice(1)}`, 'wrong-kind'],
    [`https://open.spotify.com/track/${ID}x`, 'wrong-kind'],
    [`https://open.spotify.com/track/${ID}/extra`, 'wrong-kind'],
    ['https://open.spotify.com/', 'wrong-kind'],
  ])('refuses %s as %s', (text, reason) => {
    expect(spotifyUri(text)).toEqual({ ok: false, reason });
  });
});

describe('the player and the words', () => {
  test('embedUrl builds the player address only from a stored URI', () => {
    expect(embedUrl(`spotify:album:${ID}`)).toBe(`https://open.spotify.com/embed/album/${ID}`);
    expect(embedUrl('https://open.spotify.com/track/x')).toBeNull();
    expect(embedUrl(`spotify:track:${ID}?x`)).toBeNull();
  });

  test('uriKind and kindWord', () => {
    expect(uriKind(`spotify:playlist:${ID}`)).toBe('playlist');
    expect(uriKind('nonsense')).toBeNull();
    expect(kindWord('track')).toBe('Song');
    expect(kindWord('ALBUM')).toBe('Album');
    expect(kindWord('PLAYLIST')).toBe('Playlist');
  });
});

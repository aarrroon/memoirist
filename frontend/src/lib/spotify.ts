// Soundtracks (D-35, task 4.15). The server stores a Spotify URI and nothing else: `spotify:track:`, `spotify:album:`
// or `spotify:playlist:` and a 22-character id (API_CONTRACT §Music). People don't have URIs; they have the link from
// Spotify's Share menu. This file turns one into the other, and back into the address of Spotify's embedded player.
// Pure functions only: nothing here contacts Spotify (the player loads on a tap, the search goes through our server).

export type SoundtrackKind = 'track' | 'album' | 'playlist';

/** What the server accepts, exactly (backend SpotifyUri): the whole text, no spaces around it. */
const URI = /^spotify:(track|album|playlist):([A-Za-z0-9]{22})$/;
const ID = /^[A-Za-z0-9]{22}$/;
const KINDS: readonly string[] = ['track', 'album', 'playlist'];

export type SpotifyLink =
  | { ok: true; uri: string; kind: SoundtrackKind }
  /** `not-a-link`: not Spotify at all (words to search for, say). `short-link`: spotify.link, which only Spotify can
   *  expand. `wrong-kind`: an artist, a podcast, a profile: Spotify, but nothing the server keeps. */
  | { ok: false; reason: 'not-a-link' | 'short-link' | 'wrong-kind' };

// TODO(human) PRACTICE: Write spotifyUri(text): turn what the user pasted into the URI the server stores, or say why
// it can't be one. Accept a URI as it is, and an open.spotify.com (or play.spotify.com) link with or without
// "https://", with a "?si=" share code, a language segment ("/intl-de/"), "/embed/", or the old "/user/<name>/"
// prefix. Refuse every other host, a short spotify.link, and any kind but track, album and playlist.
// Hint: this is untrusted input, like an account number pasted into a payment form: normalise first (trim, add the
// scheme), parse with the platform's parser (new URL, in a try), then allow-list the host, the path's shape, the kind
// and the id. Never search the text for "spotify.com" with includes(): "evil.com/?open.spotify.com/track/…" passes.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function spotifyUri(text: string): SpotifyLink {
  const trimmed = text.trim();
  const direct = URI.exec(trimmed);
  if (direct) return { ok: true, uri: trimmed, kind: direct[1] as SoundtrackKind };
  if (/^spotify:/i.test(trimmed)) return { ok: false, reason: 'wrong-kind' };

  const withScheme = /^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
  let url: URL;
  try {
    url = new URL(withScheme);
  } catch {
    return { ok: false, reason: 'not-a-link' };
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:')
    return { ok: false, reason: 'not-a-link' };
  const host = url.hostname.toLowerCase();
  if (host === 'spotify.link' || host === 'spotify.app.link')
    return { ok: false, reason: 'short-link' };
  if (host !== 'open.spotify.com' && host !== 'play.spotify.com')
    return { ok: false, reason: 'not-a-link' };

  const parts = url.pathname.split('/').filter((part) => part !== '');
  if (parts[0]?.startsWith('intl-')) parts.shift();
  if (parts[0] === 'embed') parts.shift();
  if (parts[0] === 'user' && parts.length === 4) parts.splice(0, 2);
  const [kind, id, ...rest] = parts;
  if (kind === undefined || id === undefined || rest.length > 0 || !KINDS.includes(kind)) {
    return { ok: false, reason: 'wrong-kind' };
  }
  if (!ID.test(id)) return { ok: false, reason: 'wrong-kind' };
  return { ok: true, uri: `spotify:${kind}:${id}`, kind: kind as SoundtrackKind };
}
// └── END ANSWER ▲ ──

/** The kind of a stored URI, or null for anything the server wouldn't have stored. */
export function uriKind(uri: string): SoundtrackKind | null {
  const match = URI.exec(uri);
  return match ? (match[1] as SoundtrackKind) : null;
}

/** Spotify's embedded player for a stored URI (D-35). Only ever used as an iframe's src after the user taps Play. */
export function embedUrl(uri: string): string | null {
  const match = URI.exec(uri);
  return match ? `https://open.spotify.com/embed/${match[1]}/${match[2]}` : null;
}

/** The word for a kind, as the Write add bar says it: a track is a "Song". */
export function kindWord(kind: SoundtrackKind | 'TRACK' | 'ALBUM' | 'PLAYLIST'): string {
  const lower = kind.toLowerCase();
  return lower === 'track' ? 'Song' : lower === 'album' ? 'Album' : 'Playlist';
}

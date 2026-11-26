import { Disc3, ListMusic, Music } from 'lucide-react';
import { useId, useRef, useState, type RefObject } from 'react';
import { useMusicSearch, type MusicResult } from '../../api/music';
import { ApiProblem, problemMessage } from '../../api/problem';
import { SpotifyMark } from '../../components/SpotifyMark';
import { Button } from '../../components/ui/button';
import { kindWord, spotifyUri, uriKind } from '../../lib/spotify';

// The soundtrack field of an entry, a week and an Era (D-35, task 4.15, F-02 step 2). One field does both jobs: a
// pasted Spotify link is turned into its URI at once, and anything else is searched for through our server when the
// user presses Enter or Search (POST /music/search). Search runs on request, not on every key, because Spotify gives
// a new app a small quota. When the server says search is unavailable (503, no credentials or Spotify said no), the
// field becomes a paste-only "Spotify link" field until the next time it opens.
//
// Results show no cover art: Spotify's images live on Spotify's servers, and the browser contacts Spotify only when
// the user taps Play (PRD Privacy, design.md §6). Each result shows an icon for its kind instead.
//
// The parent owns the value (the URI, or null) and sends it on every PUT (D-26: a PUT without it clears it).

const SEARCH_HINT = 'Paste a Spotify link, or search for a song, album or playlist.';
const PASTE_HINT = 'Copy it from Spotify’s Share menu.';
const MESSAGES = {
  'short-link':
    'Short spotify.link addresses can’t be read here. Open the link, then copy the address it goes to.',
  'wrong-kind': 'A soundtrack is a song, an album or a playlist. That link is to something else.',
  'not-a-link': 'Paste a link from Spotify’s Share menu, such as open.spotify.com/track/…',
} as const;

const ICONS = { TRACK: Music, ALBUM: Disc3, PLAYLIST: ListMusic } as const;

const unavailableProblem = (error: unknown) =>
  error instanceof ApiProblem && error.kind === 'music-search-unavailable';

export function SoundtrackField({
  value,
  onChange,
  focusRef,
}: {
  value: string | null;
  onChange: (uri: string | null) => void;
  /** Points at whatever the field shows first (the text field, or Remove), for capture's Song button. */
  focusRef?: RefObject<HTMLElement | null>;
}) {
  const id = useId();
  const [text, setText] = useState('');
  // The words last searched for: null until the user asks, so nothing is sent before then.
  const [words, setWords] = useState<string | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  // What a search said about the song just picked, so the field can name it. Only the URI is stored (D-35).
  const [picked, setPicked] = useState<Pick<MusicResult, 'uri' | 'title' | 'subtitle'> | null>(
    null,
  );
  const [unavailable, setUnavailable] = useState(false);
  const search = useMusicSearch(words);
  const input = useRef<HTMLInputElement | null>(null);
  const remove = useRef<HTMLButtonElement | null>(null);

  // Once the server says search can't be done, the field stays paste-only (adjusting state during render, React's
  // pattern for state derived from a change: https://react.dev/learn/you-might-not-need-an-effect).
  if (unavailableProblem(search.error) && !unavailable) setUnavailable(true);

  const choose = (uri: string, about: Pick<MusicResult, 'title' | 'subtitle'> | null) => {
    onChange(uri);
    setPicked(about ? { uri, ...about } : null);
    setText('');
    setWords(null);
    setProblem(null);
    requestAnimationFrame(() => remove.current?.focus());
  };

  const submit = () => {
    const wanted = text.trim();
    if (wanted === '') return;
    const link = spotifyUri(wanted);
    if (link.ok) return choose(link.uri, null);
    if (link.reason !== 'not-a-link' || unavailable) {
      setProblem(MESSAGES[link.reason]);
      return;
    }
    setProblem(null);
    setWords(wanted);
  };

  const pointAt = (element: HTMLElement | null) => {
    if (focusRef) focusRef.current = element;
  };

  if (value !== null) {
    const kind = uriKind(value);
    const about = picked?.uri === value ? picked : null;
    const word = kind ? kindWord(kind) : 'Soundtrack';
    return (
      <div className="flex flex-col gap-2">
        <p className="text-small font-strong">Soundtrack</p>
        <div className="flex min-h-11 items-center gap-3">
          <SpotifyMark className="text-ink" />
          <p className="min-w-0 flex-1 text-small">
            {about ? (
              <>
                <strong className="font-strong">{about.title}</strong>
                {about.subtitle && `, ${about.subtitle}`}
              </>
            ) : (
              <>
                <strong className="font-strong">{word}</strong> on Spotify
              </>
            )}
          </p>
          <Button
            ref={(element) => {
              remove.current = element;
              pointAt(element);
            }}
            variant="quiet"
            aria-label={`Remove the soundtrack, ${about ? about.title : `${word.toLowerCase()} on Spotify`}`}
            onClick={() => {
              onChange(null);
              setPicked(null);
              requestAnimationFrame(() => input.current?.focus());
            }}
          >
            Remove
          </Button>
        </div>
      </div>
    );
  }

  const results = words !== null && search.data ? search.data : null;
  const searchFailed = search.isError && !unavailableProblem(search.error);

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="text-small font-strong">
        {unavailable ? 'Soundtrack: Spotify link' : 'Soundtrack'}
      </label>
      <div className="flex gap-2">
        <input
          id={id}
          ref={(element) => {
            input.current = element;
            pointAt(element);
          }}
          value={text}
          maxLength={200}
          autoComplete="off"
          enterKeyHint={unavailable ? 'done' : 'search'}
          aria-describedby={`${id}-hint`}
          aria-invalid={problem ? true : undefined}
          onChange={(event) => {
            const next = event.target.value;
            setText(next);
            setProblem(null);
            // A pasted link is taken as it is: nothing to press (F-02).
            const link = spotifyUri(next);
            if (link.ok) choose(link.uri, null);
          }}
          onKeyDown={(event) => {
            // Enter searches (or reads the link); it must not submit the form around the field.
            if (event.key === 'Enter') {
              event.preventDefault();
              submit();
            }
          }}
          className="min-h-11 min-w-0 flex-1 rounded-sm border border-control bg-page px-3 text-body text-ink aria-invalid:border-danger"
        />
        {!unavailable && (
          <Button onClick={submit} disabled={text.trim() === '' || search.isFetching}>
            Search
          </Button>
        )}
      </div>
      <p id={`${id}-hint`} className="text-small text-ink-2">
        {unavailable ? PASTE_HINT : SEARCH_HINT}
      </p>
      {/* Said once, as it happens; afterwards the label and the hint say what the field is for. */}
      {unavailableProblem(search.error) && (
        <p role="alert" className="text-small">
          {problemMessage(search.error)}
        </p>
      )}
      {problem && (
        <p role="alert" className="text-small text-danger">
          {problem}
        </p>
      )}
      {searchFailed && (
        <p role="alert" className="text-small text-danger">
          {problemMessage(search.error)}
        </p>
      )}
      {words !== null && search.isFetching && (
        <p role="status" className="text-small text-ink-2">
          Searching…
        </p>
      )}
      {results && !search.isFetching && results.length === 0 && (
        <p role="status" className="text-small text-ink-2">
          Nothing found. Try other words, or paste a link.
        </p>
      )}
      {results && results.length > 0 && (
        <ul aria-label="Search results" className="flex flex-col divide-y divide-line">
          {results.map((result) => {
            const Icon = ICONS[result.kind];
            return (
              <li key={result.uri}>
                <button
                  type="button"
                  onClick={() =>
                    choose(result.uri, { title: result.title, subtitle: result.subtitle })
                  }
                  className="flex min-h-14 w-full items-center gap-3 py-2 text-left focus-visible:-outline-offset-2"
                >
                  <Icon size={20} aria-hidden className="shrink-0 text-ink-2" />
                  <span className="flex min-w-0 flex-col">
                    <span className="line-clamp-1 text-body font-strong">{result.title}</span>
                    <span className="line-clamp-1 text-small text-ink-2">
                      {result.subtitle
                        ? `${kindWord(result.kind)} · ${result.subtitle}`
                        : kindWord(result.kind)}
                    </span>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

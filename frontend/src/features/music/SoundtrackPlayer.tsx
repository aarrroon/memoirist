import { useEffect, useRef, useState } from 'react';
import { SpotifyMark } from '../../components/SpotifyMark';
import { Button } from '../../components/ui/button';
import { cn } from '../../lib/cn';
import { embedUrl, kindWord, uriKind } from '../../lib/spotify';

// The soundtrack row (DESIGN.md §4 Soundtrack row, D-35, task 4.15): Spotify's mark, what it is, and a quiet Play.
// Nothing from Spotify is on the page until Play is pressed: no iframe, no image, no script (design.md §6, PRD
// Privacy). Only then is Spotify's own embedded player added, and focus moves into it, so a keyboard user carries on
// there. The server stores only the URI (D-35), so the row names the kind ("Song on Spotify"), not the song's title:
// the title is Spotify's to tell, and asking would contact Spotify before the tap.

/** The compact player's height: Spotify draws its 80 px player for a track, an album and a playlist alike. */
const PLAYER_HEIGHT = 80;

export function SoundtrackPlayer({
  uri,
  onBand = false,
}: {
  uri: string;
  /** On an Era's title band, in the band's own ink (DESIGN.md §4 Title band). */
  onBand?: boolean;
}) {
  const [playing, setPlaying] = useState(false);
  const frame = useRef<HTMLIFrameElement>(null);
  const kind = uriKind(uri);
  const src = embedUrl(uri);

  useEffect(() => {
    if (playing) frame.current?.focus();
  }, [playing]);

  // A value the server would never store draws nothing, rather than a player that can't load.
  if (!kind || !src) return null;
  const word = kindWord(kind);

  return (
    <div className="flex flex-col gap-2">
      <div className="flex min-h-11 items-center gap-3">
        <SpotifyMark className={onBand ? 'text-on-band' : 'text-ink'} />
        <p className="min-w-0 flex-1 text-small">
          <strong className="font-strong">{word}</strong> on Spotify
        </p>
        {!playing && (
          <Button
            variant="quiet"
            className={cn(onBand && 'text-on-band')}
            aria-label={`Play the ${word.toLowerCase()} on Spotify`}
            onClick={() => setPlaying(true)}
          >
            Play
          </Button>
        )}
      </div>
      {playing && (
        <iframe
          ref={frame}
          src={src}
          title={`Spotify player for the ${word.toLowerCase()}`}
          width="100%"
          height={PLAYER_HEIGHT}
          // Spotify's player needs its scripts and its own origin's storage; it may open Spotify in a new tab. It may
          // not move this page anywhere (no allow-top-navigation).
          sandbox="allow-scripts allow-same-origin allow-popups allow-popups-to-escape-sandbox"
          allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture"
          className="h-20 w-full rounded-lg border-0"
        />
      )}
    </div>
  );
}

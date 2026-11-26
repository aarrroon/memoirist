import markUrl from '../assets/spotify-mark.svg?no-inline';
import { cn } from '../lib/cn';

// Spotify's mark beside a soundtrack (DESIGN.md §4 Soundtrack row, task 4.15). The path is Spotify's own logo, kept in
// one file in src/assets and placed with <use>, as the travelling star is (components/TravellingStar.tsx), so it takes
// `color` from here: the ink token, black in light mode and white in dark, never green (design.md §7). The file is
// ours, served by the app: showing the mark contacts nobody (D-35). It is decorative: the text beside it always says
// "… on Spotify", so a label here would make a screen reader say Spotify twice (4.15 review).
export function SpotifyMark({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={20}
      height={20}
      aria-hidden="true"
      focusable="false"
      className={cn('shrink-0', className)}
    >
      <use href={`${markUrl}#spotify-mark`} />
    </svg>
  );
}

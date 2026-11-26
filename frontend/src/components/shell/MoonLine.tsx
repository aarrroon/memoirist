import { useMe } from '../../api/users';
import { greeting } from '../../lib/time';
import { useNow } from '../../lib/useNow';
import { Moon } from '../Moon';

// The moon date line: the moon in today's phase and "Wednesday at dusk" in `title` (DESIGN.md §4 Shell). It
// replaces a wordmark at the top of the Journal, Timeline, Eras and You, and heads the laptop's side nav.
// The greeting needs the user's timezone, so until GET /users/me answers the line stays empty at its full height
// (no guessing from the browser's zone, frontend.md), and nothing below it moves when it fills in.
export function MoonLine() {
  const zone = useMe().data?.timezone;
  const now = useNow();
  return (
    <p className="flex min-h-11 items-center gap-3">
      {zone && (
        <>
          <Moon now={now} />
          <span className="font-display text-title">{greeting(now, zone)}</span>
        </>
      )}
    </p>
  );
}

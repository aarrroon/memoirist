import { useEffect, useState } from 'react';

/**
 * The current instant, refreshed every `everyMs` (a minute by default), so the moon line turns from "afternoon"
 * to "at dusk" and "Today" moves on at midnight without a reload. Formatting it is time.ts's job.
 */
export function useNow(everyMs = 60_000): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), everyMs);
    return () => clearInterval(id);
  }, [everyMs]);
  return now;
}

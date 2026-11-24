import { useEffect, useState } from 'react';

/**
 * False for the first `ms` milliseconds, then true. A list shows its rows' shapes only after 300 ms (DESIGN.md §7),
 * so a fast answer never flashes a skeleton for a frame before the real thing.
 */
export function useDelayed(ms = 300): boolean {
  const [passed, setPassed] = useState(false);
  useEffect(() => {
    const id = setTimeout(() => setPassed(true), ms);
    return () => clearTimeout(id);
  }, [ms]);
  return passed;
}

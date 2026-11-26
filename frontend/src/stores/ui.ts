import { create } from 'zustand';

// UI state only (D-13, frontend.md): which panel is open, never anything the server owns. Server data lives in
// TanStack Query's cache; copying it here would give two answers to "what is this entry's title?".

/**
 * What capture opens as (DESIGN.md §4 Shell): a new entry, a photo dump (D-34) or a diary page (D-20) from the Write
 * menu, or the first letter from the first-run screen (task 4.16, D-33), which is sealed for a month when saved.
 */
export type CaptureKind = 'entry' | 'dump' | 'page' | 'letter';

interface UiState {
  /** The capture view that is open, or null. features/capture/CaptureHost draws the sheet (phone) or dialog (laptop)
   *  that reads it (task 4.6). 'dump' opens the photo dump (task 4.13, features/capture/PhotoDump.tsx). */
  capture: CaptureKind | null;
  /**
   * The opened Loot Drop that *Reply to past you* answers (task 4.16, D-33), or null. Only its id: which entry the
   * open sheet is about is UI state, while the Loot Drop itself stays in the query cache.
   */
  replyTo: string | null;
  openCapture: (kind: CaptureKind, options?: { replyTo?: string }) => void;
  closeCapture: () => void;
}

// `create<UiState>()(...)`, with the extra (), is Zustand's curried form for TypeScript: it lets the type be
// given while the rest is inferred. Components read a slice with a selector, useUiStore((s) => s.capture), so
// they re-render only when that slice changes.
export const useUiStore = create<UiState>()((set) => ({
  capture: null,
  replyTo: null,
  openCapture: (kind, options) => set({ capture: kind, replyTo: options?.replyTo ?? null }),
  closeCapture: () => set({ capture: null, replyTo: null }),
}));

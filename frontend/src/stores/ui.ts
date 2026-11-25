import { create } from 'zustand';

// UI state only (D-13, frontend.md): which panel is open, never anything the server owns. Server data lives in
// TanStack Query's cache; copying it here would give two answers to "what is this entry's title?".

/** What the Write menu opens (DESIGN.md §4 Shell): a new entry, a photo dump (D-34) or a diary page (D-20). */
export type CaptureKind = 'entry' | 'dump' | 'page';

interface UiState {
  /** The capture view that is open, or null. features/capture/CaptureHost draws the sheet (phone) or dialog (laptop)
   *  that reads it (task 4.6). 'dump' opens the photo dump (task 4.13, features/capture/PhotoDump.tsx). */
  capture: CaptureKind | null;
  openCapture: (kind: CaptureKind) => void;
  closeCapture: () => void;
}

// `create<UiState>()(...)`, with the extra (), is Zustand's curried form for TypeScript: it lets the type be
// given while the rest is inferred. Components read a slice with a selector, useUiStore((s) => s.capture), so
// they re-render only when that slice changes.
export const useUiStore = create<UiState>()((set) => ({
  capture: null,
  openCapture: (kind) => set({ capture: kind }),
  closeCapture: () => set({ capture: null }),
}));

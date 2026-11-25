import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import {
  APPEARANCE_KEY,
  readAppearance,
  resolveTheme,
  setAppearance,
  useAppearance,
} from './appearance';

// Task 4.9: Appearance is kept on the device and switches the whole app through data-theme on <html> (D-41).
let phoneIsDark = false;

beforeEach(() => {
  phoneIsDark = false;
  vi.stubGlobal(
    'matchMedia',
    vi.fn((query: string) => ({
      matches: query.includes('dark') && phoneIsDark,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  window.localStorage.clear();
  delete document.documentElement.dataset.theme;
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('appearance', () => {
  test('Match phone is the default and follows the phone', () => {
    expect(readAppearance()).toBe('system');
    expect(resolveTheme('system', true)).toBe('dark');
    expect(resolveTheme('system', false)).toBe('light');
    phoneIsDark = true;
    setAppearance('system');
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  test('Light or Dark wins over the phone and is kept on the device', () => {
    phoneIsDark = true;
    setAppearance('light');
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(window.localStorage.getItem(APPEARANCE_KEY)).toBe('light');
    setAppearance('dark');
    expect(document.documentElement.dataset.theme).toBe('dark');
    // Match phone forgets the choice, so a reload follows the phone again.
    setAppearance('system');
    expect(window.localStorage.getItem(APPEARANCE_KEY)).toBeNull();
  });

  test('an unknown stored value reads as Match phone', () => {
    window.localStorage.setItem(APPEARANCE_KEY, 'sepia');
    expect(readAppearance()).toBe('system');
  });

  test('blocked storage still applies the choice', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    setAppearance('dark');
    expect(document.documentElement.dataset.theme).toBe('dark');
    vi.restoreAllMocks();
  });

  test('useAppearance re-renders on a change here and in another tab', () => {
    const { result } = renderHook(() => useAppearance());
    expect(result.current).toBe('system');
    act(() => setAppearance('dark'));
    expect(result.current).toBe('dark');
    act(() => {
      window.localStorage.setItem(APPEARANCE_KEY, 'light');
      window.dispatchEvent(new StorageEvent('storage', { key: APPEARANCE_KEY }));
    });
    expect(result.current).toBe('light');
  });
});

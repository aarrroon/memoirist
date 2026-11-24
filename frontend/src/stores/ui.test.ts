import { afterEach, expect, test } from 'vitest';
import { useUiStore } from './ui';

afterEach(() => useUiStore.setState({ capture: null }));

test('nothing is open at first', () => {
  expect(useUiStore.getState().capture).toBeNull();
});

test('the Write menu opens one capture view at a time, and closing clears it', () => {
  const { openCapture, closeCapture } = useUiStore.getState();

  openCapture('dump');
  expect(useUiStore.getState().capture).toBe('dump');

  openCapture('page');
  expect(useUiStore.getState().capture).toBe('page');

  closeCapture();
  expect(useUiStore.getState().capture).toBeNull();
});

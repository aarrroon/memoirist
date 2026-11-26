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

test('Reply to past you opens capture with the Loot Drop it answers, and closing forgets it (task 4.16)', () => {
  const { openCapture, closeCapture } = useUiStore.getState();

  openCapture('entry', { replyTo: 'entry-letter' });
  expect(useUiStore.getState()).toMatchObject({ capture: 'entry', replyTo: 'entry-letter' });

  closeCapture();
  expect(useUiStore.getState()).toMatchObject({ capture: null, replyTo: null });

  // The Write menu never carries an old target along.
  openCapture('entry');
  expect(useUiStore.getState().replyTo).toBeNull();
});

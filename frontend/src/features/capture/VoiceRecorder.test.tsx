import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { useUiStore } from '../../stores/ui';
import { fakeBackend } from '../../test/msw/capture';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';
import { recorderReducer, type RecorderState } from './useRecorder';

// Voice notes in capture (task 4.7), against the fake entry API (MSW) and a fake microphone. jsdom has neither
// MediaRecorder nor getUserMedia, so the tests give it the smallest stand-ins that behave like Chrome's: a recorder
// that hands over one chunk when stopped. jsdom also has no AudioContext, so these recordings have no bars, as in a
// browser without Web Audio. The real recorder was tried in Chrome (journal 4.7, Evidence).

// Shrinking needs a canvas, which jsdom lacks: photos go up as they were picked.
vi.mock('browser-image-compression', () => ({ default: (file: File) => Promise.resolve(file) }));

const NOW = new Date('2026-11-24T12:47:00Z');

let backend: ReturnType<typeof fakeBackend>;
let supported: string[];
let getUserMedia: ReturnType<typeof vi.fn>;
const stopped: string[] = [];

class FakeRecorder {
  static isTypeSupported = (type: string) => supported.includes(type);
  state: 'inactive' | 'recording' = 'inactive';
  mimeType: string;
  ondataavailable: ((event: { data: Blob }) => void) | null = null;
  onstop: (() => void) | null = null;
  onerror: (() => void) | null = null;
  constructor(_stream: MediaStream, options: { mimeType: string }) {
    this.mimeType = options.mimeType;
  }
  start() {
    this.state = 'recording';
  }
  stop() {
    this.state = 'inactive';
    this.ondataavailable?.({ data: new Blob([new Uint8Array(4096)], { type: this.mimeType }) });
    this.onstop?.();
  }
}

const fakeStream = () =>
  ({
    getTracks: () => [{ stop: () => stopped.push('track') }],
  }) as unknown as MediaStream;

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
  backend = fakeBackend();
  server.use(...backend.handlers);
  supported = ['audio/webm;codecs=opus', 'audio/mp4'];
  stopped.length = 0;
  getUserMedia = vi.fn(() => Promise.resolve(fakeStream()));
  vi.stubGlobal('MediaRecorder', FakeRecorder);
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: { getUserMedia },
  });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  Reflect.deleteProperty(navigator, 'mediaDevices');
  useUiStore.setState({ capture: null });
  localStorage.clear();
});

async function openCapture() {
  const user = userEvent.setup();
  renderApp('/?write=entry');
  const sheet = await screen.findByRole('dialog', { name: 'New entry' }, { timeout: 10_000 });
  await within(sheet).findByRole('button', { name: /Memory date/ });
  return { user, sheet };
}

describe('recording a voice note', { timeout: 15_000 }, () => {
  test('records with Opus in WebM, uploads it on save, and the feed plays it back', async () => {
    const { user, sheet } = await openCapture();

    await user.click(within(sheet).getByRole('button', { name: 'Voice' }));
    // Focus moves to Stop, so Enter ends the recording.
    const stop = await within(sheet).findByRole('button', { name: 'Stop recording' });
    expect(stop).toHaveFocus();
    expect(within(sheet).getByText('Recording')).toBeVisible();
    expect(getUserMedia).toHaveBeenCalledWith({ audio: true });
    await user.keyboard('{Enter}');

    const notes = await within(sheet).findByRole('list', { name: 'Voice notes' });
    expect(within(notes).getByRole('button', { name: 'Play voice note' })).toBeVisible();
    expect(within(notes).getByRole('slider', { name: 'Position in voice note' })).toBeVisible();
    // The microphone was handed back as soon as the recording stopped.
    expect(stopped).toEqual(['track']);
    // A voice note alone is something to save (D-23).
    const save = within(sheet).getByRole('button', { name: 'Save' });
    expect(save).toBeEnabled();

    await user.click(save);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    const upload = backend.state.calls.find((c) => c.path.endsWith('/media'));
    expect(upload?.body).toEqual({
      purpose: 'PHOTO',
      name: 'voice-note.webm',
      type: 'audio/webm;codecs=opus',
    });
    // The new entry is in the feed with its voice note row, and its display title says what it is (D-24).
    const thisWeek = await screen.findByRole('region', { name: 'This week · 2 entries' });
    expect(within(thisWeek).getByRole('button', { name: 'Play voice note' })).toBeVisible();
  });

  test('records AAC in MP4 where WebM can’t be recorded, as on Safari and iOS', async () => {
    supported = ['audio/mp4'];
    const { user, sheet } = await openCapture();

    await user.click(within(sheet).getByRole('button', { name: 'Voice' }));
    await user.click(await within(sheet).findByRole('button', { name: 'Stop recording' }));
    await within(sheet).findByRole('list', { name: 'Voice notes' });
    await user.click(within(sheet).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    const upload = backend.state.calls.find((c) => c.path.endsWith('/media'));
    expect(upload?.body).toMatchObject({ name: 'voice-note.m4a', type: 'audio/mp4' });
  });

  test('a photo picked after a recording is sent first, so the entry is still named by its photo (D-24)', async () => {
    const { user, sheet } = await openCapture();

    await user.click(within(sheet).getByRole('button', { name: 'Voice' }));
    await user.click(await within(sheet).findByRole('button', { name: 'Stop recording' }));
    await within(sheet).findByRole('list', { name: 'Voice notes' });
    await user.upload(
      within(sheet).getByLabelText('Add photos'),
      new File([new Uint8Array(64)], 'walk.jpg', { type: 'image/jpeg' }),
    );
    await within(sheet).findByRole('button', { name: 'Photo 1 of 1. Options' });
    await user.click(within(sheet).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // The photo uploads first and the order sent puts it first: display_order 0 is the photo.
    const uploads = backend.state.calls.filter((c) => c.path.endsWith('/media'));
    expect(uploads.map((c) => (c.body as { type: string }).type)).toEqual([
      'image/jpeg',
      'audio/webm;codecs=opus',
    ]);
    const order = backend.state.calls.find((c) => c.path.endsWith('/media/order'));
    expect(order?.body).toEqual({ mediaIds: ['media-new-1', 'media-new-2'] });
  });

  test('Discard throws the recording away, frees the microphone and keeps Save off', async () => {
    const { user, sheet } = await openCapture();

    await user.click(within(sheet).getByRole('button', { name: 'Voice' }));
    await within(sheet).findByRole('button', { name: 'Stop recording' });
    await user.click(within(sheet).getByRole('button', { name: 'Discard' }));

    expect(within(sheet).queryByRole('list', { name: 'Voice notes' })).not.toBeInTheDocument();
    expect(stopped).toEqual(['track']);
    expect(within(sheet).getByRole('button', { name: 'Save' })).toBeDisabled();
  });

  test('a refused microphone explains how to allow it, and photos still work (F-03)', async () => {
    getUserMedia.mockRejectedValueOnce(new DOMException('Permission denied', 'NotAllowedError'));
    const { user, sheet } = await openCapture();

    await user.click(within(sheet).getByRole('button', { name: 'Voice' }));

    const alert = await within(sheet).findByRole('alert');
    expect(alert).toHaveTextContent('Allow it for this site in your browser’s settings');
    expect(alert).toHaveTextContent('Photos still work.');
    await user.click(within(alert).getByRole('button', { name: 'Try again' }));
    expect(await within(sheet).findByRole('button', { name: 'Stop recording' })).toBeVisible();
  });

  test('no microphone says so', async () => {
    getUserMedia.mockRejectedValueOnce(new DOMException('none', 'NotFoundError'));
    const { user, sheet } = await openCapture();

    await user.click(within(sheet).getByRole('button', { name: 'Voice' }));

    expect(await within(sheet).findByRole('alert')).toHaveTextContent('No microphone was found.');
  });

  test('Voice is hidden when the browser can record neither format (F-03)', async () => {
    supported = [];
    const { sheet } = await openCapture();

    expect(within(sheet).getByRole('button', { name: /Photo/ })).toBeVisible();
    expect(within(sheet).queryByRole('button', { name: 'Voice' })).toBeNull();
  });

  test('Voice is off once the entry holds 20 photos and voice notes (D-17)', async () => {
    const { user, sheet } = await openCapture();
    const files = Array.from(
      { length: 20 },
      (_, i) => new File([new Uint8Array(64)], `p${i}.jpg`, { type: 'image/jpeg' }),
    );
    await user.upload(within(sheet).getByLabelText('Add photos'), files);
    await within(sheet).findByRole(
      'button',
      { name: 'Photo 20 of 20. Options' },
      { timeout: 5000 },
    );

    expect(within(sheet).getByRole('button', { name: 'Voice' })).toBeDisabled();
  });
});

describe('recorderReducer', () => {
  const idle: RecorderState = { status: 'idle' };

  test('goes idle → asking → recording → idle', () => {
    const asking = recorderReducer(idle, { type: 'ask' });
    expect(asking).toEqual({ status: 'asking' });
    const recording = recorderReducer(asking, { type: 'started' });
    expect(recording).toEqual({ status: 'recording' });
    expect(recorderReducer(recording, { type: 'finished' })).toEqual(idle);
  });

  test('ignores a start that wasn’t asked for: a prompt answered after Discard records nothing', () => {
    expect(recorderReducer(idle, { type: 'started' })).toBe(idle);
  });

  test('a refusal or failure only follows asking or recording, and Try again asks again', () => {
    const asking: RecorderState = { status: 'asking' };
    const denied = recorderReducer(asking, { type: 'denied' });
    expect(denied).toEqual({ status: 'denied' });
    expect(recorderReducer(denied, { type: 'ask' })).toEqual({ status: 'asking' });
    expect(recorderReducer(idle, { type: 'failed', reason: 'other' })).toBe(idle);
    expect(recorderReducer(asking, { type: 'failed', reason: 'no-microphone' })).toEqual({
      status: 'failed',
      reason: 'no-microphone',
    });
  });

  test('asking again while recording changes nothing', () => {
    const recording: RecorderState = { status: 'recording' };
    expect(recorderReducer(recording, { type: 'ask' })).toBe(recording);
  });
});

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { ApiProblem } from '../../api/problem';
import { fakeBackend } from '../../test/msw/capture';
import { server } from '../../test/msw/server';
import { saveEntry, type EntryFields, type SaveInput, type TrayItem } from './saveEntry';

// The save flow against a fake API (MSW): create with the client's id → upload → reorder (D-12, D-23, G-03).

// Since task 4.13 a photo is shrunk as it uploads. Shrinking needs a canvas, which jsdom lacks (the library would wait
// on a web worker that never answers), so the file goes up as it was picked.
vi.mock('browser-image-compression', () => ({ default: (file: File) => Promise.resolve(file) }));

let backend: ReturnType<typeof fakeBackend>;
beforeEach(() => {
  backend = fakeBackend();
  server.use(...backend.handlers);
});

const fields = (over: Partial<EntryFields> = {}): EntryFields => ({
  entryDate: '2026-11-20',
  templateId: null,
  title: 'Dumplings',
  contentJson: null,
  tagIds: ['tag-family'],
  soundtrack: null,
  replyToEntryId: null,
  ...over,
});

const photo = (key: string, purpose: TrayItem['purpose'] = 'PHOTO'): TrayItem => ({
  key,
  file: new File([new Uint8Array(100)], `${key}.jpg`, { type: 'image/jpeg' }),
  previewUrl: `blob:${key}`,
  mediaType: 'image/jpeg',
  purpose,
  status: 'waiting',
  mediaId: null,
  error: null,
});

const input = (over: Partial<SaveInput> = {}): SaveInput => ({
  clientId: 'client-1',
  saved: null,
  fields: fields(),
  items: [],
  removedMediaIds: [],
  ...over,
});

const noop = () => undefined;

describe('saveEntry', () => {
  test('creates with the client id, uploads in order, then puts that order', async () => {
    const updates: [string, Partial<TrayItem>][] = [];
    const { entry, failed } = await saveEntry(
      input({ items: [photo('a'), photo('b', 'PAGE_SCAN')] }),
      (key, patch) => updates.push([key, patch]),
    );

    expect(failed).toBe(0);
    expect(entry?.id).toBe('client-1');
    expect(backend.state.calls.map((c) => `${c.method} ${c.path}`)).toEqual([
      'POST /entries',
      'POST /entries/client-1/media',
      'POST /entries/client-1/media',
      'PUT /entries/client-1/media/order',
    ]);
    expect(backend.state.calls[0]?.body).toMatchObject({
      id: 'client-1',
      title: 'Dumplings',
      tagIds: ['tag-family'],
    });
    // The diary page goes up as a page (D-20).
    expect(backend.state.calls[2]?.body).toMatchObject({ purpose: 'PAGE_SCAN', name: 'b.jpg' });
    expect(backend.state.calls[3]?.body).toEqual({ mediaIds: ['media-new-1', 'media-new-2'] });
    expect(updates).toContainEqual([
      'b',
      { status: 'uploaded', mediaId: 'media-new-2', error: null },
    ]);
  });

  test('sent again after a lost answer, the same client id gives back the same entry, not a second one', async () => {
    await saveEntry(input(), noop);
    const again = await saveEntry(input(), noop);
    expect(again.entry?.id).toBe('client-1');
    expect(backend.state.entries.size).toBe(1);
  });

  test('a failed upload keeps the entry with what did upload, and reports the failure on its item', async () => {
    backend.state.failUploads.add(2);
    const updates: [string, Partial<TrayItem>][] = [];
    const { entry, failed } = await saveEntry(
      input({ items: [photo('a'), photo('b')] }),
      (key, patch) => updates.push([key, patch]),
    );

    expect(failed).toBe(1);
    expect(entry?.media.map((m) => m.id)).toEqual(['media-new-1']);
    expect(updates).toContainEqual([
      'b',
      { status: 'failed', error: 'Photos can’t be reached right now. Try again soon.' },
    ]);
    // One uploaded photo needs no order.
    expect(backend.state.calls.some((c) => c.path.endsWith('/order'))).toBe(false);
  });

  test('an entry of only photos that all failed is deleted, so no empty entry is left (D-23)', async () => {
    backend.state.failUploads.add(1);
    const { entry, failed } = await saveEntry(
      input({ fields: fields({ title: null, tagIds: [] }), items: [photo('a')] }),
      noop,
    );
    expect(failed).toBe(1);
    expect(entry).toBeNull();
    expect(backend.state.entries.size).toBe(0);
    expect(backend.state.calls.at(-1)).toMatchObject({
      method: 'DELETE',
      path: '/entries/client-1',
    });
  });

  test('once saved, the next save is a full PUT with the version and every field (D-26)', async () => {
    const first = await saveEntry(input(), noop);
    await saveEntry(
      input({
        saved: { id: 'client-1', version: first.entry?.version ?? -1 },
        fields: fields({ tagIds: [], title: 'Dumplings, again' }),
      }),
      noop,
    );
    const put = backend.state.calls.find((c) => c.method === 'PUT');
    expect(put?.body).toEqual({
      version: 0,
      entryDate: '2026-11-20',
      title: 'Dumplings, again',
      contentJson: null,
      tagIds: [],
    });
  });

  test('photos already on the server but not in the tray stay in the order (a restored draft, D-23)', async () => {
    backend.state.failUploads.add(2);
    const first = await saveEntry(input({ items: [photo('a'), photo('b')] }), noop);
    expect(first.failed).toBe(1);

    // After a reload the tray starts without 'a', which is already on the server; two new photos are added.
    const second = await saveEntry(
      input({
        saved: { id: 'client-1', version: first.entry?.version ?? -1 },
        items: [photo('c'), photo('d')],
      }),
      noop,
    );
    expect(second.failed).toBe(0);
    expect(backend.state.calls.at(-1)?.body).toEqual({
      mediaIds: ['media-new-2', 'media-new-3', 'media-new-1'],
    });
    expect(backend.state.entries.get('client-1')?.media).toHaveLength(3);
  });

  test('an entry that still holds photos is not erased when only the new uploads fail', async () => {
    const first = await saveEntry(
      input({ fields: fields({ title: null, tagIds: [] }), items: [photo('a')] }),
      noop,
    );
    backend.state.failUploads.add(2);
    const second = await saveEntry(
      input({
        saved: { id: 'client-1', version: first.entry?.version ?? -1 },
        fields: fields({ title: null, tagIds: [] }),
        items: [photo('b')],
      }),
      noop,
    );
    expect(second.failed).toBe(1);
    expect(second.entry?.media.map((m) => m.id)).toEqual(['media-new-1']);
    expect(backend.state.calls.some((c) => c.method === 'DELETE')).toBe(false);
  });

  test('a stale version fails the save with entry-stale and uploads nothing (D-23)', async () => {
    await saveEntry(input(), noop);
    const stale = saveEntry(
      input({ saved: { id: 'client-1', version: 7 }, items: [photo('a')] }),
      noop,
    );
    await expect(stale).rejects.toBeInstanceOf(ApiProblem);
    await expect(stale).rejects.toMatchObject({ kind: 'entry-stale' });
    expect(backend.state.calls.some((c) => c.path.endsWith('/media'))).toBe(false);
  });

  test('a photo still over 25 MB once shrunk fails on its own item and is never sent (D-17, task 4.13)', async () => {
    const huge: TrayItem = {
      ...photo('huge'),
      file: new File([new Uint8Array(25 * 1024 * 1024 + 1)], 'huge.jpg', { type: 'image/jpeg' }),
    };
    const updates: [string, Partial<TrayItem>][] = [];
    const { entry, failed } = await saveEntry(input({ items: [photo('a'), huge] }), (key, patch) =>
      updates.push([key, patch]),
    );
    expect(failed).toBe(1);
    expect(entry?.id).toBe('client-1');
    expect(updates).toContainEqual([
      'huge',
      { status: 'failed', error: 'This file is over 25 MB.' },
    ]);
    expect(backend.state.calls.filter((c) => c.path.endsWith('/media'))).toHaveLength(1);
  });

  test('sends dateUnknown on a create only when asked (D-34, task 4.13)', async () => {
    await saveEntry(input({ fields: fields({ dateUnknown: true }), items: [photo('a')] }), noop);
    expect(backend.state.calls[0]?.body).toMatchObject({ dateUnknown: true });
    await saveEntry(input({ clientId: 'client-2', items: [photo('b')] }), noop);
    expect(
      backend.state.calls.find(
        (c) => c.method === 'POST' && (c.body as { id: string }).id === 'client-2',
      )?.body,
    ).not.toHaveProperty('dateUnknown');
  });
});

// Task 4.18 (D-43): the smaller copies go up in the same request as the photo. jsdom can't decode a picture, so the
// browser's createImageBitmap is stood in for with the size the photo would decode to.
describe('saveEntry with smaller copies', () => {
  const decodesTo = (width: number, height: number) => {
    const decode = vi.fn(() => Promise.resolve({ width, height, close: () => undefined }));
    vi.stubGlobal('createImageBitmap', decode);
    return decode;
  };
  afterEach(() => vi.unstubAllGlobals());

  test('a big photo goes up with a thumbnail and a screen copy, as JPEGs', async () => {
    decodesTo(2560, 1920);
    await saveEntry(input({ items: [photo('a')] }), noop);

    expect(backend.state.calls[1]?.body).toMatchObject({
      name: 'a.jpg',
      thumbnail: { name: 'a-thumbnail.jpg', type: 'image/jpeg' },
      screen: { name: 'a-screen.jpg', type: 'image/jpeg' },
    });
    // The fake server, like the real one, keeps a URL for each copy on the photo it stored.
    const stored = backend.state.entries.get('client-1')?.media[0];
    expect(stored?.thumbnailUrl).toContain('-thumbnail.jpg');
    expect(stored?.screenUrl).toContain('-screen.jpg');
  });

  test('a photo between the sizes gets only a thumbnail; a small one gets none', async () => {
    decodesTo(1200, 900);
    await saveEntry(input({ clientId: 'client-mid', items: [photo('mid')] }), noop);
    const mid = backend.state.calls.find((c) => c.path === '/entries/client-mid/media')?.body;
    expect(mid).toMatchObject({ thumbnail: { name: 'mid-thumbnail.jpg' } });
    expect(mid).not.toHaveProperty('screen');

    decodesTo(480, 320);
    await saveEntry(input({ clientId: 'client-small', items: [photo('small')] }), noop);
    const small = backend.state.calls.find((c) => c.path === '/entries/client-small/media')?.body;
    expect(small).not.toHaveProperty('thumbnail');
    expect(small).not.toHaveProperty('screen');
  });

  test('a voice note gets no copies and is never decoded', async () => {
    const decode = decodesTo(2560, 1920);
    const voice: TrayItem = {
      ...photo('v'),
      file: new File([new Uint8Array(100)], 'v.webm', { type: 'audio/webm' }),
      mediaType: 'audio/webm',
    };
    await saveEntry(input({ items: [voice] }), noop);
    expect(backend.state.calls[1]?.body).not.toHaveProperty('thumbnail');
    expect(decode).not.toHaveBeenCalled();
  });

  test('a retry after a failed upload makes the copies only once', async () => {
    const decode = decodesTo(2560, 1920);
    const item = photo('again');
    backend.state.failUploads.add(1);
    const first = await saveEntry(input({ items: [item] }), noop);
    expect(first.failed).toBe(1);

    await saveEntry(input({ items: [item] }), noop);
    expect(decode).toHaveBeenCalledTimes(1);
    const uploads = backend.state.calls.filter((c) => c.path.endsWith('/media'));
    expect(uploads.at(-1)?.body).toMatchObject({ thumbnail: { name: 'again-thumbnail.jpg' } });
  });
});

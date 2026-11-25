import { describe, expect, test } from 'vitest';
import { datedPhoto } from '../test/exif';
import { photoDay, readPhotoDay, usableDay } from './photoDate';

// A photo's own date (D-34, task 4.13). photoDay is the rule; readPhotoDay runs the real exifr over a JPEG's bytes.

const MELBOURNE = 'Australia/Melbourne';

describe('photoDay', () => {
  test('without an offset, the camera clock’s own day is the day', () => {
    expect(photoDay({ DateTimeOriginal: '2026:10:23 23:30:00' }, MELBOURNE)).toBe('2026-10-23');
    // The browser's zone plays no part: a UTC reading of the same text would still be the 23rd here.
    expect(photoDay({ DateTimeOriginal: '2026:10:23 00:10:00' }, 'America/Los_Angeles')).toBe(
      '2026-10-23',
    );
  });

  test('with an offset, the moment is moved into the user’s zone', () => {
    // 21:30 in Melbourne (+11:00) is still the 23rd there.
    expect(
      photoDay(
        { DateTimeOriginal: '2026:10:23 21:30:00', OffsetTimeOriginal: '+11:00' },
        MELBOURNE,
      ),
    ).toBe('2026-10-23');
    // 21:30 in San Francisco (-07:00) on the 23rd is 15:30 on the 24th in Melbourne.
    expect(
      photoDay(
        { DateTimeOriginal: '2026:10:23 21:30:00', OffsetTimeOriginal: '-07:00' },
        MELBOURNE,
      ),
    ).toBe('2026-10-24');
    // A half-hour zone: 01:00 in Adelaide (+10:30) is 00:30 in Melbourne (+11:00), the same day.
    expect(
      photoDay(
        { DateTimeOriginal: '2026:10:23 01:00:00', OffsetTimeOriginal: '+10:30' },
        MELBOURNE,
      ),
    ).toBe('2026-10-23');
  });

  test('falls back to the digitised time with its own offset', () => {
    expect(
      photoDay({ CreateDate: '2026:10:23 21:30:00', OffsetTimeDigitized: '-07:00' }, MELBOURNE),
    ).toBe('2026-10-24');
    // The original time wins when both are there.
    expect(
      photoDay(
        { DateTimeOriginal: '2026:10:20 10:00:00', CreateDate: '2026:10:23 10:00:00' },
        MELBOURNE,
      ),
    ).toBe('2026-10-20');
  });

  test('refuses dates that aren’t real: a clock never set, 30 February, junk, a Date object', () => {
    expect(photoDay({ DateTimeOriginal: '0000:00:00 00:00:00' }, MELBOURNE)).toBeNull();
    expect(photoDay({ DateTimeOriginal: '2026:02:30 10:00:00' }, MELBOURNE)).toBeNull();
    expect(photoDay({ DateTimeOriginal: '2026:13:01 10:00:00' }, MELBOURNE)).toBeNull();
    expect(photoDay({ DateTimeOriginal: '2026:10:23 25:00:00' }, MELBOURNE)).toBeNull();
    expect(photoDay({ DateTimeOriginal: 'yesterday' }, MELBOURNE)).toBeNull();
    expect(photoDay({ DateTimeOriginal: new Date() }, MELBOURNE)).toBeNull();
    expect(photoDay({}, MELBOURNE)).toBeNull();
  });

  test('ignores an offset it can’t read', () => {
    expect(
      photoDay({ DateTimeOriginal: '2026:10:23 21:30:00', OffsetTimeOriginal: 'Z' }, MELBOURNE),
    ).toBe('2026-10-23');
  });
});

describe('usableDay', () => {
  test('keeps days from 1900 to today, and nothing after today', () => {
    expect(usableDay('2026-11-24', '2026-11-24')).toBe('2026-11-24');
    expect(usableDay('2026-11-25', '2026-11-24')).toBeNull();
    expect(usableDay('1899-12-31', '2026-11-24')).toBeNull();
    expect(usableDay(null, '2026-11-24')).toBeNull();
  });
});

describe('readPhotoDay', () => {
  test('reads the date a camera wrote into a JPEG', async () => {
    expect(
      await readPhotoDay(datedPhoto('a.jpg', '2026:10:23 21:30:00', '-07:00'), MELBOURNE),
    ).toBe('2026-10-24');
    expect(await readPhotoDay(datedPhoto('b.jpg', '2026:10:23 08:00:00'), MELBOURNE)).toBe(
      '2026-10-23',
    );
  });

  test('gives null, never an error, for a photo with no date or a voice note', async () => {
    expect(await readPhotoDay(datedPhoto('c.jpg', null), MELBOURNE)).toBeNull();
    expect(
      await readPhotoDay(
        new File([new Uint8Array(10)], 'n.webm', { type: 'audio/webm' }),
        MELBOURNE,
      ),
    ).toBeNull();
    expect(
      await readPhotoDay(new File([new Uint8Array(10)], 'x.png', { type: 'image/png' }), MELBOURNE),
    ).toBeNull();
  });
});

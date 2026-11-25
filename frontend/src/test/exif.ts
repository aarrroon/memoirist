// A tiny JPEG that holds only an EXIF block with a date (task 4.13), so tests can run the real exifr over the bytes a
// camera writes: an APP1 segment ("Exif\0\0", a little-endian TIFF header, IFD0 pointing at the Exif IFD, which holds
// DateTimeOriginal and, when given, OffsetTimeOriginal). There is no picture in it; nothing here decodes one.

const DATE_TIME_ORIGINAL = 0x9003;
const OFFSET_TIME_ORIGINAL = 0x9011;
const EXIF_IFD_POINTER = 0x8769;
const ASCII = 2;
const LONG = 4;

/** The bytes of a JPEG whose EXIF says it was taken at `dateTime` ("2026:10:23 21:30:00"), at `offset` ("+11:00"). */
export function exifJpegBytes(dateTime: string, offset?: string): Uint8Array<ArrayBuffer> {
  const encoder = new TextEncoder();
  const strings: [number, string][] = [[DATE_TIME_ORIGINAL, `${dateTime}\0`]];
  if (offset) strings.push([OFFSET_TIME_ORIGINAL, `${offset}\0`]);

  const ifd0 = 8;
  const exifIfd = ifd0 + 2 + 12 + 4;
  let data = exifIfd + 2 + 12 * strings.length + 4;
  const tiff = new Uint8Array(data + strings.reduce((n, [, s]) => n + s.length, 0));
  const view = new DataView(tiff.buffer);
  tiff.set([0x49, 0x49, 0x2a, 0x00]);
  view.setUint32(4, ifd0, true);
  view.setUint16(ifd0, 1, true);
  view.setUint16(ifd0 + 2, EXIF_IFD_POINTER, true);
  view.setUint16(ifd0 + 4, LONG, true);
  view.setUint32(ifd0 + 6, 1, true);
  view.setUint32(ifd0 + 10, exifIfd, true);
  view.setUint32(ifd0 + 14, 0, true);
  view.setUint16(exifIfd, strings.length, true);
  strings.forEach(([tag, text], index) => {
    const at = exifIfd + 2 + index * 12;
    view.setUint16(at, tag, true);
    view.setUint16(at + 2, ASCII, true);
    view.setUint32(at + 4, text.length, true);
    view.setUint32(at + 8, data, true);
    tiff.set(encoder.encode(text), data);
    data += text.length;
  });
  view.setUint32(exifIfd + 2 + 12 * strings.length, 0, true);

  const header = encoder.encode('Exif\0\0');
  const length = 2 + header.length + tiff.length;
  return new Uint8Array([
    0xff,
    0xd8,
    0xff,
    0xe1,
    length >> 8,
    length & 0xff,
    ...header,
    ...tiff,
    0xff,
    0xd9,
  ]);
}

/** A picked JPEG photo taken at `dateTime`, or with no EXIF at all when `dateTime` is null. */
export function datedPhoto(name: string, dateTime: string | null, offset?: string): File {
  const bytes = dateTime
    ? exifJpegBytes(dateTime, offset)
    : new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);
  return new File([bytes], name, { type: 'image/jpeg' });
}

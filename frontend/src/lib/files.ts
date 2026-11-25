import imageCompression from 'browser-image-compression';

// Files picked in capture (D-17, task 4.6). The client checks the type, the size and the count before anything is
// sent, and shrinks photos first: the server keeps Tomcat's default swallow size, so a file far over 25 MB from the
// app could end in a dropped connection instead of a 413 (D-17, decided 2026-11-24).

/** The D-17 allow-list, as the file input's `accept` (frontend.md, Capture and media). HEIC is left out on purpose:
 *  iOS then hands over a JPEG. */
export const ACCEPT = 'image/jpeg,image/png,image/webp,audio/webm,audio/mp4,audio/mpeg';

const ALLOWED = new Set(ACCEPT.split(','));

/** 25 MB per file and 20 media per entry (D-17). */
export const MAX_FILE_BYTES = 25 * 1024 * 1024;
export const MAX_FILES = 20;

export const isImageType = (type: string) => type.startsWith('image/');

/** Why a file can't be added, in words for the user, or null when it can. */
export function fileProblem(file: { type: string; size: number }): string | null {
  // The declared type is what the server checks too (API_CONTRACT §Media); a parameter such as "; codecs=opus"
  // is cut off first, like the server does.
  const type = (file.type.split(';')[0] ?? '').trim().toLowerCase();
  if (!ALLOWED.has(type)) return 'This kind of file can’t be added. Use a JPEG, PNG or WebP photo.';
  if (file.size > MAX_FILE_BYTES) return 'This file is over 25 MB.';
  return null;
}

/** What a picture is for, which decides how hard it is shrunk (D-20): the upload's `purpose`. */
export type PicturePurpose = 'PHOTO' | 'PAGE_SCAN';

// Task 4.13 (TECH_STACK §3 Media): a diary page is shrunk more gently than a photo, so small handwriting stays
// readable when it is zoomed, and later read on a tap (D-20, Phase 4). Both stay far under the 25 MB of D-17.
const SETTINGS: Record<
  PicturePurpose,
  { maxSizeMB: number; maxWidthOrHeight: number; initialQuality: number }
> = {
  PHOTO: { maxSizeMB: 2, maxWidthOrHeight: 2560, initialQuality: 0.85 },
  PAGE_SCAN: { maxSizeMB: 6, maxWidthOrHeight: 4096, initialQuality: 0.92 },
};

/**
 * A photo shrunk and re-encoded for upload: at most 2560 px on its long side and about 2 MB, in its own format, or
 * at most 4096 px and about 6 MB for a diary page. Re-encoding applies the photo's rotation, so the size the server
 * reads is the size as seen (D-17), and drops its EXIF, so its date must be read first (lib/photoDate.ts, D-34).
 * Since task 4.13 capture keeps the picked file and shrinks it when it uploads, once its purpose is known. When the
 * browser can't shrink it (an old browser, a broken file), the original is returned and the size check decides.
 * Anything that isn't a photo is returned as it is.
 */
export async function shrinkPhoto(file: File, purpose: PicturePurpose = 'PHOTO'): Promise<File> {
  if (!isImageType(file.type)) return file;
  try {
    const shrunk = await imageCompression(file, {
      ...SETTINGS[purpose],
      useWebWorker: true,
      fileType: file.type,
    });
    // Never send a bigger file than the one picked: a small PNG can grow when it is re-encoded.
    return shrunk.size < file.size
      ? new File([shrunk], file.name, { type: file.type, lastModified: file.lastModified })
      : file;
  } catch {
    return file;
  }
}

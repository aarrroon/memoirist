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

/**
 * A photo shrunk and re-encoded for upload: at most 2560 px on its long side and about 2 MB, in its own format.
 * Re-encoding applies the photo's rotation, so the size the server reads is the size as seen (D-17). When the
 * browser can't shrink it (an old browser, a broken file), the original is returned and the size check decides.
 * Anything that isn't a photo is returned as it is.
 */
export async function shrinkPhoto(file: File): Promise<File> {
  if (!isImageType(file.type)) return file;
  try {
    const shrunk = await imageCompression(file, {
      maxSizeMB: 2,
      maxWidthOrHeight: 2560,
      useWebWorker: true,
      fileType: file.type,
      initialQuality: 0.85,
    });
    // Never send a bigger file than the one picked: a small PNG can grow when it is re-encoded.
    return shrunk.size < file.size
      ? new File([shrunk], file.name, { type: file.type, lastModified: file.lastModified })
      : file;
  } catch {
    return file;
  }
}

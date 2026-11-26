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
      // Not in a worker (task 4.18): browser-image-compression's worker loads the library from cdn.jsdelivr.net,
      // and no page calls another company's server (design.md §6).
      useWebWorker: false,
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

// Smaller copies (D-43, task 4.18). When a photo or diary page uploads, the browser also makes a thumbnail and a
// screen copy and sends them in the same request; the server keeps them on the photo's own row. Making them here
// needs no image library on the server (the JDK can't read WebP) and keeps the work off Phase 2's small host.

/** The longest side of each copy, in pixels (D-43). */
export const COPY_SIZES = { thumbnail: 480, screen: 1600 } as const;
export type CopySize = keyof typeof COPY_SIZES;

/** The copies that travel with one upload; a size the photo already fits in is left out. */
export type PhotoCopies = Partial<Record<CopySize, File>>;

/**
 * The copies a picture of this size needs: one for each size its longest side is bigger than. A photo no bigger than a
 * copy gets no copy of that size (D-43), and a size that can't be read (0, NaN, from a broken file) gets none, so the
 * screens fall back to the original.
 */
export function copiesNeeded(width: number, height: number): CopySize[] {
  const longest = Math.max(width, height);
  if (!Number.isFinite(longest) || longest <= 0) return [];
  return (Object.keys(COPY_SIZES) as CopySize[]).filter((size) => longest > COPY_SIZES[size]);
}

/** A WebP stays WebP; anything else becomes JPEG (D-43): a PNG photo's copy would be several times bigger as PNG. */
export const copyType = (type: string) => (type === 'image/webp' ? 'image/webp' : 'image/jpeg');

/** The picture's size in pixels as the browser decodes it, or null when it can't (jsdom, a broken file). */
async function pictureSize(file: Blob): Promise<{ width: number; height: number } | null> {
  if (typeof createImageBitmap !== 'function') return null;
  try {
    const bitmap = await createImageBitmap(file);
    const size = { width: bitmap.width, height: bitmap.height };
    bitmap.close();
    return size;
  } catch {
    return null;
  }
}

/**
 * Makes the copies of a photo that is about to upload: call it with the file as it is sent (already shrunk, so its
 * rotation is applied and the copies match it). A copy that fails is left out, and the screens use the next size up.
 */
export async function makeCopies(file: File): Promise<PhotoCopies> {
  if (!isImageType(file.type)) return {};
  const size = await pictureSize(file);
  if (!size) return {};
  const type = copyType(file.type);
  const base = file.name.replace(/\.[^.]*$/, '');
  const copies: PhotoCopies = {};
  for (const name of copiesNeeded(size.width, size.height)) {
    try {
      const copy = await imageCompression(file, {
        maxWidthOrHeight: COPY_SIZES[name],
        initialQuality: 0.82,
        fileType: type,
        // On the page's own thread: the library's worker loads its script from cdn.jsdelivr.net, and the app calls
        // no other company's server (design.md §6). The file is already at most 2560 px, so this is quick.
        useWebWorker: false,
      });
      copies[name] = new File([copy], `${base}-${name}.${type === 'image/webp' ? 'webp' : 'jpg'}`, {
        type,
        lastModified: file.lastModified,
      });
    } catch {
      // No copy of this size: the screens fall back to the next size up (D-43).
    }
  }
  return copies;
}

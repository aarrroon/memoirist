package com.memoirist.media;

import com.memoirist.common.UnsupportedFileTypeException;
import com.memoirist.common.ValidationFailedException;
import java.util.Locale;
import java.util.Map;

/**
 * What an upload may be: the allowed file types and their file extensions, the size and count limits (D-17), and the
 * two purposes a photo can have (D-20). Plain functions with no Spring beans and no database, so every rule is a
 * unit test away.
 *
 * <p>Each check throws the exception whose answer the contract names, with a fixed sentence that never repeats what
 * the client sent (a declared type, a purpose and a file name are all untrusted input).
 */
final class UploadRules {

    /** 25 MB per file (D-17). Spring's multipart limit in {@code application.yml} is the same number, and a test says so. */
    static final long MAX_FILE_BYTES = 25L * 1024 * 1024;

    /** At most 20 photos and voice notes in one entry (D-17). */
    static final int MAX_ITEMS_PER_ENTRY = 20;

    /**
     * The D-17 allow-list, and the extension each type is stored under. The extension comes from this table, never from
     * the client's file name, so a name like {@code ../../x.jpg} can't reach the object key.
     */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "audio/webm", "webm",
            "audio/mp4", "m4a",
            "audio/mpeg", "mp3");

    private UploadRules() {}

    // TODO(human) PRACTICE: Write allowedType: turn the Content-Type the client declared for the file into one of the
    //   six allowed types, or refuse the file with UnsupportedFileTypeException (415). Think about what the header can
    //   look like in real life: capital letters, a missing header, text that isn't a media type at all, and the
    //   parameters a browser's voice recorder adds. Decide what to return and what to store as media_type.
    // Hint: D-17 lists the types, and "Allowed types" in API_CONTRACT (Media) says what is stored. Chrome's
    //   MediaRecorder labels its recording "audio/webm;codecs=opus", which a plain equals() against the list would
    //   refuse. Compare an allow-list (what you accept), never a deny-list (what you think is bad). Only the part
    //   before the first ";" names the type, and the list itself is the validator: no parser is needed. (Spring's
    //   MediaType.parseMediaType was tried first and also checks the parameters, so "image/jpeg; charset=binary" made
    //   it throw. The list doesn't care about parameters.)
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    static String allowedType(String declaredContentType) {
        if (declaredContentType == null) {
            throw unsupportedType();
        }
        // "audio/webm;codecs=opus" -> "audio/webm". ROOT, so a Turkish-locale JVM doesn't turn "IMAGE" into "ımage".
        String type = declaredContentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.containsKey(type)) {
            throw unsupportedType();
        }
        return type;
    }
    // └── END ANSWER ▲ ──

    /** The file extension for an allowed type, as returned by {@link #allowedType}. */
    static String extensionFor(String allowedType) {
        String extension = EXTENSIONS.get(allowedType);
        if (extension == null) {
            throw new IllegalArgumentException("Not an allowed type: " + allowedType);
        }
        return extension;
    }

    static boolean isImage(String allowedType) {
        return allowedType.startsWith("image/");
    }

    /**
     * The purpose the client asked for: {@code PHOTO} when it asked for none, otherwise exactly {@code PHOTO} or
     * {@code PAGE_SCAN} (D-20). Anything else, an empty text and a different spelling included, is
     * {@code 400 validation-failed} on {@code purpose}.
     */
    static String purpose(String requested) {
        if (requested == null) {
            return Media.PURPOSE_PHOTO;
        }
        return switch (requested) {
            case Media.PURPOSE_PHOTO, Media.PURPOSE_PAGE_SCAN -> requested;
            default -> throw new ValidationFailedException("purpose", "must be PHOTO or PAGE_SCAN");
        };
    }

    /** A diary-page photo has to be a picture (D-20): {@code 415} for a voice note marked {@code PAGE_SCAN}. */
    static void requireImageForPageScan(String purpose, String allowedType) {
        if (Media.PURPOSE_PAGE_SCAN.equals(purpose) && !isImage(allowedType)) {
            throw new UnsupportedFileTypeException("A diary page must be a JPEG, PNG or WebP image.");
        }
    }

    /** The one sentence every refused type gets. It lists the allowed types and says nothing about the refused one. */
    private static UnsupportedFileTypeException unsupportedType() {
        return new UnsupportedFileTypeException(
                "This file type isn't supported. Use a JPEG, PNG or WebP image, or WebM, MP4 or MP3 audio.");
    }
}

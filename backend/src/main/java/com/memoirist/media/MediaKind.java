package com.memoirist.media;

/**
 * What kind of thing a media item is, for a list row (API_CONTRACT, EntrySummary): the {@code firstMediaKind} the client
 * turns into a label when an entry has no title and no caption (D-24).
 *
 * <p>It is worked out from the two columns the upload rules already fix (D-17, D-20), in one place, so the counts and
 * the first kind of a row can't disagree about what a voice note is:
 * <ul>
 *   <li>a file whose type starts with {@code audio/} is a {@link #VOICE_NOTE};
 *   <li>any other file is an image ({@code PAGE_SCAN} must be one, D-20), and it is a {@link #PAGE_SCAN} when its
 *       purpose says so, otherwise a {@link #PHOTO}.
 * </ul>
 */
public enum MediaKind {
    PHOTO,
    PAGE_SCAN,
    VOICE_NOTE;

    /** The kind of this item. Reads two plain columns of the row, so it costs no query. */
    public static MediaKind of(Media media) {
        if (media.getMediaType().startsWith("audio/")) {
            return VOICE_NOTE;
        }
        return Media.PURPOSE_PAGE_SCAN.equals(media.getPurpose()) ? PAGE_SCAN : PHOTO;
    }
}

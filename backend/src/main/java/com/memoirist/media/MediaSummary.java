package com.memoirist.media;

import java.util.Comparator;
import java.util.List;

/**
 * What a list row says about an entry's carousel: how many pictures, how many voice notes, and the kind of the first
 * item (API_CONTRACT, EntrySummary; D-24). Plain data and plain functions, no database and no Spring, so the rule is a
 * unit test away.
 *
 * <p>It is only ever built for an entry the user may see. {@code EntryMapper} never asks for it on a sealed entry (D-10).
 *
 * @param photoCount every image, diary pages included
 * @param voiceNoteCount every audio file
 * @param firstKind the kind of the item with the lowest {@code displayOrder}, or {@code null} for an entry with no media
 */
public record MediaSummary(int photoCount, int voiceNoteCount, MediaKind firstKind) {

    /** An entry with nothing in its carousel. */
    public static final MediaSummary EMPTY = new MediaSummary(0, 0, null);

    // TODO(human) PRACTICE: Write of(carousel): fold an entry's media into the three numbers a list row shows. Count
    //   the pictures (a diary page is one) and the voice notes, and name the kind of the *first* item. Decide what
    //   "first" means: the first element of the list you were given, or the item with the lowest displayOrder, and
    //   what each choice relies on.
    // Hint: API_CONTRACT (EntrySummary) and D-24. MediaKind.of(media) already says what one item is. An enum switch
    //   with arrows and a record are all you need; a list that Hibernate fills from a join isn't promised to be in
    //   any order unless something sorts it, so don't lean on that.
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    public static MediaSummary of(List<Media> carousel) {
        int photos = 0;
        int voiceNotes = 0;
        for (Media media : carousel) {
            switch (MediaKind.of(media)) {
                case PHOTO, PAGE_SCAN -> photos++;
                case VOICE_NOTE -> voiceNotes++;
            }
        }
        // The lowest displayOrder, not the head of the list: the list's order comes from an @OrderBy that a fetch join
        // applies, but a summary of "the first item" should not depend on that detail staying true.
        MediaKind first = carousel.stream()
                .min(Comparator.comparingInt(Media::getDisplayOrder))
                .map(MediaKind::of)
                .orElse(null);
        return new MediaSummary(photos, voiceNotes, first);
    }
    // └── END ANSWER ▲ ──
}

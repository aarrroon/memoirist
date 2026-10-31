package com.memoirist.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.entry.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What a list row says about a carousel (task 3.9, D-24): counts and the first kind, as plain logic. */
class MediaSummaryTest {

    private final UUID userId = UUID.randomUUID();
    private final Entry entry = Fixtures.entry(userId, Fixtures.week(userId));

    private Media item(String mediaType, String purpose, int order) {
        return new Media(UUID.randomUUID(), entry, "users/test/" + order, mediaType, purpose, order, 1024);
    }

    private Media photo(int order) {
        return item("image/jpeg", Media.PURPOSE_PHOTO, order);
    }

    private Media voice(int order) {
        return item("audio/webm", Media.PURPOSE_PHOTO, order);
    }

    private Media page(int order) {
        return item("image/png", Media.PURPOSE_PAGE_SCAN, order);
    }

    @Test
    void anEntryWithNoMediaHasNothingToCountAndNoFirstKind() {
        assertThat(MediaSummary.of(List.of())).isEqualTo(MediaSummary.EMPTY);
        assertThat(MediaSummary.EMPTY).isEqualTo(new MediaSummary(0, 0, null));
    }

    @Test
    void photosAndDiaryPagesAreBothPhotosAndVoiceNotesAreCountedApart() {
        MediaSummary summary = MediaSummary.of(List.of(photo(0), page(1), voice(2), photo(3), voice(4), voice(5)));

        assertThat(summary.photoCount()).isEqualTo(3);
        assertThat(summary.voiceNoteCount()).isEqualTo(3);
    }

    @Test
    void theFirstKindIsThatOfTheFirstItem() {
        assertThat(MediaSummary.of(List.of(photo(0), voice(1))).firstKind()).isEqualTo(MediaKind.PHOTO);
        assertThat(MediaSummary.of(List.of(voice(0), photo(1))).firstKind()).isEqualTo(MediaKind.VOICE_NOTE);
        assertThat(MediaSummary.of(List.of(page(0), photo(1))).firstKind()).isEqualTo(MediaKind.PAGE_SCAN);
    }

    @Test
    void firstMeansTheLowestDisplayOrderEvenIfTheListIsInAnotherOrder() {
        List<Media> shuffled = new ArrayList<>(List.of(voice(2), photo(0), page(1)));

        assertThat(MediaSummary.of(shuffled).firstKind()).isEqualTo(MediaKind.PHOTO);
    }

    @Test
    void aSingleItemIsBothTheCountAndTheFirstKind() {
        assertThat(MediaSummary.of(List.of(voice(0)))).isEqualTo(new MediaSummary(0, 1, MediaKind.VOICE_NOTE));
        assertThat(MediaSummary.of(List.of(page(0)))).isEqualTo(new MediaSummary(1, 0, MediaKind.PAGE_SCAN));
    }
}

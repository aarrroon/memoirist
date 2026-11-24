package com.memoirist.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.entry.Entry;
import com.memoirist.week.WeeklyLog;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What kind a media item is (task 3.9, D-17, D-20): plain logic on two columns, no database. */
class MediaKindTest {

    private final UUID userId = UUID.randomUUID();
    private final WeeklyLog week = Fixtures.week(userId);
    private final Entry entry = Fixtures.entry(userId, week);

    private Media media(String mediaType, String purpose) {
        return new Media(UUID.randomUUID(), entry, "users/test/key", mediaType, purpose, 0, 1024);
    }

    @ParameterizedTest
    @CsvSource({
        "image/jpeg, PHOTO, PHOTO",
        "image/png, PHOTO, PHOTO",
        "image/webp, PHOTO, PHOTO",
        "image/jpeg, PAGE_SCAN, PAGE_SCAN",
        "image/png, PAGE_SCAN, PAGE_SCAN",
        "audio/webm, PHOTO, VOICE_NOTE",
        "audio/mp4, PHOTO, VOICE_NOTE",
        "audio/mpeg, PHOTO, VOICE_NOTE",
    })
    void theKindComesFromTheTypeAndThePurpose(String mediaType, String purpose, MediaKind expected) {
        assertThat(MediaKind.of(media(mediaType, purpose))).isEqualTo(expected);
    }

    @Test
    void audioIsAVoiceNoteWhateverThePurposeSays() {
        // The upload rules refuse an audio PAGE_SCAN, so no row is like this; if one ever were, it is not a picture.
        assertThat(MediaKind.of(media("audio/webm", Media.PURPOSE_PAGE_SCAN))).isEqualTo(MediaKind.VOICE_NOTE);
    }

    @Test
    void theNamesAreTheOnesTheContractUses() {
        assertThat(MediaKind.values()).extracting(Enum::name).containsExactly("PHOTO", "PAGE_SCAN", "VOICE_NOTE");
    }
}

package com.memoirist.music;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The soundtrack format (D-35): {@code spotify:track|album|playlist:} and exactly 22 letters or digits. Plain unit
 * tests: no Spring, no database. {@code SoundtrackEndpointTest} proves the check is wired into the requests.
 */
class SoundtrackTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "spotify:track:1a2B3c4D5e6F7g8H9i0J1k",
                "spotify:album:4aawyAB9vmqN3uQ7FjRGTy",
                "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
                "spotify:track:0000000000000000000000",
                "spotify:track:ZZZZZZZZZZZZZZZZZZZZZZ"
            })
    void aTrackAlbumOrPlaylistWithA22CharacterIdIsValid(String uri) {
        assertThat(Soundtrack.isValid(uri)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                " ",
                "spotify:track:",
                // The id: one character short, one too long, a character outside letters and digits.
                "spotify:track:1a2B3c4D5e6F7g8H9i0J1",
                "spotify:track:1a2B3c4D5e6F7g8H9i0J1kX",
                "spotify:track:1a2B3c4D5e6F7g8H9i0J1-",
                "spotify:track:1a2B3c4D5e6F7g8H9i0J1é",
                "spotify:track:1a2B3c4D5e6F7g8H9i0 1k",
                // The kind: anything but the three.
                "spotify:artist:1a2B3c4D5e6F7g8H9i0J1k",
                "spotify:episode:1a2B3c4D5e6F7g8H9i0J1k",
                "spotify:show:1a2B3c4D5e6F7g8H9i0J1k",
                "spotify:user:someone:playlist:1a2B3c4D5e6F7g8H9i0J1k",
                "spotify:Track:1a2B3c4D5e6F7g8H9i0J1k",
                "SPOTIFY:track:1a2B3c4D5e6F7g8H9i0J1k",
                // Not a URI at all: a web link, a path, text around a good URI.
                "https://open.spotify.com/track/1a2B3c4D5e6F7g8H9i0J1k",
                "open.spotify.com/track/1a2B3c4D5e6F7g8H9i0J1k",
                "spotify/track/1a2B3c4D5e6F7g8H9i0J1k",
                " spotify:track:1a2B3c4D5e6F7g8H9i0J1k",
                "spotify:track:1a2B3c4D5e6F7g8H9i0J1k ",
                "spotify:track:1a2B3c4D5e6F7g8H9i0J1k\n",
                "spotify:track:1a2B3c4D5e6F7g8H9i0J1k\nspotify:track:1a2B3c4D5e6F7g8H9i0J1k",
                "x spotify:track:1a2B3c4D5e6F7g8H9i0J1k",
                "spotify:track:1a2B3c4D5e6F7g8H9i0J1k?si=abc",
                "javascript:alert(1)"
            })
    void anythingElseIsNotValid(String uri) {
        assertThat(Soundtrack.isValid(uri)).isFalse();
    }

    /** The constraint on a request field: null means no soundtrack and passes; everything else is the rule above. */
    @Test
    void theConstraintLetsNullThroughButNotTheEmptyString() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(violationsOf(validator, null)).isEmpty();
        assertThat(violationsOf(validator, "spotify:track:1a2B3c4D5e6F7g8H9i0J1k"))
                .isEmpty();
        assertThat(violationsOf(validator, "")).hasSize(1);
        assertThat(violationsOf(validator, "nonsense")).hasSize(1);
    }

    private static Set<?> violationsOf(Validator validator, String value) {
        return validator.validate(new Holder(value));
    }

    private record Holder(@SpotifyUri String soundtrack) {}
}

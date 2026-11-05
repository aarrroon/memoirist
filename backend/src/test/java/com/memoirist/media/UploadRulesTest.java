package com.memoirist.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memoirist.common.UnsupportedFileTypeException;
import com.memoirist.common.ValidationFailedException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The upload rules on their own (D-17, D-20), with no Spring and no database. The endpoint tests prove that the answers
 * reach the client; these prove every edge of the rules cheaply.
 */
class UploadRulesTest {

    // The allowed types

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp", "audio/webm", "audio/mp4", "audio/mpeg"})
    void everyTypeOnTheD17ListIsAllowedAndComesBackAsItself(String type) {
        assertThat(UploadRules.allowedType(type)).isEqualTo(type);
    }

    @Test
    void parametersAreDroppedSoABrowsersVoiceRecordingIsAccepted() {
        // Chrome's MediaRecorder labels its output like this. The stored media_type is the plain type.
        assertThat(UploadRules.allowedType("audio/webm;codecs=opus")).isEqualTo("audio/webm");
        assertThat(UploadRules.allowedType("audio/webm; codecs=opus")).isEqualTo("audio/webm");
        assertThat(UploadRules.allowedType("audio/mp4;codecs=mp4a.40.2")).isEqualTo("audio/mp4");
        assertThat(UploadRules.allowedType("image/jpeg; charset=binary")).isEqualTo("image/jpeg");
    }

    @Test
    void capitalLettersMakeNoDifference() {
        assertThat(UploadRules.allowedType("IMAGE/JPEG")).isEqualTo("image/jpeg");
        assertThat(UploadRules.allowedType("Audio/WebM;Codecs=Opus")).isEqualTo("audio/webm");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "image/gif",
                "image/heic",
                "image/heif",
                "image/svg+xml",
                "image/jpg",
                "image/*",
                "*/*",
                "audio/ogg",
                "audio/x-m4a",
                "audio/mp3",
                "video/mp4",
                "video/webm",
                "application/pdf",
                "application/octet-stream",
                "text/html",
                "text/plain",
                "multipart/mixed",
            })
    void everyOtherTypeIsRefused(String type) {
        assertThatThrownBy(() -> UploadRules.allowedType(type)).isInstanceOf(UnsupportedFileTypeException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   \t", "jpeg", "image", "image/", "/jpeg", "image/jpeg, image/png", "image jpeg"})
    void aMissingOrBrokenDeclarationIsRefusedNotCrashed(String declared) {
        assertThatThrownBy(() -> UploadRules.allowedType(declared)).isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void theRefusalNamesTheAllowedTypesAndNeverRepeatsTheDeclaredOne() {
        assertThatThrownBy(() -> UploadRules.allowedType("application/x-evil-<script>"))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .message()
                .contains("JPEG", "PNG", "WebP", "WebM", "MP4", "MP3")
                .doesNotContain("evil", "script");
    }

    // Extensions

    @Test
    void eachAllowedTypeHasTheExtensionTheKeyIsBuiltFrom() {
        assertThat(List.of("image/jpeg", "image/png", "image/webp", "audio/webm", "audio/mp4", "audio/mpeg"))
                .extracting(UploadRules::extensionFor)
                .containsExactly("jpg", "png", "webp", "webm", "m4a", "mp3");
    }

    @Test
    void anExtensionIsNeverGivenForATypeThatWasNotChecked() {
        assertThatThrownBy(() -> UploadRules.extensionFor("text/html")).isInstanceOf(IllegalArgumentException.class);
    }

    // Images, and diary pages

    @Test
    void onlyTheThreeImageTypesAreImages() {
        assertThat(List.of("image/jpeg", "image/png", "image/webp")).allMatch(UploadRules::isImage);
        assertThat(List.of("audio/webm", "audio/mp4", "audio/mpeg")).noneMatch(UploadRules::isImage);
    }

    @Test
    void aDiaryPageMustBeAnImage() {
        UploadRules.requireImageForPageScan("PAGE_SCAN", "image/png");
        UploadRules.requireImageForPageScan("PHOTO", "image/png");
        // The purpose only constrains a PAGE_SCAN: a voice note is a fine PHOTO as far as this rule goes, because the
        // type list, not the purpose, decides what a PHOTO may be (D-17).
        UploadRules.requireImageForPageScan("PHOTO", "audio/webm");

        assertThatThrownBy(() -> UploadRules.requireImageForPageScan("PAGE_SCAN", "audio/webm"))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    // Purposes

    @Test
    void noPurposeMeansPhoto() {
        assertThat(UploadRules.purpose(null)).isEqualTo("PHOTO");
    }

    @Test
    void thePurposesAreExactlyPhotoAndPageScan() {
        assertThat(UploadRules.purpose("PHOTO")).isEqualTo("PHOTO");
        assertThat(UploadRules.purpose("PAGE_SCAN")).isEqualTo("PAGE_SCAN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "photo", "Page_Scan", "PAGE-SCAN", "VOICE_NOTE", "PHOTO ", "null"})
    void anythingElseIsAFieldErrorOnPurpose(String purpose) {
        assertThatThrownBy(() -> UploadRules.purpose(purpose))
                .isInstanceOfSatisfying(
                        ValidationFailedException.class,
                        failure -> assertThat(failure.getFieldErrors())
                                .containsOnlyKeys("purpose")
                                .containsEntry("purpose", "must be PHOTO or PAGE_SCAN"));
    }

    // The limits

    @Test
    void theLimitsAreD17s() {
        assertThat(UploadRules.MAX_FILE_BYTES).isEqualTo(26_214_400L);
        assertThat(UploadRules.MAX_ITEMS_PER_ENTRY).isEqualTo(20);
    }
}

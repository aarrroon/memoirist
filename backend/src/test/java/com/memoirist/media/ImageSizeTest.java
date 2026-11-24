package com.memoirist.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link ImageSize#read}: the size from the header of real files (JPEG and PNG written by the JDK's encoders, WebP
 * written by Chromium), and nothing, without an exception, for everything it can't read (D-17, task 4.5).
 */
class ImageSizeTest {

    // JPEG

    @Test
    void aBaselineJpegGivesItsSize() {
        assertThat(read(jpeg(640, 480, false))).contains(new ImageSize(640, 480));
    }

    @Test
    void aProgressiveJpegGivesItsSizeFromItsOtherKindOfFrameHeader() {
        byte[] progressive = jpeg(300, 1200, true);
        // SOF2 (FF C2), not SOF0: the reader must know more than one start-of-frame marker.
        assertThat(indexOf(progressive, (byte) 0xFF, (byte) 0xC2)).isPositive();

        assertThat(read(progressive)).contains(new ImageSize(300, 1200));
    }

    @Test
    void aJpegWithALargeExifSegmentBeforeItsFrameHeaderIsSkippedNotMisread() {
        // A phone's photo carries EXIF (and a thumbnail) in APP1 segments in front of the frame header. 60 KB of
        // random bytes there, which contain FF C0 by chance more than once, must be skipped by its length.
        byte[] plain = jpeg(800, 600, false);
        byte[] payload = new byte[60_000];
        new Random(7).nextBytes(payload);
        byte[] app1 = new byte[4 + payload.length];
        app1[0] = (byte) 0xFF;
        app1[1] = (byte) 0xE1;
        app1[2] = (byte) ((payload.length + 2) >> 8);
        app1[3] = (byte) (payload.length + 2);
        System.arraycopy(payload, 0, app1, 4, payload.length);
        byte[] withExif = concat(Arrays.copyOf(plain, 2), app1, Arrays.copyOfRange(plain, 2, plain.length));

        assertThat(read(withExif)).contains(new ImageSize(800, 600));
    }

    @Test
    void aJpegCutOffBeforeItsFrameHeaderHasNoSize() {
        byte[] whole = jpeg(800, 600, false);
        int frame = indexOf(whole, (byte) 0xFF, (byte) 0xC0);

        assertThat(read(Arrays.copyOf(whole, frame + 3))).isEmpty();
    }

    // PNG

    @Test
    void aPngGivesItsSize() {
        assertThat(read(png(960, 640))).contains(new ImageSize(960, 640));
    }

    @Test
    void aPngWhoseFirstChunkIsNotTheHeaderHasNoSize() {
        byte[] bad = png(10, 10);
        bad[12] = 'X'; // "IHDR" -> "XHDR"

        assertThat(read(bad)).isEmpty();
    }

    // WebP: Chromium writes the extended format; the simple lossy and lossless files are its own bitstreams, rewrapped

    @ParameterizedTest(name = "{0}")
    @CsvSource({"extended-17x41.webp, 17, 41", "lossy-30x20.webp, 30, 20", "lossless-23x19.webp, 23, 19"})
    void aWebpGivesItsSizeInEachOfItsThreeFormats(String file, int width, int height) throws IOException {
        try (InputStream content = getClass().getResourceAsStream("/media/" + file)) {
            assertThat(ImageSize.read(content)).contains(new ImageSize(width, height));
        }
    }

    // What it can't read

    @Test
    void randomBytesHaveNoSize() {
        byte[] noise = new byte[4096];
        new Random(1).nextBytes(noise);

        assertThat(read(noise)).isEmpty();
    }

    @Test
    void anEmptyOrTinyFileHasNoSize() {
        assertThat(read(new byte[0])).isEmpty();
        assertThat(read(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
    }

    @Test
    void aHeaderThatClaimsZeroPixelsHasNoSize() {
        byte[] zero = png(10, 10);
        Arrays.fill(zero, 16, 20, (byte) 0); // the width

        assertThat(read(zero)).isEmpty();
    }

    @Test
    void aVoiceNoteHasNoSize() {
        // A WebM file starts with the EBML magic number, which is none of the three image signatures.
        byte[] webm = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0, 0, 0, 0, 0};

        assertThat(read(webm)).isEmpty();
    }

    // Helpers

    private static Optional<ImageSize> read(byte[] bytes) {
        return ImageSize.read(new ByteArrayInputStream(bytes));
    }

    private static byte[] png(int width, int height) {
        return TestImages.png(width, height);
    }

    private static byte[] jpeg(int width, int height, boolean progressive) {
        return TestImages.jpeg(width, height, progressive);
    }

    private static int indexOf(byte[] bytes, byte first, byte second) {
        for (int i = 0; i + 1 < bytes.length; i++) {
            if (bytes[i] == first && bytes[i + 1] == second) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}

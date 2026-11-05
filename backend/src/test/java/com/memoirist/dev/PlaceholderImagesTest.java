package com.memoirist.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The generated pictures: real PNGs of the promised size, different from each other, and the same every time. */
class PlaceholderImagesTest {

    private static BufferedImage decode(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    @Test
    void aSceneIsAPngOfTheSizeThatWasPromised() throws IOException {
        BufferedImage image = decode(PlaceholderImages.scene(0.55f));

        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(PlaceholderImages.WIDTH);
        assertThat(image.getHeight()).isEqualTo(PlaceholderImages.HEIGHT);
    }

    @Test
    void aDiaryPageIsAPngOfTheSameSize() throws IOException {
        BufferedImage image = decode(PlaceholderImages.diaryPage(7));

        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(PlaceholderImages.WIDTH);
        assertThat(image.getHeight()).isEqualTo(PlaceholderImages.HEIGHT);
    }

    @Test
    void theSameArgumentsGiveTheSameBytes() {
        // Storing a picture again under its key must replace it with an identical file.
        assertThat(PlaceholderImages.scene(0.2f)).isEqualTo(PlaceholderImages.scene(0.2f));
        assertThat(PlaceholderImages.diaryPage(3)).isEqualTo(PlaceholderImages.diaryPage(3));
    }

    @Test
    void differentArgumentsGiveDifferentPictures() {
        assertThat(PlaceholderImages.scene(0.1f)).isNotEqualTo(PlaceholderImages.scene(0.6f));
        assertThat(PlaceholderImages.diaryPage(1)).isNotEqualTo(PlaceholderImages.diaryPage(2));
    }

    @Test
    void aHueOutsideZeroToOneWrapsAroundTheColourWheel() {
        assertThat(PlaceholderImages.scene(1.25f)).isEqualTo(PlaceholderImages.scene(0.25f));
    }
}

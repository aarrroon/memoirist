package com.memoirist.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Real image files for tests (task 4.5): JPEG and PNG written by the JDK's own encoders at any size, and WebP files
 * written by Chromium, kept in {@code src/test/resources/media/} (the JDK has no WebP encoder).
 */
public final class TestImages {

    private TestImages() {}

    public static byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return out.toByteArray();
    }

    /** A baseline JPEG (frame header SOF0), or a progressive one (SOF2). */
    public static byte[] jpeg(int width, int height, boolean progressive) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (progressive) {
                params.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /**
     * One of the WebP files in {@code src/test/resources/media/}: {@code extended-17x41.webp} (VP8X, with alpha, as
     * Chromium writes it), {@code lossy-30x20.webp} (VP8) and {@code lossless-23x19.webp} (VP8L). The last two are
     * Chromium's own bitstreams taken out of its extended wrapper.
     */
    public static byte[] webp(String file) {
        try (InputStream content = TestImages.class.getResourceAsStream("/media/" + file)) {
            if (content == null) {
                throw new IllegalArgumentException("No test image " + file);
            }
            return content.readAllBytes();
        } catch (IOException missing) {
            throw new UncheckedIOException(missing);
        }
    }
}

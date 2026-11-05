package com.memoirist.dev;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * The pictures of the local sample content (task 3.16), drawn with {@code java.awt} so no image library is needed and
 * no real photo is ever stored in the repository.
 *
 * <p>Two shapes: a {@link #scene landscape} (a sky, a sun and two hills, in a colour chosen by a number) for ordinary
 * photos, and a {@link #diaryPage ruled page} for the diary-page photo. They draw no text, because a server without
 * fonts installed (a bare CI container) would fail on {@code drawString}. The output depends only on the arguments:
 * the same call gives the same bytes, so storing a picture again under the same key replaces it with an identical file.
 */
final class PlaceholderImages {

    static final int WIDTH = 960;
    static final int HEIGHT = 640;

    /** Every file made here is a PNG. */
    static final String CONTENT_TYPE = "image/png";

    private PlaceholderImages() {}

    /**
     * A landscape. {@code hue} is a number from 0 to 1 on the colour wheel (0.55 is a blue sky, 0.05 a sunset); two
     * different numbers give two clearly different pictures, so a carousel of them is easy to tell apart.
     */
    static byte[] scene(float hue) {
        return draw(g -> {
            g.setPaint(new GradientPaint(0, 0, hsb(hue, 0.35f, 1.0f), 0, HEIGHT, hsb(hue + 0.08f, 0.55f, 0.85f)));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            g.setColor(new Color(255, 255, 255, 215));
            g.fillOval(WIDTH - 280, 70, 130, 130);

            g.setColor(hsb(hue + 0.30f, 0.45f, 0.55f));
            g.fillPolygon(hill(0, 380, 250, 700));
            g.setColor(hsb(hue + 0.32f, 0.55f, 0.38f));
            g.fillPolygon(hill(300, 700, 330, WIDTH + 100));
        });
    }

    /**
     * A page from a paper diary: cream paper, blue ruled lines, a red margin and grey strokes standing in for
     * handwriting. {@code seed} changes the strokes.
     */
    static byte[] diaryPage(long seed) {
        return draw(g -> {
            g.setColor(new Color(250, 244, 226));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(170, 195, 225));
            for (int y = 90; y < HEIGHT; y += 44) {
                g.drawLine(0, y, WIDTH, y);
            }
            g.setColor(new Color(215, 120, 120));
            g.drawLine(110, 0, 110, HEIGHT);

            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(70, 70, 90));
            Random random = new Random(seed);
            for (int y = 84; y < HEIGHT - 40; y += 44) {
                int x = 135;
                int end = 135 + 300 + random.nextInt(480);
                while (x < end) {
                    int word = 30 + random.nextInt(90);
                    g.drawLine(x, y, Math.min(x + word, end), y - random.nextInt(8));
                    x += word + 22;
                }
            }
        });
    }

    private interface Painting {
        void paint(Graphics2D g);
    }

    private static byte[] draw(Painting painting) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            painting.paint(g);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, "png", bytes)) {
                throw new IllegalStateException("This JVM has no PNG writer.");
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not encode a placeholder image", failure);
        }
        return bytes.toByteArray();
    }

    /** A triangle with its peak at ({@code peakX}, {@code peakY}) that runs off the bottom of the picture. */
    private static Polygon hill(int left, int peakX, int peakY, int right) {
        return new Polygon(new int[] {left, peakX, right}, new int[] {HEIGHT, peakY, HEIGHT}, 3);
    }

    /** Hue wraps around the colour wheel, so callers can add to it freely. */
    private static Color hsb(float hue, float saturation, float brightness) {
        return Color.getHSBColor(hue - (float) Math.floor(hue), saturation, brightness);
    }
}

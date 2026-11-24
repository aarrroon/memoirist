package com.memoirist.media;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * A photo's width and height in pixels, read from the file's header without decoding the picture (D-17, task 4.5).
 *
 * <p><b>Why our own reader, not {@code ImageIO}.</b> The JDK has no WebP reader, and one of the three allowed image
 * types would always come back without a size. The three headers are small and documented, so reading them by hand is
 * about a hundred lines with no new dependency, and it reads only the bytes it needs: a PNG's first 24, a WebP's
 * first 30, and a JPEG's segment headers up to its frame header (skipping, not reading, the EXIF and thumbnail data in
 * front of it).
 *
 * <p><b>What it trusts.</b> The bytes, not the declared type: the format is chosen by the file's own signature. Any
 * file it can't read (a truncated header, an unknown format, a size of 0) gives {@link Optional#empty()}, never an
 * exception, because a photo without a size is still a photo: the screen gives it a 4:5 frame. The size is the stored
 * size, without EXIF rotation: the app's browser re-encodes every photo before upload, which applies it (D-17).
 *
 * @param width in pixels, at least 1
 * @param height in pixels, at least 1
 */
public record ImageSize(int width, int height) {

    public ImageSize {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("An image is at least 1 x 1 pixels");
        }
    }

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /**
     * Reads the size from the start of {@code content}. The stream is read, not closed: the caller owns it.
     *
     * @return the size, or empty when the header isn't a readable JPEG, PNG or WebP header
     */
    public static Optional<ImageSize> read(InputStream content) {
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(content));
            byte[] start = in.readNBytes(4);
            if (start.length < 4) {
                return Optional.empty();
            }
            if ((start[0] & 0xFF) == 0xFF && (start[1] & 0xFF) == 0xD8) {
                return jpeg(in, start);
            }
            if (Arrays.equals(start, 0, 4, PNG_SIGNATURE, 0, 4)) {
                return png(in);
            }
            if (start[0] == 'R' && start[1] == 'I' && start[2] == 'F' && start[3] == 'F') {
                return webp(in);
            }
            return Optional.empty();
        } catch (IOException | IllegalArgumentException unreadable) {
            // EOFException (a header cut short) is an IOException; IllegalArgumentException is a size of 0.
            return Optional.empty();
        }
    }

    /** PNG: the 8-byte signature, then the IHDR chunk (length, "IHDR", width and height as big-endian ints). */
    private static Optional<ImageSize> png(DataInputStream in) throws IOException {
        byte[] rest = in.readNBytes(4);
        if (!Arrays.equals(rest, 0, rest.length, PNG_SIGNATURE, 4, 8)) {
            return Optional.empty();
        }
        in.skipNBytes(4); // the chunk's length, always 13 for IHDR
        if (!fourCc(in).equals("IHDR")) {
            return Optional.empty();
        }
        return Optional.of(new ImageSize(in.readInt(), in.readInt()));
    }

    /**
     * JPEG: a run of segments, each {@code FF}, a marker byte and (for most markers) a two-byte length that counts
     * itself. The size is in the first start-of-frame segment ({@code C0} to {@code CF}, except {@code C4} DHT,
     * {@code C8} JPG and {@code CC} DAC): precision, then height, then width. Reaching the scan ({@code DA}) or the end
     * ({@code D9}) first means there is no frame header to read.
     */
    private static Optional<ImageSize> jpeg(DataInputStream in, byte[] start) throws IOException {
        // The first marker after SOI is already in start[2..3].
        int marker = (start[2] & 0xFF) == 0xFF ? start[3] & 0xFF : -1;
        if (marker < 0) {
            return Optional.empty();
        }
        while (true) {
            while (marker == 0xFF) {
                marker = in.readUnsignedByte(); // fill bytes: any number of FF before a marker
            }
            if (marker == 0xD9 || marker == 0xDA) {
                return Optional.empty();
            }
            boolean standalone = marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8);
            if (!standalone) {
                int length = in.readUnsignedShort();
                if (length < 2) {
                    return Optional.empty();
                }
                if (isStartOfFrame(marker)) {
                    in.skipNBytes(1); // sample precision
                    int height = in.readUnsignedShort();
                    int width = in.readUnsignedShort();
                    return Optional.of(new ImageSize(width, height));
                }
                in.skipNBytes(length - 2L);
            }
            if (in.readUnsignedByte() != 0xFF) {
                return Optional.empty();
            }
            marker = in.readUnsignedByte();
        }
    }

    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    /**
     * WebP: "RIFF", the file size, "WEBP", then the first chunk, which is one of three kinds:
     * <ul>
     *   <li>{@code VP8X} (extended): flags, three reserved bytes, then the canvas width and height minus one, each a
     *       24-bit little-endian number.
     *   <li>{@code VP8 } (lossy): a 3-byte frame tag, the start code {@code 9D 01 2A}, then width and height as 16-bit
     *       little-endian numbers whose top two bits are a scale, not part of the size.
     *   <li>{@code VP8L} (lossless): the signature byte {@code 2F}, then 14 bits of width minus one and 14 bits of height
     *       minus one, packed little-endian.
     * </ul>
     */
    private static Optional<ImageSize> webp(DataInputStream in) throws IOException {
        in.skipNBytes(4); // the RIFF size
        if (!fourCc(in).equals("WEBP")) {
            return Optional.empty();
        }
        String chunk = fourCc(in);
        in.skipNBytes(4); // the chunk size
        switch (chunk) {
            case "VP8X" -> {
                in.skipNBytes(4); // flags and reserved
                return Optional.of(new ImageSize(uint24le(in) + 1, uint24le(in) + 1));
            }
            case "VP8 " -> {
                in.skipNBytes(3); // frame tag
                if (in.readUnsignedByte() != 0x9D || in.readUnsignedByte() != 0x01 || in.readUnsignedByte() != 0x2A) {
                    return Optional.empty();
                }
                return Optional.of(new ImageSize(uint16le(in) & 0x3FFF, uint16le(in) & 0x3FFF));
            }
            case "VP8L" -> {
                if (in.readUnsignedByte() != 0x2F) {
                    return Optional.empty();
                }
                long bits = in.readUnsignedByte()
                        | (long) in.readUnsignedByte() << 8
                        | (long) in.readUnsignedByte() << 16
                        | (long) in.readUnsignedByte() << 24;
                int width = (int) (bits & 0x3FFF) + 1;
                int height = (int) ((bits >> 14) & 0x3FFF) + 1;
                return Optional.of(new ImageSize(width, height));
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    private static String fourCc(DataInputStream in) throws IOException {
        byte[] code = new byte[4];
        in.readFully(code);
        return new String(code, StandardCharsets.US_ASCII);
    }

    private static int uint16le(DataInputStream in) throws IOException {
        return in.readUnsignedByte() | in.readUnsignedByte() << 8;
    }

    private static int uint24le(DataInputStream in) throws IOException {
        return in.readUnsignedByte() | in.readUnsignedByte() << 8 | in.readUnsignedByte() << 16;
    }
}

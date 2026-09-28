package com.company.bds.media;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Synthetic test images: left half red, right half blue, optionally with an EXIF block (orientation + GPS). */
public final class MediaTestImages {
    private MediaTestImages() {}

    public static byte[] jpeg(int width, int height) { return encode(twoColour(width, height, false), "jpg"); }

    public static byte[] png(int width, int height) { return encode(twoColour(width, height, true), "png"); }

    /** JPEG whose APP1 EXIF says {@code orientation} and carries a GPS IFD (latitude ref "N", 21°). */
    public static byte[] jpegWithExif(int width, int height, int orientation) {
        byte[] plain = jpeg(width, height);
        byte[] app1 = exifApp1(orientation);
        byte[] out = new byte[plain.length + app1.length];
        out[0] = plain[0];
        out[1] = plain[1];
        System.arraycopy(app1, 0, out, 2, app1.length);
        System.arraycopy(plain, 2, out, 2 + app1.length, plain.length - 2);
        return out;
    }

    static BufferedImage twoColour(int width, int height, boolean alpha) {
        BufferedImage image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) throw new IllegalStateException("no writer " + format);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Big-endian TIFF: IFD0 {Orientation, GPSInfo pointer}, GPS IFD {GPSLatitudeRef "N", GPSLatitude 21/1 0/1 0/1}. */
    private static byte[] exifApp1(int orientation) {
        ByteBuffer tiff = ByteBuffer.allocate(128);
        tiff.put("MM".getBytes(StandardCharsets.US_ASCII)).putShort((short) 42).putInt(8);
        // IFD0 at 8: 2 entries
        tiff.putShort((short) 2);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(38);
        tiff.putInt(0);                                   // next IFD: none (ends at 38)
        // GPS IFD at 38: 2 entries
        tiff.putShort((short) 2);
        tiff.putShort((short) 1).putShort((short) 2).putInt(2).put((byte) 'N').put((byte) 0).putShort((short) 0);
        tiff.putShort((short) 2).putShort((short) 5).putInt(3).putInt(68);
        tiff.putInt(0);                                   // ends at 68
        tiff.putInt(21).putInt(1).putInt(0).putInt(1).putInt(0).putInt(1);   // rationals at 68..92
        int tiffLength = tiff.position();
        byte[] header = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int segmentLength = 2 + header.length + tiffLength;
        ByteBuffer app1 = ByteBuffer.allocate(2 + segmentLength);
        app1.put((byte) 0xFF).put((byte) 0xE1).putShort((short) segmentLength).put(header).put(tiff.array(), 0, tiffLength);
        return app1.array();
    }
}

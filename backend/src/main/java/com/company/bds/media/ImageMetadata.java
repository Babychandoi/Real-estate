package com.company.bds.media;

/**
 * Minimal, allocation-free readers for the metadata the pipeline cares about: the EXIF orientation of a JPEG (APP1
 * "Exif" TIFF IFD0 tag 0x0112) and whether an encoded file still carries metadata blocks (JPEG APP1/APP13, WebP
 * EXIF/XMP chunks, PNG eXIf/tEXt/iTXt/zTXt chunks). Malformed input never throws; it yields orientation 1.
 */
final class ImageMetadata {
    private ImageMetadata() {}

    /** EXIF orientation 1–8 of a JPEG, 1 when absent, invalid or not a JPEG. */
    static int jpegOrientation(byte[] jpeg) {
        if (jpeg.length < 4 || u8(jpeg, 0) != 0xFF || u8(jpeg, 1) != 0xD8) return 1;
        int pos = 2;
        while (pos + 4 <= jpeg.length) {
            if (u8(jpeg, pos) != 0xFF) return 1;
            int marker = u8(jpeg, pos + 1);
            if (marker == 0xFF) { pos++; continue; }                    // fill byte
            if (marker == 0xDA || marker == 0xD9) return 1;              // start of scan / end of image
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { pos += 2; continue; }
            int length = u16(jpeg, pos + 2, false);
            if (length < 2 || pos + 2 + length > jpeg.length) return 1;
            int start = pos + 4;
            if (marker == 0xE1 && length >= 16 && ascii(jpeg, start, "Exif\0\0")) {
                int orientation = tiffOrientation(jpeg, start + 6, pos + 2 + length);
                if (orientation != 0) return orientation;
            }
            pos += 2 + length;
        }
        return 1;
    }

    private static int tiffOrientation(byte[] b, int tiff, int end) {
        if (tiff + 8 > end) return 0;
        boolean little;
        if (b[tiff] == 'I' && b[tiff + 1] == 'I') little = true;
        else if (b[tiff] == 'M' && b[tiff + 1] == 'M') little = false;
        else return 0;
        if (u16(b, tiff + 2, little) != 42) return 0;
        long ifd = u32(b, tiff + 4, little);
        if (ifd < 8 || tiff + ifd + 2 > end) return 0;
        int ifdPos = (int) (tiff + ifd);
        int entries = u16(b, ifdPos, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifdPos + 2 + i * 12;
            if (entry + 12 > end) return 0;
            if (u16(b, entry, little) == 0x0112 && u16(b, entry + 2, little) == 3) {
                int value = u16(b, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 0;
            }
        }
        return 0;
    }

    /** True when the encoded image still contains an EXIF/XMP/IPTC/text metadata block. */
    static boolean containsMetadata(byte[] bytes, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> jpegHasMetadata(bytes);
            case "image/webp" -> riffHasChunk(bytes, "EXIF") || riffHasChunk(bytes, "XMP ");
            case "image/png" -> pngHasChunk(bytes, "eXIf") || pngHasChunk(bytes, "tEXt") || pngHasChunk(bytes, "iTXt")
                    || pngHasChunk(bytes, "zTXt");
            default -> true;
        };
    }

    private static boolean jpegHasMetadata(byte[] jpeg) {
        int pos = 2;
        while (pos + 4 <= jpeg.length) {
            if (u8(jpeg, pos) != 0xFF) return false;
            int marker = u8(jpeg, pos + 1);
            if (marker == 0xFF) { pos++; continue; }
            if (marker == 0xDA || marker == 0xD9) return false;
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { pos += 2; continue; }
            if (marker == 0xE1 || marker == 0xED || marker == 0xFE) return true;   // EXIF/XMP, IPTC, comment
            int length = u16(jpeg, pos + 2, false);
            if (length < 2) return false;
            pos += 2 + length;
        }
        return false;
    }

    private static boolean riffHasChunk(byte[] b, String fourcc) {
        if (b.length < 12 || !ascii(b, 0, "RIFF") || !ascii(b, 8, "WEBP")) return false;
        int pos = 12;
        while (pos + 8 <= b.length) {
            if (ascii(b, pos, fourcc)) return true;
            long size = u32(b, pos + 4, true);
            pos += 8 + (int) size + (int) (size & 1);
            if (size < 0 || pos < 0) return false;
        }
        return false;
    }

    private static boolean pngHasChunk(byte[] b, String type) {
        int pos = 8;
        while (pos + 12 <= b.length) {
            long length = u32(b, pos, false);
            if (ascii(b, pos + 4, type)) return true;
            if (ascii(b, pos + 4, "IEND")) return false;
            pos += 12 + (int) length;
            if (pos < 0) return false;
        }
        return false;
    }

    private static int u8(byte[] b, int i) { return b[i] & 0xFF; }

    private static int u16(byte[] b, int i, boolean little) {
        if (i + 2 > b.length) return -1;
        return little ? (u8(b, i) | u8(b, i + 1) << 8) : (u8(b, i) << 8 | u8(b, i + 1));
    }

    private static long u32(byte[] b, int i, boolean little) {
        if (i + 4 > b.length) return -1;
        long v = little
                ? (u8(b, i) | u8(b, i + 1) << 8 | u8(b, i + 2) << 16 | (long) u8(b, i + 3) << 24)
                : ((long) u8(b, i) << 24 | u8(b, i + 1) << 16 | u8(b, i + 2) << 8 | u8(b, i + 3));
        return v & 0xFFFFFFFFL;
    }

    private static boolean ascii(byte[] b, int offset, String expected) {
        if (offset + expected.length() > b.length) return false;
        for (int i = 0; i < expected.length(); i++) if (b[offset + i] != (byte) expected.charAt(i)) return false;
        return true;
    }
}

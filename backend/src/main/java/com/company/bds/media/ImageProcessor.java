package com.company.bds.media;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Image pipeline core (contract §10, F14.2), free of Spring/storage so it is unit-testable.
 * <ul>
 *   <li><b>Memory bound:</b> the header is read first (no pixels); sources over {@link #MAX_PIXELS} are refused; decoding
 *   uses integer source subsampling so the decoded raster is below 2 × {@link #MASTER_MAX_EDGE} on its long edge; one
 *   image is processed at a time per JVM ({@link #PERMITS}).</li>
 *   <li><b>Orientation:</b> the JPEG EXIF orientation (1–8) is applied to the pixels.</li>
 *   <li><b>Privacy:</b> every output is re-encoded from pixels only, so EXIF (GPS, camera serial), XMP, IPTC and comments
 *   never survive.</li>
 *   <li><b>Outputs:</b> a master in the source format capped at {@link #MASTER_MAX_EDGE}; WebP variants at
 *   {@link #VARIANT_WIDTHS} (never upscaled, deduplicated); dominant colour; a tiny blurred WebP LQIP data URI.</li>
 * </ul>
 */
public final class ImageProcessor {
    public static final int MASTER_MAX_EDGE = 2048;
    public static final List<Integer> VARIANT_WIDTHS = List.of(320, 640, 960, 1600);
    public static final long MAX_PIXELS = 50_000_000L;
    static final int LQIP_WIDTH = 16;
    static final int LQIP_MAX_CHARS = 2048;
    private static final float MASTER_QUALITY = 0.85f;
    private static final float VARIANT_QUALITY = 0.80f;
    private static final Semaphore PERMITS = new Semaphore(1, true);

    private ImageProcessor() {}

    /** Header-only check used at upload time: format decodable, dimensions within limits. */
    public static Dimensions probe(byte[] bytes, String contentType) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = readerFor(input, contentType);
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                checkDimensions(width, height);
                return new Dimensions(width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof UnsupportedImageException unsupported) throw unsupported;
            throw new UnsupportedImageException("Ảnh bị lỗi hoặc không đọc được.", ex);
        }
    }

    public static Result process(byte[] bytes, String contentType) {
        PERMITS.acquireUninterruptibly();
        try {
            BufferedImage decoded = decodeBounded(bytes, contentType);
            BufferedImage master = orient(fit(decoded, MASTER_MAX_EDGE), orientationOf(bytes, contentType));
            decoded = null; // release the (possibly larger) decoded raster before encoding
            boolean alpha = master.getColorModel().hasAlpha();
            byte[] masterBytes = encode(master, contentType, MASTER_QUALITY);
            List<Variant> variants = new ArrayList<>();
            for (int width : variantWidths(master.getWidth())) {
                BufferedImage scaled = scaleToWidth(master, width);
                variants.add(new Variant(width, scaled.getHeight(), encode(scaled, "image/webp", VARIANT_QUALITY)));
            }
            return new Result(masterBytes, contentType, master.getWidth(), master.getHeight(), variants,
                    dominantColor(master), lqip(master, alpha));
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof UnsupportedImageException unsupported) throw unsupported;
            throw new UnsupportedImageException("Không xử lý được ảnh.", ex);
        } finally {
            PERMITS.release();
        }
    }

    /** Widths {@code min(target, original)} for every target, deduplicated, ascending. */
    static List<Integer> variantWidths(int originalWidth) {
        Set<Integer> widths = new LinkedHashSet<>();
        for (int target : VARIANT_WIDTHS) widths.add(Math.min(target, originalWidth));
        return List.copyOf(widths);
    }

    static int orientationOf(byte[] bytes, String contentType) {
        return "image/jpeg".equals(contentType) ? ImageMetadata.jpegOrientation(bytes) : 1;
    }

    private static void checkDimensions(int width, int height) {
        if (width <= 0 || height <= 0) throw new UnsupportedImageException("Ảnh không có kích thước hợp lệ.");
        if ((long) width * height > MAX_PIXELS) {
            throw new UnsupportedImageException("Ảnh quá lớn (tối đa 50 megapixel).");
        }
    }

    private static ImageReader readerFor(ImageInputStream input, String contentType) {
        if (input == null) throw new UnsupportedImageException("Ảnh bị lỗi hoặc không đọc được.");
        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(contentType);
        if (!readers.hasNext()) throw new UnsupportedImageException("Định dạng ảnh chưa được hỗ trợ: " + contentType);
        return readers.next();
    }

    private static BufferedImage decodeBounded(byte[] bytes, String contentType) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = readerFor(input, contentType);
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                checkDimensions(width, height);
                int subsampling = Math.max(1, Math.max(width, height) / MASTER_MAX_EDGE);
                ImageReadParam param = reader.getDefaultReadParam();
                if (subsampling > 1) param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                BufferedImage image = reader.read(0, param);
                if (image == null) throw new UnsupportedImageException("Ảnh bị lỗi hoặc không đọc được.");
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    /** Scales down (never up) so the long edge is at most {@code maxEdge}; converts to a plain RGB/ARGB raster. */
    static BufferedImage fit(BufferedImage source, int maxEdge) {
        int longEdge = Math.max(source.getWidth(), source.getHeight());
        if (longEdge <= maxEdge) return normalized(source);
        double ratio = (double) maxEdge / longEdge;
        return scale(source, Math.max(1, (int) Math.round(source.getWidth() * ratio)),
                Math.max(1, (int) Math.round(source.getHeight() * ratio)));
    }

    static BufferedImage scaleToWidth(BufferedImage source, int width) {
        if (width >= source.getWidth()) return source;
        int height = Math.max(1, (int) Math.round((double) source.getHeight() * width / source.getWidth()));
        return scale(source, width, height);
    }

    /** Progressive halving + bilinear: good quality without the cost of area averaging on big rasters. */
    private static BufferedImage scale(BufferedImage source, int width, int height) {
        BufferedImage current = normalized(source);
        while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return current.getWidth() == width && current.getHeight() == height ? current : draw(current, width, height);
    }

    private static BufferedImage normalized(BufferedImage source) {
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (source.getType() == type) return source;
        return draw(source, source.getWidth(), source.getHeight());
    }

    private static BufferedImage draw(BufferedImage source, int width, int height) {
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** Applies EXIF orientation 1–8 so the pixels are upright. */
    static BufferedImage orient(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) return image;
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // mirror horizontal
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // rotate 180
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // mirror vertical
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // transpose
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // rotate 90 clockwise
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // transverse
            default -> new AffineTransform(0, -1, 1, 0, 0, w);  // 8: rotate 90 counter-clockwise
        };
        BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h, image.getType());
        Graphics2D g = target.createGraphics();
        try {
            g.drawImage(image, t, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    static byte[] encode(BufferedImage image, String contentType, float quality) throws IOException {
        Iterator<javax.imageio.ImageWriter> writers = ImageIO.getImageWritersByMIMEType(contentType);
        if (!writers.hasNext()) throw new UnsupportedImageException("Không có bộ mã hóa cho " + contentType);
        ImageWriter writer = writers.next();
        BufferedImage output = image;
        if ("image/jpeg".equals(contentType) && image.getColorModel().hasAlpha()) {
            output = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = output.createGraphics();
            try {
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, image.getWidth(), image.getHeight());
                g.drawImage(image, 0, 0, null);
            } finally {
                g.dispose();
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (!"image/png".equals(contentType) && param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = param.getCompressionTypes();
                if (types != null && types.length > 0) {
                    String lossy = null;
                    for (String type : types) if (type.toLowerCase(Locale.ROOT).contains("lossy")) lossy = type;
                    param.setCompressionType(lossy != null ? lossy : types[0]);
                }
                param.setCompressionQuality(quality);
            }
            // Pixels only: no stream or image metadata is passed, so nothing from the source survives.
            writer.write(null, new IIOImage(output, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    static String dominantColor(BufferedImage image) {
        BufferedImage tiny = scale(image, Math.min(8, image.getWidth()), Math.min(8, image.getHeight()));
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < tiny.getHeight(); y++) {
            for (int x = 0; x < tiny.getWidth(); x++) {
                int argb = tiny.getRGB(x, y);
                int alpha = argb >>> 24;
                if (tiny.getColorModel().hasAlpha() && alpha < 16) continue;
                r += (argb >> 16) & 0xFF; g += (argb >> 8) & 0xFF; b += argb & 0xFF; n++;
            }
        }
        if (n == 0) return "#ffffff";
        return String.format(Locale.ROOT, "#%02x%02x%02x", r / n, g / n, b / n);
    }

    private static String lqip(BufferedImage image, boolean alpha) throws IOException {
        BufferedImage tiny = scaleToWidth(image, Math.min(LQIP_WIDTH, image.getWidth()));
        String uri = "data:image/webp;base64," + Base64.getEncoder().encodeToString(encode(tiny, "image/webp", 0.3f));
        return uri.length() <= LQIP_MAX_CHARS ? uri : null;
    }

    public record Dimensions(int width, int height) {}

    public record Variant(int width, int height, byte[] bytes) {}

    public record Result(byte[] master, String contentType, int width, int height, List<Variant> variants,
                         String dominantColor, String lqip) {}

    /** The input cannot be decoded or is outside the limits: retrying will not help. */
    public static final class UnsupportedImageException extends IllegalArgumentException {
        public UnsupportedImageException(String message) { super(message); }
        public UnsupportedImageException(String message, Throwable cause) { super(message, cause); }
    }
}

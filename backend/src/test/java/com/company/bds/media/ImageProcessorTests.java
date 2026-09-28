package com.company.bds.media;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTests {

    @Test
    void readsExifOrientationAndDetectsMetadata() {
        byte[] exif = MediaTestImages.jpegWithExif(60, 40, 6);
        assertThat(ImageMetadata.jpegOrientation(exif)).isEqualTo(6);
        assertThat(ImageMetadata.containsMetadata(exif, "image/jpeg")).isTrue();
        byte[] plain = MediaTestImages.jpeg(60, 40);
        assertThat(ImageMetadata.jpegOrientation(plain)).isEqualTo(1);
        assertThat(ImageMetadata.jpegOrientation(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 1}))
                .isEqualTo(1);
    }

    @Test
    void orientationSixIsRotatedClockwiseAndEveryOutputIsMetadataFree() throws IOException {
        ImageProcessor.Result result = ImageProcessor.process(MediaTestImages.jpegWithExif(600, 400, 6), "image/jpeg");

        assertThat(result.width()).isEqualTo(400);
        assertThat(result.height()).isEqualTo(600);
        BufferedImage master = decode(result.master());
        assertThat(master.getWidth()).isEqualTo(400);
        // Rotating 90° clockwise moves the left (red) half to the top.
        assertThat(isRed(master.getRGB(200, 100))).isTrue();
        assertThat(isBlue(master.getRGB(200, 500))).isTrue();
        assertThat(ImageMetadata.containsMetadata(result.master(), "image/jpeg")).isFalse();
        assertThat(ImageMetadata.jpegOrientation(result.master())).isEqualTo(1);
        for (ImageProcessor.Variant variant : result.variants()) {
            assertThat(ImageMetadata.containsMetadata(variant.bytes(), "image/webp")).isFalse();
            assertThat(new String(variant.bytes(), 0, 4)).isEqualTo("RIFF");
        }
    }

    @Test
    void orientationsThreeAndEightAreApplied() throws IOException {
        BufferedImage rotated180 = decode(ImageProcessor.process(MediaTestImages.jpegWithExif(600, 400, 3), "image/jpeg").master());
        assertThat(rotated180.getWidth()).isEqualTo(600);
        assertThat(isBlue(rotated180.getRGB(100, 200))).isTrue();    // right half moved to the left
        BufferedImage ccw = decode(ImageProcessor.process(MediaTestImages.jpegWithExif(600, 400, 8), "image/jpeg").master());
        assertThat(ccw.getWidth()).isEqualTo(400);
        assertThat(isBlue(ccw.getRGB(200, 100))).isTrue();           // counter-clockwise: the right half is on top
        assertThat(isRed(ccw.getRGB(200, 500))).isTrue();
    }

    @Test
    void variantsFollowTheContractWidthsWithoutUpscaling() throws IOException {
        ImageProcessor.Result large = ImageProcessor.process(MediaTestImages.jpeg(3000, 2000), "image/jpeg");
        assertThat(large.width()).isEqualTo(2048);                   // master capped
        assertThat(large.variants()).extracting(ImageProcessor.Variant::width).containsExactly(320, 640, 960, 1600);
        ImageProcessor.Variant w640 = large.variants().get(1);
        assertThat(w640.height()).isEqualTo(427);
        BufferedImage decoded = decode(w640.bytes());
        assertThat(decoded.getWidth()).isEqualTo(640);
        assertThat(decoded.getHeight()).isEqualTo(427);

        ImageProcessor.Result small = ImageProcessor.process(MediaTestImages.jpeg(700, 500), "image/jpeg");
        assertThat(small.variants()).extracting(ImageProcessor.Variant::width).containsExactly(320, 640, 700);
        assertThat(ImageProcessor.variantWidths(200)).isEqualTo(List.of(200));
    }

    @Test
    void placeholderHasDominantColourAndATinyLqip() throws IOException {
        ImageProcessor.Result result = ImageProcessor.process(MediaTestImages.png(400, 300), "image/png");
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.dominantColor()).matches("#[0-9a-f]{6}");
        // Half red, half blue → purple-ish average.
        int rgb = Integer.parseInt(result.dominantColor().substring(1), 16);
        assertThat((rgb >> 16) & 0xFF).isBetween(100, 160);
        assertThat(rgb & 0xFF).isBetween(100, 160);
        assertThat(result.lqip()).startsWith("data:image/webp;base64,").hasSizeLessThanOrEqualTo(ImageProcessor.LQIP_MAX_CHARS);
        BufferedImage lqip = decode(Base64.getDecoder().decode(result.lqip().substring("data:image/webp;base64,".length())));
        assertThat(lqip.getWidth()).isEqualTo(ImageProcessor.LQIP_WIDTH);
        assertThat(ImageMetadata.containsMetadata(result.master(), "image/png")).isFalse();
    }

    @Test
    void largeSourcesAreDecodedSubsampledAndOversizedOnesRefusedFromTheHeader() {
        // 6000×4000 = 24 MP: decoded with subsampling 2 (3000×2000), master still 2048 wide.
        ImageProcessor.Result result = ImageProcessor.process(MediaTestImages.jpeg(6000, 4000), "image/jpeg");
        assertThat(result.width()).isEqualTo(2048);
        assertThat(result.height()).isEqualTo(1365);
        assertThat(ImageProcessor.probe(MediaTestImages.jpeg(640, 480), "image/jpeg"))
                .isEqualTo(new ImageProcessor.Dimensions(640, 480));
        // A PNG header claiming 10000×10000 (a decompression bomb) is refused before any pixel is decoded.
        assertThatThrownBy(() -> ImageProcessor.probe(pngHeader(10_000, 10_000), "image/png"))
                .isInstanceOf(ImageProcessor.UnsupportedImageException.class).hasMessageContaining("50 megapixel");
        assertThatThrownBy(() -> ImageProcessor.process("not an image".getBytes(), "image/jpeg"))
                .isInstanceOf(ImageProcessor.UnsupportedImageException.class);
    }

    private static byte[] pngHeader(int width, int height) {
        byte[] png = MediaTestImages.png(2, 2);
        // IHDR width/height live at offsets 16..23; the CRC is not checked when only the header is read.
        java.nio.ByteBuffer.wrap(png, 16, 8).putInt(width).putInt(height);
        return png;
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(image).as("decodable").isNotNull();
        return image;
    }

    private static boolean isRed(int rgb) { return ((rgb >> 16) & 0xFF) > 200 && (rgb & 0xFF) < 60; }

    private static boolean isBlue(int rgb) { return (rgb & 0xFF) > 200 && ((rgb >> 16) & 0xFF) < 60; }
}

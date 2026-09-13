package com.company.bds.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaStorageServiceTests {
    @Test void detectsSupportedImageMagicBytes() {
        assertEquals("image/jpeg", MediaStorageService.detectContentType(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}));
        assertEquals("image/png", MediaStorageService.detectContentType(new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}));
        assertEquals("image/webp", MediaStorageService.detectContentType("RIFF0000WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertEquals("image/avif", MediaStorageService.detectContentType("0000ftypavif".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    }

    @Test void rejectsNonImagePayload() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaStorageService.detectContentType("not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}

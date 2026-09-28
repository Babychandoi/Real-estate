package com.company.bds.engagement.api;

import com.company.bds.shared.error.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset cursor {@code (instant, id)} for the engagement lists. Not signed: every list is scoped to the
 * signed-in user server side, so a forged cursor can only skip within the caller's own rows.
 */
record PageCursor(Instant at, UUID id) {

    String encode() {
        String raw = at.getEpochSecond() + "." + at.getNano() + "." + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
    }

    static PageCursor decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.length() > 120) throw new IllegalArgumentException();
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.US_ASCII);
            String[] parts = raw.split("\\.", 3);
            return new PageCursor(Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1])),
                    UUID.fromString(parts[2]));
        } catch (RuntimeException ex) {
            throw ApiException.badRequest("CURSOR_INVALID", "Vị trí trang không hợp lệ; hãy tải lại danh sách.");
        }
    }

    static int size(int requested, int max) {
        if (requested < 1 || requested > max) {
            throw ApiException.badRequest("INVALID_SIZE", "Số mục mỗi trang phải từ 1 đến " + max + ".");
        }
        return requested;
    }
}

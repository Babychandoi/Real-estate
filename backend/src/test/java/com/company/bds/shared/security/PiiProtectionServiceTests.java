package com.company.bds.shared.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PiiProtectionServiceTests {
    private final PiiProtectionService service = new PiiProtectionService("", "", "test");

    @Test
    void protectsAndRevealsNormalizedPhoneWithoutStoringPlainText() {
        PiiProtectionService.ProtectedValue protectedValue = service.protect("091 234 5678");

        assertNotEquals("0912345678", protectedValue.encrypted());
        assertEquals("0912345678", service.reveal(protectedValue.encrypted()));
        assertEquals(service.blindIndex("0912345678"), protectedValue.blindIndex());
    }

    @Test
    void sealsArbitraryTextBoundToItsPurpose() {
        String text = "Liên kết: https://nhadatchuan.online/reset-password?token=abc  (giữ nguyên khoảng trắng)";
        String sealed = service.seal(text, "mail-outbox");

        assertEquals(text, service.unseal(sealed, "mail-outbox"));
        assertNotEquals(sealed, service.seal(text, "mail-outbox"));
        assertThrows(IllegalStateException.class, () -> service.unseal(sealed, "another-purpose"));
        int tampered = sealed.lastIndexOf(':') + 5;
        String flipped = sealed.substring(0, tampered) + (sealed.charAt(tampered) == 'A' ? 'B' : 'A') + sealed.substring(tampered + 1);
        assertThrows(IllegalStateException.class, () -> service.unseal(flipped, "mail-outbox"));
        assertThrows(IllegalArgumentException.class, () -> service.unseal("plain", "mail-outbox"));
    }

    @Test
    void rejectsMalformedOrTamperedValues() {
        assertThrows(IllegalArgumentException.class, () -> service.reveal("plain-text"));
        assertThrows(IllegalStateException.class, () -> service.reveal("v1:091****678:bad:bad"));
    }
}

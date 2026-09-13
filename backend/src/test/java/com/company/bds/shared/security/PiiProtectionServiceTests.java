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
    void rejectsMalformedOrTamperedValues() {
        assertThrows(IllegalArgumentException.class, () -> service.reveal("plain-text"));
        assertThrows(IllegalStateException.class, () -> service.reveal("v1:091****678:bad:bad"));
    }
}

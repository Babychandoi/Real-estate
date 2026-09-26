package com.company.bds.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactInfoGuardTests {
    @Test
    void detectsPhonesEmailsAndMessengerLinks() {
        assertThat(ContactInfoGuard.containsContact("Liên hệ 0912345678 để xem nhà")).isTrue();
        assertThat(ContactInfoGuard.containsContact("Gọi 0912 345 678")).isTrue();
        assertThat(ContactInfoGuard.containsContact("Hotline 091.234.5678")).isTrue();
        assertThat(ContactInfoGuard.containsContact("+84 912-345-678")).isTrue();
        assertThat(ContactInfoGuard.containsContact("Máy bàn 024 3826 1234")).isTrue();
        assertThat(ContactInfoGuard.containsContact("Mail: chunha@gmail.com")).isTrue();
        assertThat(ContactInfoGuard.containsContact("chunha (at) gmail.com")).isTrue();
        assertThat(ContactInfoGuard.containsContact("Nhắn zalo.me/0912345678")).isTrue();
        assertThat(ContactInfoGuard.containsContact("Xem thêm https://facebook.com/nha.dep")).isTrue();
    }

    @Test
    void ignoresPricesAreasAndAddresses() {
        assertThat(ContactInfoGuard.containsContact("Giá 3.950.000.000 đồng, diện tích 82 m²")).isFalse();
        assertThat(ContactInfoGuard.containsContact("Giá 3950000000, sổ đỏ năm 2026")).isFalse();
        assertThat(ContactInfoGuard.containsContact("Số 10 ngõ 82 Trần Duy Hưng, mặt tiền 4.5 m")).isFalse();
        assertThat(ContactInfoGuard.containsContact("Căn hộ 3PN 96 m², tầng 12, hướng Đông Nam")).isFalse();
    }

    @Test
    void redactsStoredContentAndRejectsNewContent() {
        assertThat(ContactInfoGuard.redact("Gọi 0912 345 678 hoặc a@b.vn"))
                .isEqualTo("Gọi " + ContactInfoGuard.REDACTED + " hoặc " + ContactInfoGuard.REDACTED);
        assertThatThrownBy(() -> ContactInfoGuard.requireNoContact("Tiêu đề hợp lệ", "SĐT 0987654321"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ContactInfoGuard.REJECTION);
    }
}

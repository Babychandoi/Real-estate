package com.company.bds.listing.domain.model;

/** Legal document code (contract §2.1); the free-text {@code legal_status} stays as display detail. */
public enum LegalStatusCode {
    RED_BOOK("Sổ đỏ"),
    PINK_BOOK("Sổ hồng"),
    SALE_CONTRACT("Hợp đồng mua bán"),
    PENDING_CERTIFICATE("Đang chờ sổ"),
    OTHER("Khác");

    private final String label;

    LegalStatusCode(String label) { this.label = label; }

    public String label() { return label; }
}

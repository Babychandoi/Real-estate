package com.company.bds.transaction.domain.model;

/**
 * Hành động biến động tài khoản ký quỹ Escrow bảo đảm (FR28)
 */
public enum EscrowAction {
    DEPOSIT,  // Nạp tiền cọc vào hệ thống
    LOCK,     // Phong tỏa an toàn trong Escrow Vault sau khi 2 bên ký số
    RELEASE,  // Giải ngân cho người bán
    REFUND,   // Hoàn trả cho người mua
    DISPUTE   // Tạm khóa tranh chấp
}

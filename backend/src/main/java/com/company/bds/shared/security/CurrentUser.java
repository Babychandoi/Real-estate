package com.company.bds.shared.security;

import com.company.bds.iam.application.AuthService;
import org.springframework.security.core.Authentication;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    public static UUID id(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthService.UserAccount account) {
            return account.id();
        }
        if (authentication != null) {
            try { return UUID.fromString(authentication.getName()); } catch (IllegalArgumentException ignored) { }
        }
        throw new IllegalStateException("Phiên đăng nhập không có định danh hợp lệ.");
    }
}

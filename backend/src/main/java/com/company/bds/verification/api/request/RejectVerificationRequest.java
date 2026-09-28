package com.company.bds.verification.api.request;

import jakarta.validation.constraints.NotBlank;

/** Rejection or revocation: {@code reasonCode} is a TrustReason of the matching kind; {@code reason} is the note. */
public record RejectVerificationRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        String reason,
        String reasonCode
) {}

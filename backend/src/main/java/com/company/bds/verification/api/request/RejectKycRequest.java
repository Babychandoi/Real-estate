package com.company.bds.verification.api.request;

import jakarta.validation.constraints.NotBlank;

/** {@code reasonCode}: a TrustReason of kind REJECT (REVOKE for a revocation); {@code reason} is the note shown to the user. */
public record RejectKycRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        String reason,
        String reasonCode
) {}

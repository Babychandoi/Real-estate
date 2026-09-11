package com.company.bds.verification.api.request;

import jakarta.validation.constraints.NotBlank;

public record RejectVerificationRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        String reason
) {}

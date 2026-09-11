package com.company.bds.moderation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RejectListingRequest(
        @NotNull(message = "revisionId không được để trống")
        UUID revisionId,
        @NotBlank(message = "Lý do từ chối không được để trống")
        String reasonCode,
        String reasonDetail
) {
}

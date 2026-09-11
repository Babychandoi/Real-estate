package com.company.bds.moderation.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ApproveListingRequest(
        @NotNull(message = "revisionId không được để trống")
        UUID revisionId,
        String note
) {
}

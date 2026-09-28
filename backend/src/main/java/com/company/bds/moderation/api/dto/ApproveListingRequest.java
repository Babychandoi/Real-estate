package com.company.bds.moderation.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code reasonCode} from {@code ApprovalReason} (defaults to MEETS_STANDARDS for older clients). */
public record ApproveListingRequest(
        @NotNull(message = "revisionId không được để trống")
        UUID revisionId,
        String reasonCode,
        @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
        String note
) {
}

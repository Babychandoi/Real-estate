package com.company.bds.moderation.api.dto;

import com.company.bds.moderation.domain.model.FieldDiff;

import java.util.List;
import java.util.UUID;

public record ListingDiffResponse(
        UUID listingId,
        UUID currentRevisionId,
        int currentRevisionNumber,
        UUID previousRevisionId,
        Integer previousRevisionNumber,
        boolean isFirstSubmission,
        int changedCount,
        List<FieldDiff> diffs
) {
}

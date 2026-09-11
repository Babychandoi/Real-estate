package com.company.bds.moderation.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ModerationQueueItemResponse(
        UUID listingId,
        UUID revisionId,
        int revisionNumber,
        String title,
        UUID ownerId,
        long priceVnd,
        BigDecimal areaM2,
        String purpose,
        String propertyType,
        String addressSummary,
        Instant submittedAt,
        int mediaCount,
        boolean isFirstSubmission
) {
}

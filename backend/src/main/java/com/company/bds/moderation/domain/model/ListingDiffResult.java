package com.company.bds.moderation.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Kết quả đối chiếu diff song song 2 phiên bản của tin đăng.
 */
public record ListingDiffResult(
        UUID listingId,
        UUID currentRevisionId,
        int currentRevisionNumber,
        UUID previousRevisionId,
        Integer previousRevisionNumber,
        boolean isFirstSubmission,
        int changedCount,
        List<FieldDiff> diffs
) {
    public static ListingDiffResult of(
            UUID listingId,
            UUID currentRevisionId,
            int currentRevisionNumber,
            UUID previousRevisionId,
            Integer previousRevisionNumber,
            List<FieldDiff> diffs) {
        boolean firstSubmission = previousRevisionId == null;
        int count = (int) diffs.stream().filter(FieldDiff::isChanged).count();
        return new ListingDiffResult(
                listingId,
                currentRevisionId,
                currentRevisionNumber,
                previousRevisionId,
                previousRevisionNumber,
                firstSubmission,
                count,
                diffs
        );
    }
}

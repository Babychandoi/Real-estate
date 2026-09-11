package com.company.bds.moderation.application.port.in;

import com.company.bds.moderation.domain.model.ListingDiffResult;
import java.util.UUID;

public interface GetListingDiffUseCase {
    ListingDiffResult getDiff(UUID listingId);
}

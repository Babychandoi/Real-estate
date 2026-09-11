package com.company.bds.moderation.application.port.in;

import com.company.bds.listing.domain.model.Listing;
import java.util.List;

public interface GetModerationQueueUseCase {
    List<Listing> getPendingQueue();
}

package com.company.bds.moderation.application.port.in;

import com.company.bds.listing.domain.model.Listing;
import com.company.bds.moderation.application.command.ApproveListingCommand;
import com.company.bds.moderation.application.command.RejectListingCommand;

public interface ModerateListingUseCase {
    Listing approve(ApproveListingCommand command);
    Listing reject(RejectListingCommand command);
}

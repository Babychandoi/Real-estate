package com.company.bds.listing.application.port.in;

import com.company.bds.listing.application.command.SubmitListingRevisionCommand;

public interface SubmitListingRevisionUseCase {
    void submitRevision(SubmitListingRevisionCommand command);
}

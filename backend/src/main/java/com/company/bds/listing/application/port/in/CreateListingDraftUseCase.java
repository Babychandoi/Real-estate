package com.company.bds.listing.application.port.in;

import com.company.bds.listing.application.command.CreateListingDraftCommand;


public interface CreateListingDraftUseCase {
    DraftSaved createDraft(CreateListingDraftCommand command);
}

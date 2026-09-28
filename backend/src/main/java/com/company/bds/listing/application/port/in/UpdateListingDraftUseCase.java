package com.company.bds.listing.application.port.in;

import com.company.bds.listing.application.command.UpdateListingDraftCommand;


public interface UpdateListingDraftUseCase {
    DraftSaved updateDraft(UpdateListingDraftCommand command);
}

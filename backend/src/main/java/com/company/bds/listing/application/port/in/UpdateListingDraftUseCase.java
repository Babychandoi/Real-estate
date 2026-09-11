package com.company.bds.listing.application.port.in;

import com.company.bds.listing.application.command.UpdateListingDraftCommand;

import java.util.UUID;

public interface UpdateListingDraftUseCase {
    UUID updateDraft(UpdateListingDraftCommand command);
}

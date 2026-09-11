package com.company.bds.listing.application.port.in;

import com.company.bds.listing.application.command.CreateListingDraftCommand;

import java.util.UUID;

public interface CreateListingDraftUseCase {
    UUID createDraft(CreateListingDraftCommand command);
}

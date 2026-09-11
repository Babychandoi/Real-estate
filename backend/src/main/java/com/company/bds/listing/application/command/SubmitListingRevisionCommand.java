package com.company.bds.listing.application.command;

import java.util.UUID;

public record SubmitListingRevisionCommand(
        UUID listingId,
        UUID requesterId
) {}

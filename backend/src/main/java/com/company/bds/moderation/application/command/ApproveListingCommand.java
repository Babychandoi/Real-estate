package com.company.bds.moderation.application.command;

import java.util.UUID;

public record ApproveListingCommand(
        UUID listingId,
        UUID revisionId,
        UUID moderatorId,
        String note
) {
}

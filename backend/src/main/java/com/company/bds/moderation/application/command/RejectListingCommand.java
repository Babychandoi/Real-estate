package com.company.bds.moderation.application.command;

import java.util.UUID;

public record RejectListingCommand(
        UUID listingId,
        UUID revisionId,
        UUID moderatorId,
        String reasonCode,
        String reasonDetail
) {
}

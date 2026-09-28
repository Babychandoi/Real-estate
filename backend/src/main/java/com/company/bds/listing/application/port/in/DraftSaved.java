package com.company.bds.listing.application.port.in;

import java.util.UUID;

/** Result of saving a draft: the new optimistic-concurrency {@code version} is what the client sends next (If-Match). */
public record DraftSaved(UUID listingId, UUID revisionId, long version) {}

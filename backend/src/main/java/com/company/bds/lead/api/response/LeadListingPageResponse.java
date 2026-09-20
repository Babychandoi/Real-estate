package com.company.bds.lead.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LeadListingPageResponse(
        List<Item> items, long totalElements, int page, int size, int totalPages) {
    public record Item(UUID listingId, String title, String slug, String address, String imageUrl,
                       long totalLeads, long newLeads, long activeLeads, long closedLeads, Instant lastLeadAt) {}
}

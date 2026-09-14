package com.company.bds.lead.api.response;

import com.company.bds.lead.domain.model.LeadPage;

import java.util.List;
import java.util.Map;
import com.company.bds.lead.domain.model.LeadStatus;

public record LeadPageResponse(List<LeadResponse> items, long totalElements, int page, int size, int totalPages,
        Map<LeadStatus, Long> statusCounts) {
    public static LeadPageResponse fromDomain(LeadPage page) {
        return new LeadPageResponse(page.items().stream().map(LeadResponse::fromDomain).toList(),
                page.totalElements(), page.page(), page.size(), page.totalPages(), page.statusCounts());
    }
}

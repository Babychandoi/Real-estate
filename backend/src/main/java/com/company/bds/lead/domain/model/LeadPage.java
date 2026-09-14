package com.company.bds.lead.domain.model;

import java.util.List;
import java.util.Map;

public record LeadPage(List<Lead> items, long totalElements, int page, int size, Map<LeadStatus, Long> statusCounts) {
    public static LeadPage empty(int page, int size) {
        return new LeadPage(List.of(), 0, page, size, Map.of());
    }

    public int totalPages() {
        return totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}

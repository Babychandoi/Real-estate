package com.company.bds.lead.api.response;

import java.util.List;
import java.util.Map;

public record ProductAnalyticsOverviewResponse(
        long activeListingsCount,
        long totalDetailViews,
        long totalContactClicks,
        long totalLeadsSubmitted,
        long totalEscrowDeposited,
        double avgModerationHours,
        double verifiedOwnerRatioPercent,
        Map<String, Integer> sourceBreakdownPercent,
        List<DistrictMetric> districtBreakdown
) {
    public record DistrictMetric(String districtName, long listingsCount, long leadsCount) {}
}

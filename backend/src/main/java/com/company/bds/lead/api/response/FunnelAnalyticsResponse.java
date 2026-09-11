package com.company.bds.lead.api.response;

import java.util.List;

public record FunnelAnalyticsResponse(
        long impressions,
        long detailViews,
        long leadsSubmitted,
        long contactedCount,
        long dealsClosed,
        double conversionRatePercent,
        List<StepMetric> steps
) {
    public record StepMetric(
            int stepIndex,
            String stepName,
            long count,
            double percentage
    ) {}
}

package com.company.bds.lead.api;

import com.company.bds.lead.api.response.FunnelAnalyticsResponse;
import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.lead.domain.port.LeadPersistencePort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller cung cấp số liệu phân tích phễu chuyển đổi 5 bước (FR29).
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class FunnelAnalyticsController {

    private final LeadPersistencePort leadPersistencePort;

    public FunnelAnalyticsController(LeadPersistencePort leadPersistencePort) {
        this.leadPersistencePort = leadPersistencePort;
    }

    @GetMapping("/funnel")
    public ResponseEntity<FunnelAnalyticsResponse> getFunnelAnalytics() {
        long totalLeads = leadPersistencePort.countAll();
        long totalContacted = leadPersistencePort.countByStatuses(
                List.of(LeadStatus.CONTACTED, LeadStatus.APPOINTED, LeadStatus.CLOSED));
        long totalClosed = leadPersistencePort.countByStatuses(List.of(LeadStatus.CLOSED));
        long impressions = 0;
        long detailViews = 0;

        double conversionRate = totalLeads > 0 ? (double) totalClosed / totalLeads * 100 : 0.0;
        double roundedRate = Math.round(conversionRate * 10.0) / 10.0;

        List<FunnelAnalyticsResponse.StepMetric> steps = List.of(
                new FunnelAnalyticsResponse.StepMetric(1, "Lượt hiển thị (chưa thu thập)", impressions, 0.0),
                new FunnelAnalyticsResponse.StepMetric(2, "Xem chi tiết tin (chưa thu thập)", detailViews, 0.0),
                new FunnelAnalyticsResponse.StepMetric(3, "Gửi liên hệ Lead", totalLeads, 0.0),
                new FunnelAnalyticsResponse.StepMetric(4, "Môi giới kết nối / Hẹn xem", totalContacted, percent(totalContacted, totalLeads)),
                new FunnelAnalyticsResponse.StepMetric(5, "Chốt giao dịch", totalClosed, percent(totalClosed, totalContacted))
        );

        return ResponseEntity.ok(new FunnelAnalyticsResponse(
                impressions,
                detailViews,
                totalLeads,
                totalContacted,
                totalClosed,
                roundedRate,
                steps
        ));
    }

    @GetMapping("/overview")
    public ResponseEntity<com.company.bds.lead.api.response.ProductAnalyticsOverviewResponse> getOverviewAnalytics() {
        return ResponseEntity.ok(new com.company.bds.lead.api.response.ProductAnalyticsOverviewResponse(
                0, 0, 0, leadPersistencePort.countAll(), 0, 0, 0, java.util.Map.of(), List.of()));
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0 : Math.round((double) part / total * 1000.0) / 10.0;
    }
}

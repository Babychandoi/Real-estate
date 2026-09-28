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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public FunnelAnalyticsController(LeadPersistencePort leadPersistencePort, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.leadPersistencePort = leadPersistencePort;
        this.jdbc = jdbc;
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

    /**
     * KYC drop-off funnel (F17.4): form opened → KYC required shown → lead submitted, over [from, to) days (UTC, max 92),
     * internal and bot traffic excluded. Web events without consent carry no session, so steps are counted as events;
     * the qualified share of submitted leads is the quality guardrail. Values are counts of recorded events only.
     */
    @GetMapping("/lead-funnel")
    public java.util.Map<String, Object> leadFunnel(
            @org.springframework.web.bind.annotation.RequestParam(name = "days", defaultValue = "30") int days) {
        int window = Math.max(1, Math.min(92, days));
        java.sql.Timestamp since = java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(window)));
        java.util.Map<String, Object> counts = jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE name = 'lead_form_opened') AS form_opened,
                       COUNT(*) FILTER (WHERE name = 'kyc_required_shown' AND properties ->> 'context' = 'lead_form') AS kyc_required,
                       COUNT(*) FILTER (WHERE name = 'lead_submitted') AS submitted
                FROM analytics_events
                WHERE name IN ('lead_form_opened','kyc_required_shown','lead_submitted') AND occurred_at >= ?
                  AND is_internal = FALSE AND is_bot = FALSE
                """, since);
        java.util.Map<String, Object> quality = jdbc.queryForMap("""
                SELECT COUNT(*) AS leads, COUNT(*) FILTER (WHERE qualification = 'QUALIFIED') AS qualified,
                       COUNT(*) FILTER (WHERE qualification IS NOT NULL) AS assessed
                FROM leads WHERE created_at >= ?
                """, since);
        long opened = ((Number) counts.get("form_opened")).longValue();
        long kyc = ((Number) counts.get("kyc_required")).longValue();
        long submitted = ((Number) counts.get("submitted")).longValue();
        long assessed = ((Number) quality.get("assessed")).longValue();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("days", window);
        result.put("formOpened", opened);
        result.put("kycRequiredShown", kyc);
        result.put("leadSubmitted", submitted);
        result.put("kycShownPercentOfOpened", opened == 0 ? null : percent(kyc, opened));
        result.put("submittedPercentOfOpened", opened == 0 ? null : percent(submitted, opened));
        result.put("leadsAssessed", assessed);
        result.put("qualifiedPercentOfAssessed", assessed == 0 ? null : percent(((Number) quality.get("qualified")).longValue(), assessed));
        return result;
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0 : Math.round((double) part / total * 1000.0) / 10.0;
    }
}

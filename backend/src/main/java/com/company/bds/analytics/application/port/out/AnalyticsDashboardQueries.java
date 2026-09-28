package com.company.bds.analytics.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Read side of the analytics dashboard. Every query is bounded by the window (≤ 92 days, validated by the service) and
 * excludes bot and internal traffic. Web queries only see consented events (nothing else is stored).
 */
public interface AnalyticsDashboardQueries {

    /** Filters; {@code null} = all. {@code source} is the lower-case UTM source, or {@link #DIRECT} for none. */
    record Filters(String device, String area, String source) {
        public boolean webOnly() { return device != null || source != null; }
    }

    String DIRECT = "(direct)";

    record Window(Instant start, Instant end, LocalDate firstDay, LocalDate lastDay) {}

    record WebVolume(long events, long sessions) {}

    record SearchFunnel(long searchSessions, long detailSessions, long leadFormSessions, long leadSessions) {}

    record KycFunnel(long formSessions, long kycShownSessions, long submittedAfterKyc, long submittedSessions) {}

    record ZeroResults(long searchesWithCount, long zeroResultSearches) {}

    record PostingFunnel(long created, long submitted, long approved, long activeNow, Double medianHoursToApproval) {}

    record LeadFunnel(long leads, long responded, long respondedWithinTarget, long assessed, long qualified,
                      long appointmentConfirmed, long appointmentCompleted, long appointmentNoShow,
                      Double medianResponseMinutes, Double p90ResponseMinutes) {}

    record NorthStar(long confirmedAppointments, long qualifiedSeekers) {}

    record Supply(long fakeSoldReports, long activeListings) {}

    record Broker(long approvedRevenueVnd, long qualifiedLeadsOfPayers, long payers, long returningPayers) {}

    record ReturnVisits(long devices, long returningDevices) {}

    record CohortCell(LocalDate cohortWeek, int weekOffset, long devices) {}

    record WebVital(String metric, String device, double p75, long samples) {}

    record Breakdown(String key, long searchSessions, long detailViews, long leadForms) {}

    record TrendDay(LocalDate day, long searches, long detailViews, long leadForms, long leads) {}

    record Freshness(Instant latestWebEventAt, Instant aggregatesComputedAt) {}

    WebVolume webVolume(Window window, Filters filters);

    SearchFunnel searchFunnel(Window window, Filters filters);

    KycFunnel kycFunnel(Window window, Filters filters);

    ZeroResults zeroResults(Window window, Filters filters);

    PostingFunnel postingFunnel(Window window, Filters filters);

    LeadFunnel leadFunnel(Window window, Filters filters);

    NorthStar northStar(Window window, Filters filters);

    Supply supply(Window window, Filters filters);

    Broker broker(Window window);

    ReturnVisits returnVisits(Window window, Filters filters);

    List<CohortCell> cohorts(Window window, Filters filters);

    List<WebVital> webVitals(Window window, Filters filters);

    /** {@code dimension}: source, device or area; top {@code limit} by search sessions. */
    List<Breakdown> breakdown(Window window, Filters filters, String dimension, int limit);

    List<TrendDay> trend(Window window, Filters filters);

    Freshness freshness(Instant now);
}

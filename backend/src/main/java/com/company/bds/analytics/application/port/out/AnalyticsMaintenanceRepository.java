package com.company.bds.analytics.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Aggregation and retention on the analytics tables. Every delete/update is batched by the adapter. */
public interface AnalyticsMaintenanceRepository {

    /** Replaces the aggregates of {@code day} with counts over [{@code start}, {@code end}); bots/internal excluded. */
    void recomputeDay(LocalDate day, Instant start, Instant end);

    /** Devices with more than {@code maxEvents} unflagged events since {@code since} (at most {@code limit}). */
    List<String> busyDevices(Instant since, int maxEvents, int limit);

    /** Nulls anonymous id, session id, user id and UTM of events that occurred before {@code before}. */
    int stripIdentifiers(Instant before);

    int deleteEvents(Instant before);

    int deleteVisitorDays(LocalDate before);

    int deleteDailyMetrics(LocalDate before);

    int deleteConsentRecords(Instant before);

    int deleteDeviceFlags(Instant before);
}

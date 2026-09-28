package com.company.bds.analytics.infrastructure.persistence;

import com.company.bds.analytics.application.port.out.AnalyticsMaintenanceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * JDBC side of aggregation and retention. Range predicates on {@code occurred_at} use the BRIN index (V095); deletes and
 * updates run in batches of {@value #BATCH} rows ({@code ctid IN (... LIMIT n)}) so no statement holds locks for long.
 */
@Component
class AnalyticsMaintenancePersistenceAdapter implements AnalyticsMaintenanceRepository {
    static final int BATCH = 5_000;
    private static final int MAX_ROUNDS = 200;

    private final JdbcTemplate jdbc;

    AnalyticsMaintenancePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void recomputeDay(LocalDate day, Instant start, Instant end) {
        Date date = Date.valueOf(day);
        Timestamp from = Timestamp.from(start);
        Timestamp to = Timestamp.from(end);
        jdbc.update("DELETE FROM analytics_daily_metrics WHERE day = ?", date);
        jdbc.update("""
                INSERT INTO analytics_daily_metrics (day, name, device, area_code, source, events, sessions, computed_at)
                SELECT ?, name, COALESCE(device, ''), COALESCE(area_code, ''), COALESCE(lower(utm ->> 'source'), ''),
                       COUNT(*), COUNT(DISTINCT session_id), now()
                FROM analytics_events
                WHERE occurred_at >= ? AND occurred_at < ? AND is_bot = FALSE AND is_internal = FALSE
                GROUP BY name, COALESCE(device, ''), COALESCE(area_code, ''), COALESCE(lower(utm ->> 'source'), '')
                """, date, from, to);
        jdbc.update("DELETE FROM analytics_visitor_days WHERE day = ?", date);
        jdbc.update("""
                INSERT INTO analytics_visitor_days (day, anonymous_id, device, source)
                SELECT ?, anonymous_id, COALESCE(MIN(device), ''), COALESCE(MIN(lower(utm ->> 'source')), '')
                FROM analytics_events
                WHERE occurred_at >= ? AND occurred_at < ? AND anonymous_id IS NOT NULL AND is_bot = FALSE AND is_internal = FALSE
                GROUP BY anonymous_id
                """, date, from, to);
    }

    @Override
    public List<String> busyDevices(Instant since, int maxEvents, int limit) {
        return jdbc.queryForList("""
                SELECT anonymous_id FROM analytics_events
                WHERE occurred_at >= ? AND anonymous_id IS NOT NULL AND is_bot = FALSE AND origin = 'web'
                GROUP BY anonymous_id HAVING COUNT(*) > ?
                ORDER BY COUNT(*) DESC LIMIT ?
                """, String.class, Timestamp.from(since), maxEvents, limit);
    }

    @Override
    public int stripIdentifiers(Instant before) {
        return batched("""
                UPDATE analytics_events SET anonymous_id = NULL, session_id = NULL, user_id = NULL, utm = NULL
                WHERE ctid IN (SELECT ctid FROM analytics_events
                               WHERE occurred_at < ? AND (anonymous_id IS NOT NULL OR session_id IS NOT NULL
                                                          OR user_id IS NOT NULL OR utm IS NOT NULL)
                               LIMIT ?)
                """, Timestamp.from(before));
    }

    @Override
    public int deleteEvents(Instant before) {
        return batched("DELETE FROM analytics_events WHERE ctid IN (SELECT ctid FROM analytics_events WHERE occurred_at < ? LIMIT ?)",
                Timestamp.from(before));
    }

    @Override
    public int deleteVisitorDays(LocalDate before) {
        return batched("DELETE FROM analytics_visitor_days WHERE ctid IN (SELECT ctid FROM analytics_visitor_days WHERE day < ? LIMIT ?)",
                Date.valueOf(before));
    }

    @Override
    public int deleteDailyMetrics(LocalDate before) {
        return batched("DELETE FROM analytics_daily_metrics WHERE ctid IN (SELECT ctid FROM analytics_daily_metrics WHERE day < ? LIMIT ?)",
                Date.valueOf(before));
    }

    @Override
    public int deleteConsentRecords(Instant before) {
        return batched("""
                DELETE FROM analytics_consent_records WHERE ctid IN (SELECT ctid FROM analytics_consent_records
                                                                     WHERE recorded_at < ? LIMIT ?)
                """, Timestamp.from(before));
    }

    @Override
    public int deleteDeviceFlags(Instant before) {
        return batched("DELETE FROM analytics_client_flags WHERE ctid IN (SELECT ctid FROM analytics_client_flags WHERE flagged_at < ? LIMIT ?)",
                Timestamp.from(before));
    }

    private int batched(String sql, Object bound) {
        int total = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            int changed = jdbc.update(sql, bound, BATCH);
            total += changed;
            if (changed < BATCH) break;
        }
        return total;
    }
}

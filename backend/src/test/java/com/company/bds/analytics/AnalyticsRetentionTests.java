package com.company.bds.analytics;

import com.company.bds.analytics.application.AnalyticsMaintenanceService;
import com.company.bds.analytics.domain.RetentionPolicy;
import com.company.bds.testsupport.BdsIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F19.2 (S8): retention strips/deletes only what is due; aggregation is idempotent and excludes bots/internal. */
@BdsIntegrationTest
class AnalyticsRetentionTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired AnalyticsMaintenanceService maintenance;

    @Test
    void retentionPseudonymisesThenDeletesAndKeepsRecentData() {
        RetentionPolicy policy = maintenance.policy();
        Instant now = Instant.now();
        UUID recent = insert(now.minus(Duration.ofDays(1)));
        UUID pseudonymise = insert(now.minus(policy.identifiers()).minus(Duration.ofDays(1)));
        UUID delete = insert(now.minus(policy.rawEvents()).minus(Duration.ofDays(1)));
        String oldConsent = "old-" + UUID.randomUUID();
        String newConsent = "new-" + UUID.randomUUID();
        consent(oldConsent, now.minus(policy.consentRecords()).minus(Duration.ofDays(1)));
        consent(newConsent, now.minus(Duration.ofDays(30)));
        String oldDevice = "oldflag-" + UUID.randomUUID();
        jdbc.update("INSERT INTO analytics_client_flags (anonymous_id, kind, reason, flagged_at) VALUES (?, 'BOT', 'TEST', ?)",
                oldDevice, Timestamp.from(now.minus(policy.deviceFlags()).minus(Duration.ofDays(1))));
        LocalDate today = LocalDate.now(AnalyticsMaintenanceService.VIETNAM);
        String visitor = "vd-" + UUID.randomUUID();
        visitorDay(today.minusDays(policy.visitorDays().toDays() + 1), visitor);
        visitorDay(today.minusDays(1), visitor);
        metricDay(today.minusDays(policy.dailyMetrics().toDays() + 1));

        Map<String, Integer> result = maintenance.applyRetention();

        assertThat(result.get("identifiersStripped")).isGreaterThanOrEqualTo(1);
        assertThat(result.get("eventsDeleted")).isGreaterThanOrEqualTo(1);
        Map<String, Object> kept = row(recent);
        assertThat(kept.get("anonymous_id")).isNotNull();
        assertThat(kept.get("user_id")).isNotNull();
        Map<String, Object> stripped = row(pseudonymise);
        assertThat(stripped.get("anonymous_id")).isNull();
        assertThat(stripped.get("session_id")).isNull();
        assertThat(stripped.get("user_id")).isNull();
        assertThat(stripped.get("utm")).isNull();
        assertThat(stripped.get("name")).as("the fact itself stays until raw retention").isEqualTo("search_performed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_events WHERE event_id = ?", Long.class, delete)).isZero();
        assertThat(count("analytics_consent_records", "consent_id", oldConsent)).isZero();
        assertThat(count("analytics_consent_records", "consent_id", newConsent)).isOne();
        assertThat(count("analytics_client_flags", "anonymous_id", oldDevice)).isZero();
        assertThat(count("analytics_visitor_days", "anonymous_id", visitor)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_daily_metrics WHERE day < ?", Long.class,
                Date.valueOf(today.minusDays(policy.dailyMetrics().toDays())))).isZero();

        assertThat(maintenance.applyRetention().get("eventsDeleted")).as("nothing left to do").isZero();
    }

    @Test
    void aggregationIsIdempotentAndExcludesBotsAndInternalTraffic() {
        LocalDate day = LocalDate.now(AnalyticsMaintenanceService.VIETNAM).minusDays(2);
        Instant noon = day.atTime(12, 0).atZone(AnalyticsMaintenanceService.VIETNAM).toInstant();
        String source = "agg" + UUID.randomUUID().toString().substring(0, 8);
        String deviceA = "agg-a-" + UUID.randomUUID();
        String deviceB = "agg-b-" + UUID.randomUUID();
        event(noon, deviceA, "sa-" + deviceA, source, false, false);
        event(noon.plusSeconds(60), deviceA, "sa-" + deviceA, source, false, false);
        event(noon, deviceB, "sb-" + deviceB, source, false, false);
        event(noon, "agg-bot-" + UUID.randomUUID(), "sbot", source, true, false);
        event(noon, "agg-staff-" + UUID.randomUUID(), "sstaff", source, false, true);
        // Just after local midnight belongs to the next Vietnam day.
        event(day.plusDays(1).atStartOfDay(AnalyticsMaintenanceService.VIETNAM).toInstant().plusSeconds(5), deviceA, "sa2", source, false, false);

        maintenance.aggregate(day);
        maintenance.aggregate(day);

        Map<String, Object> metrics = jdbc.queryForMap("""
                SELECT SUM(events) AS events, SUM(sessions) AS sessions, COUNT(*) AS rows_ FROM analytics_daily_metrics
                WHERE day = ? AND source = ? AND name = 'search_performed'
                """, Date.valueOf(day), source);
        assertThat(((Number) metrics.get("events")).longValue()).isEqualTo(3);
        assertThat(((Number) metrics.get("sessions")).longValue()).isEqualTo(2);
        assertThat(((Number) metrics.get("rows_")).longValue()).as("one row per dimension combination").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_visitor_days WHERE day = ? AND source = ?", Long.class,
                Date.valueOf(day), source)).isEqualTo(2);
    }

    @Test
    void thePolicyRejectsIdentifiersKeptLongerThanTheEvents() {
        assertThatThrownBy(() -> new RetentionPolicy(Duration.ofDays(200), Duration.ofDays(180), Duration.ofDays(90),
                Duration.ofDays(760), Duration.ofDays(1095), Duration.ofDays(180), 600)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetentionPolicy(Duration.ofDays(90), Duration.ofDays(180), Duration.ofDays(120),
                Duration.ofDays(760), Duration.ofDays(1095), Duration.ofDays(180), 600)).isInstanceOf(IllegalArgumentException.class);
    }

    private UUID insert(Instant occurredAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO analytics_events (event_id, name, schema_version, occurred_at, anonymous_id, session_id, user_id, origin,
                                              properties, utm)
                VALUES (?, 'search_performed', 1, ?, ?, 'ret-session', ?, 'web', '{}'::jsonb, '{"source":"google"}'::jsonb)
                """, id, Timestamp.from(occurredAt), "ret-" + UUID.randomUUID(), UUID.randomUUID());
        return id;
    }

    private void event(Instant at, String device, String session, String source, boolean bot, boolean internal) {
        jdbc.update("""
                INSERT INTO analytics_events (event_id, name, schema_version, occurred_at, anonymous_id, session_id, origin, device,
                                              is_bot, is_internal, properties, utm)
                VALUES (?, 'search_performed', 1, ?, ?, ?, 'web', 'mobile', ?, ?, '{}'::jsonb, jsonb_build_object('source', ?::text))
                """, UUID.randomUUID(), Timestamp.from(at), device, session, bot, internal, source);
    }

    private void consent(String consentId, Instant at) {
        jdbc.update("""
                INSERT INTO analytics_consent_records (id, consent_id, purpose, choice, policy_version, source, recorded_at)
                VALUES (?, ?, 'analytics', 'granted', '2026-09-28', 'banner', ?)
                """, UUID.randomUUID(), consentId, Timestamp.from(at));
    }

    private void visitorDay(LocalDate day, String device) {
        jdbc.update("INSERT INTO analytics_visitor_days (day, anonymous_id, device, source) VALUES (?, ?, 'mobile', '')", Date.valueOf(day), device);
    }

    private void metricDay(LocalDate day) {
        jdbc.update("""
                INSERT INTO analytics_daily_metrics (day, name, device, area_code, source, events, sessions)
                VALUES (?, 'search_performed', '', '', 'old', 1, 1) ON CONFLICT DO NOTHING
                """, Date.valueOf(day));
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT name, anonymous_id, session_id, user_id, utm::text AS utm FROM analytics_events WHERE event_id = ?", id);
    }

    private long count(String table, String column, String value) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Long.class, value);
    }
}

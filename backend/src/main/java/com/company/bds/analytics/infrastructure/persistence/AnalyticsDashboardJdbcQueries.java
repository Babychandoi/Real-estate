package com.company.bds.analytics.infrastructure.persistence;

import com.company.bds.analytics.application.port.out.AnalyticsDashboardQueries;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SQL for the analytics dashboard. Web queries filter on {@code (name, occurred_at)} (index from V029) or the BRIN on
 * {@code occurred_at} (V095); lead/listing queries on the {@code created_at} indexes from V095. Breakdowns, trend and
 * cohorts read the small aggregate tables. Bot and internal traffic are always excluded; staff requesters' leads too.
 */
@Component
class AnalyticsDashboardJdbcQueries implements AnalyticsDashboardQueries {
    private static final String NOT_STAFF = "NOT EXISTS (SELECT 1 FROM user_roles sr WHERE sr.user_id = %s AND sr.role IN ('ADMIN', 'MODERATOR'))";
    private static final String SOURCE = "COALESCE(lower(e.utm ->> 'source'), '" + DIRECT + "')";
    private static final Set<String> DIMENSIONS = Set.of("source", "device", "area");

    private final JdbcTemplate jdbc;

    AnalyticsDashboardJdbcQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** SQL fragment + arguments for the web filters on {@code analytics_events e}. */
    private record Where(String sql, List<Object> args) {
        Object[] with(Object... leading) {
            List<Object> all = new ArrayList<>(List.of(leading));
            all.addAll(args);
            return all.toArray();
        }
    }

    private static Where web(Window window, Filters filters, boolean withArea) {
        StringBuilder sql = new StringBuilder(" AND e.occurred_at >= ? AND e.occurred_at < ? AND e.is_bot = FALSE AND e.is_internal = FALSE");
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(window.start()), Timestamp.from(window.end())));
        if (filters.device() != null) {
            sql.append(" AND e.device = ?");
            args.add(filters.device());
        }
        if (filters.source() != null) {
            sql.append(" AND ").append(SOURCE).append(" = ?");
            args.add(filters.source());
        }
        if (withArea && filters.area() != null) {
            sql.append(" AND e.area_code = ?");
            args.add(filters.area());
        }
        return new Where(sql.toString(), args);
    }

    @Override
    public WebVolume webVolume(Window window, Filters filters) {
        Where where = web(window, filters, false);
        return jdbc.queryForObject("SELECT COUNT(*), COUNT(DISTINCT e.session_id) FROM analytics_events e WHERE e.origin = 'web'" + where.sql(),
                (rs, i) -> new WebVolume(rs.getLong(1), rs.getLong(2)), where.with());
    }

    @Override
    public SearchFunnel searchFunnel(Window window, Filters filters) {
        Where where = web(window, filters, false);
        Where leads = serverWindow(window);
        return jdbc.queryForObject("""
                WITH sessions AS (
                    SELECT e.session_id,
                           bool_or(e.name = 'search_performed') AS searched,
                           bool_or(e.name = 'listing_detail_viewed') AS viewed,
                           bool_or(e.name = 'lead_form_opened') AS opened,
                           (array_agg(e.user_id) FILTER (WHERE e.user_id IS NOT NULL))[1] AS user_id
                    FROM analytics_events e
                    WHERE e.name IN ('search_performed', 'listing_detail_viewed', 'lead_form_opened') AND e.session_id IS NOT NULL
                    %s
                    GROUP BY e.session_id),
                leads AS (
                    SELECT DISTINCT e.user_id FROM analytics_events e
                    WHERE e.name = 'lead_submitted' AND e.user_id IS NOT NULL %s)
                SELECT COUNT(*) FILTER (WHERE searched),
                       COUNT(*) FILTER (WHERE searched AND viewed),
                       COUNT(*) FILTER (WHERE searched AND viewed AND opened),
                       COUNT(*) FILTER (WHERE searched AND viewed AND user_id IN (SELECT user_id FROM leads))
                FROM sessions
                """.formatted(where.sql(), leads.sql()),
                (rs, i) -> new SearchFunnel(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
                concat(where.with(), leads.with()));
    }

    @Override
    public KycFunnel kycFunnel(Window window, Filters filters) {
        Where where = web(window, filters, false);
        Where leads = serverWindow(window);
        return jdbc.queryForObject("""
                WITH sessions AS (
                    SELECT e.session_id,
                           bool_or(e.name = 'lead_form_opened') AS opened,
                           bool_or(e.name = 'kyc_required_shown' AND e.properties ->> 'context' = 'lead_form') AS kyc,
                           (array_agg(e.user_id) FILTER (WHERE e.user_id IS NOT NULL))[1] AS user_id
                    FROM analytics_events e
                    WHERE e.name IN ('lead_form_opened', 'kyc_required_shown') AND e.session_id IS NOT NULL
                    %s
                    GROUP BY e.session_id),
                leads AS (
                    SELECT DISTINCT e.user_id FROM analytics_events e
                    WHERE e.name = 'lead_submitted' AND e.user_id IS NOT NULL %s)
                SELECT COUNT(*) FILTER (WHERE opened),
                       COUNT(*) FILTER (WHERE opened AND kyc),
                       COUNT(*) FILTER (WHERE opened AND kyc AND user_id IN (SELECT user_id FROM leads)),
                       COUNT(*) FILTER (WHERE opened AND user_id IN (SELECT user_id FROM leads))
                FROM sessions
                """.formatted(where.sql(), leads.sql()),
                (rs, i) -> new KycFunnel(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
                concat(where.with(), leads.with()));
    }

    /** Server events in the window (no device/source: the server does not know them). */
    private static Where serverWindow(Window window) {
        return new Where(" AND e.occurred_at >= ? AND e.occurred_at < ? AND e.is_bot = FALSE AND e.is_internal = FALSE",
                List.of(Timestamp.from(window.start()), Timestamp.from(window.end())));
    }

    @Override
    public ZeroResults zeroResults(Window window, Filters filters) {
        Where where = web(window, filters, false);
        return jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE (e.properties -> 'zeroResult') IS NOT NULL),
                       COUNT(*) FILTER (WHERE (e.properties ->> 'zeroResult') = 'true')
                FROM analytics_events e WHERE e.name = 'search_performed'
                """ + where.sql(), (rs, i) -> new ZeroResults(rs.getLong(1), rs.getLong(2)), where.with());
    }

    @Override
    public PostingFunnel postingFunnel(Window window, Filters filters) {
        String area = filters.area() == null ? "" : " AND EXISTS (SELECT 1 FROM listing_revisions ar WHERE ar.listing_id = l.id AND ar.district_code = ?)";
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(window.start()), Timestamp.from(window.end())));
        if (filters.area() != null) args.add(filters.area());
        return jdbc.queryForObject("""
                WITH created AS (
                    SELECT l.id, l.status,
                           (SELECT MIN(r.submitted_at) FROM listing_revisions r WHERE r.listing_id = l.id) AS submitted_at,
                           (SELECT MIN(r.moderated_at) FROM listing_revisions r WHERE r.listing_id = l.id AND r.status = 'APPROVED') AS approved_at
                    FROM listings l
                    WHERE l.created_at >= ? AND l.created_at < ? AND l.source <> 'SEED'
                      AND %s %s)
                SELECT COUNT(*), COUNT(submitted_at), COUNT(approved_at), COUNT(*) FILTER (WHERE status = 'ACTIVE'),
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (approved_at - submitted_at)) / 3600)
                           FILTER (WHERE approved_at IS NOT NULL AND submitted_at IS NOT NULL AND approved_at >= submitted_at)
                FROM created
                """.formatted(NOT_STAFF.formatted("l.owner_id"), area),
                (rs, i) -> new PostingFunnel(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), doubleOrNull(rs, 5)),
                args.toArray());
    }

    /** Leads created in the window, staff requesters excluded; area = district of the listing's public revision. */
    private static String leadsInWindow(Filters filters) {
        String area = filters.area() == null ? "" : """
                 AND EXISTS (SELECT 1 FROM listings al JOIN listing_revisions ar ON ar.id = al.public_revision_id
                             WHERE al.id = l.listing_id AND ar.district_code = ?)""";
        return "FROM leads l WHERE l.created_at >= ? AND l.created_at < ? AND l.status <> 'SPAM' AND "
                + NOT_STAFF.formatted("l.requester_id") + area;
    }

    private static Object[] leadArgs(Window window, Filters filters) {
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(window.start()), Timestamp.from(window.end())));
        if (filters.area() != null) args.add(filters.area());
        return args.toArray();
    }

    @Override
    public LeadFunnel leadFunnel(Window window, Filters filters) {
        return jdbc.queryForObject("""
                WITH w AS (
                    SELECT l.id, l.qualification,
                           EXTRACT(EPOCH FROM (l.first_response_at - l.created_at)) / 60 AS response_minutes,
                           COALESCE((SELECT s.first_response_minutes FROM listings sl JOIN broker_sla_settings s ON s.user_id = sl.owner_id
                                     WHERE sl.id = l.listing_id), 30) AS target_minutes,
                           EXISTS (SELECT 1 FROM viewing_appointments a WHERE a.lead_id = l.id AND a.confirmed_at IS NOT NULL) AS confirmed,
                           EXISTS (SELECT 1 FROM viewing_appointments a WHERE a.lead_id = l.id AND a.status = 'COMPLETED') AS completed,
                           EXISTS (SELECT 1 FROM viewing_appointments a WHERE a.lead_id = l.id AND a.status = 'NO_SHOW') AS no_show
                    %s)
                SELECT COUNT(*), COUNT(response_minutes), COUNT(*) FILTER (WHERE response_minutes <= target_minutes),
                       COUNT(qualification), COUNT(*) FILTER (WHERE qualification = 'QUALIFIED'),
                       COUNT(*) FILTER (WHERE confirmed), COUNT(*) FILTER (WHERE completed), COUNT(*) FILTER (WHERE no_show),
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY response_minutes) FILTER (WHERE response_minutes IS NOT NULL),
                       percentile_cont(0.9) WITHIN GROUP (ORDER BY response_minutes) FILTER (WHERE response_minutes IS NOT NULL)
                FROM w
                """.formatted(leadsInWindow(filters)),
                (rs, i) -> new LeadFunnel(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6),
                        rs.getLong(7), rs.getLong(8), doubleOrNull(rs, 9), doubleOrNull(rs, 10)),
                leadArgs(window, filters));
    }

    @Override
    public NorthStar northStar(Window window, Filters filters) {
        String area = filters.area() == null ? "" : """
                 AND EXISTS (SELECT 1 FROM listings al JOIN listing_revisions ar ON ar.id = al.public_revision_id
                             WHERE al.id = a.listing_id AND ar.district_code = ?)""";
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(window.start()), Timestamp.from(window.end())));
        if (filters.area() != null) args.add(filters.area());
        long confirmed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM viewing_appointments a
                WHERE a.confirmed_at >= ? AND a.confirmed_at < ? AND a.status IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW') AND %s %s
                """.formatted(NOT_STAFF.formatted("a.requester_id"), area), Long.class, args.toArray());
        long seekers = jdbc.queryForObject("SELECT COUNT(DISTINCT l.requester_id) " + leadsInWindow(filters)
                + " AND l.qualification = 'QUALIFIED' AND l.requester_id IS NOT NULL", Long.class, leadArgs(window, filters));
        return new NorthStar(confirmed, seekers);
    }

    @Override
    public Supply supply(Window window, Filters filters) {
        String area = filters.area() == null ? "" : """
                 AND EXISTS (SELECT 1 FROM listing_revisions ar WHERE ar.id = l.public_revision_id AND ar.district_code = ?)""";
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(window.start()), Timestamp.from(window.end())));
        if (filters.area() != null) args.add(filters.area());
        long reports = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT rp.listing_id) FROM listing_reports rp JOIN listings l ON l.id = rp.listing_id
                WHERE rp.category = 'FAKE_SOLD' AND rp.created_at >= ? AND rp.created_at < ? %s
                """.formatted(area), Long.class, args.toArray());
        List<Object> activeArgs = filters.area() == null ? List.of() : List.of(filters.area());
        long active = jdbc.queryForObject("SELECT COUNT(*) FROM listings l WHERE l.status = 'ACTIVE'" + area, Long.class, activeArgs.toArray());
        return new Supply(reports, active);
    }

    @Override
    public Broker broker(Window window) {
        Object[] args = {Timestamp.from(window.start()), Timestamp.from(window.end())};
        Map<String, Object> paid = jdbc.queryForMap("""
                SELECT COALESCE(SUM(o.amount_vnd), 0) AS revenue, COUNT(DISTINCT o.user_id) AS payers,
                       COUNT(DISTINCT o.user_id) FILTER (WHERE EXISTS (
                           SELECT 1 FROM package_orders p WHERE p.user_id = o.user_id AND p.status = 'APPROVED'
                             AND p.reviewed_at < o.reviewed_at)) AS returning
                FROM package_orders o WHERE o.status = 'APPROVED' AND o.reviewed_at >= ? AND o.reviewed_at < ?
                """, args);
        long qualified = jdbc.queryForObject("""
                SELECT COUNT(*) FROM leads l JOIN listings li ON li.id = l.listing_id
                WHERE l.created_at >= ? AND l.created_at < ? AND l.qualification = 'QUALIFIED'
                  AND EXISTS (SELECT 1 FROM package_orders o WHERE o.user_id = li.owner_id AND o.status = 'APPROVED'
                                AND o.reviewed_at >= ? AND o.reviewed_at < ?)
                """, Long.class, args[0], args[1], args[0], args[1]);
        return new Broker(((Number) paid.get("revenue")).longValue(), qualified, ((Number) paid.get("payers")).longValue(),
                ((Number) paid.get("returning")).longValue());
    }

    /** Visitor-day filters on {@code analytics_visitor_days v}. */
    private static Where visitorDays(Window window, Filters filters) {
        StringBuilder sql = new StringBuilder(" AND v.day >= ? AND v.day <= ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(window.firstDay()), Date.valueOf(window.lastDay())));
        if (filters.device() != null) {
            sql.append(" AND v.device = ?");
            args.add(filters.device());
        }
        if (filters.source() != null) {
            sql.append(" AND (CASE WHEN v.source = '' THEN '" + DIRECT + "' ELSE v.source END) = ?");
            args.add(filters.source());
        }
        return new Where(sql.toString(), args);
    }

    @Override
    public ReturnVisits returnVisits(Window window, Filters filters) {
        Where where = visitorDays(window, filters);
        return jdbc.queryForObject("""
                SELECT COUNT(*), COUNT(*) FILTER (WHERE days > 1) FROM (
                    SELECT v.anonymous_id, COUNT(*) AS days FROM analytics_visitor_days v WHERE TRUE %s GROUP BY v.anonymous_id) d
                """.formatted(where.sql()), (rs, i) -> new ReturnVisits(rs.getLong(1), rs.getLong(2)), where.with());
    }

    @Override
    public List<CohortCell> cohorts(Window window, Filters filters) {
        Where where = visitorDays(window, filters);
        // First-seen day over every retained visitor day before the window end, so a device first seen before the window
        // is not counted as new inside it.
        return jdbc.query("""
                WITH firsts AS (
                    SELECT v.anonymous_id, MIN(v.day) AS first_day FROM analytics_visitor_days v
                    WHERE v.day <= ? GROUP BY v.anonymous_id),
                active AS (
                    SELECT DISTINCT v.anonymous_id, f.first_day, (v.day - f.first_day) / 7 AS week_offset
                    FROM analytics_visitor_days v JOIN firsts f ON f.anonymous_id = v.anonymous_id
                    WHERE f.first_day >= ? %s)
                SELECT date_trunc('week', first_day)::date AS cohort_week, week_offset, COUNT(DISTINCT anonymous_id)
                FROM active GROUP BY 1, 2 ORDER BY 1, 2 LIMIT 200
                """.formatted(where.sql()),
                (rs, i) -> new CohortCell(rs.getDate(1).toLocalDate(), rs.getInt(2), rs.getLong(3)),
                concat(new Object[] {Date.valueOf(window.lastDay()), Date.valueOf(window.firstDay())}, where.with()));
    }

    @Override
    public List<WebVital> webVitals(Window window, Filters filters) {
        Where where = web(window, filters, false);
        return jdbc.query("""
                SELECT e.properties ->> 'metric' AS metric, COALESCE(e.device, '') AS device,
                       percentile_cont(0.75) WITHIN GROUP (ORDER BY (e.properties ->> 'value')::double precision), COUNT(*)
                FROM analytics_events e WHERE e.name = 'web_vital' %s
                GROUP BY 1, 2 ORDER BY 1, 2 LIMIT 40
                """.formatted(where.sql()), (rs, i) -> new WebVital(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getLong(4)),
                where.with());
    }

    @Override
    public List<Breakdown> breakdown(Window window, Filters filters, String dimension, int limit) {
        if (!DIMENSIONS.contains(dimension)) throw new IllegalArgumentException("Unknown dimension " + dimension);
        String column = switch (dimension) {
            case "source" -> "CASE WHEN m.source = '' THEN '" + DIRECT + "' ELSE m.source END";
            case "device" -> "m.device";
            default -> "m.area_code";
        };
        StringBuilder sql = new StringBuilder(" AND m.day >= ? AND m.day <= ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(window.firstDay()), Date.valueOf(window.lastDay())));
        if (filters.device() != null) {
            sql.append(" AND m.device = ?");
            args.add(filters.device());
        }
        if (filters.source() != null) {
            sql.append(" AND (CASE WHEN m.source = '' THEN '" + DIRECT + "' ELSE m.source END) = ?");
            args.add(filters.source());
        }
        if (filters.area() != null) {
            sql.append(" AND m.area_code = ?");
            args.add(filters.area());
        }
        args.add(limit);
        return jdbc.query("""
                SELECT %s AS key,
                       COALESCE(SUM(m.sessions) FILTER (WHERE m.name = 'search_performed'), 0) AS searches,
                       COALESCE(SUM(m.events) FILTER (WHERE m.name = 'listing_detail_viewed'), 0) AS details,
                       COALESCE(SUM(m.events) FILTER (WHERE m.name = 'lead_form_opened'), 0) AS forms
                FROM analytics_daily_metrics m
                WHERE m.name IN ('search_performed', 'listing_detail_viewed', 'lead_form_opened') %s
                GROUP BY 1 ORDER BY 2 DESC, 3 DESC, 1 LIMIT ?
                """.formatted(column, sql),
                (rs, i) -> new Breakdown(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)), args.toArray());
    }

    @Override
    public List<TrendDay> trend(Window window, Filters filters) {
        StringBuilder sql = new StringBuilder(" AND m.day >= ? AND m.day <= ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(window.firstDay()), Date.valueOf(window.lastDay())));
        if (filters.device() != null) {
            sql.append(" AND m.device = ?");
            args.add(filters.device());
        }
        if (filters.source() != null) {
            sql.append(" AND (CASE WHEN m.source = '' THEN '" + DIRECT + "' ELSE m.source END) = ?");
            args.add(filters.source());
        }
        if (filters.area() != null) {
            sql.append(" AND m.area_code = ?");
            args.add(filters.area());
        }
        return jdbc.query("""
                SELECT m.day,
                       COALESCE(SUM(m.events) FILTER (WHERE m.name = 'search_performed'), 0),
                       COALESCE(SUM(m.events) FILTER (WHERE m.name = 'listing_detail_viewed'), 0),
                       COALESCE(SUM(m.events) FILTER (WHERE m.name = 'lead_form_opened'), 0),
                       COALESCE(SUM(m.events) FILTER (WHERE m.name = 'lead_submitted'), 0)
                FROM analytics_daily_metrics m
                WHERE m.name IN ('search_performed', 'listing_detail_viewed', 'lead_form_opened', 'lead_submitted') %s
                GROUP BY m.day ORDER BY m.day LIMIT 100
                """.formatted(sql),
                (rs, i) -> new TrendDay(rs.getDate(1).toLocalDate(), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)),
                args.toArray());
    }

    @Override
    public Freshness freshness(Instant now) {
        Timestamp latest = jdbc.queryForObject(
                "SELECT MAX(occurred_at) FROM analytics_events WHERE origin = 'web' AND occurred_at >= ?",
                Timestamp.class, Timestamp.from(now.minus(Duration.ofDays(2))));
        Timestamp computed = jdbc.queryForObject("SELECT MAX(computed_at) FROM analytics_daily_metrics WHERE day >= ?",
                Timestamp.class, Date.valueOf(java.time.LocalDate.ofInstant(now, java.time.ZoneOffset.UTC).minusDays(3)));
        return new Freshness(latest == null ? null : latest.toInstant(), computed == null ? null : computed.toInstant());
    }

    private static Double doubleOrNull(ResultSet rs, int column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Object[] concat(Object[] first, Object[] second) {
        Object[] all = new Object[first.length + second.length];
        System.arraycopy(first, 0, all, 0, first.length);
        System.arraycopy(second, 0, all, first.length, second.length);
        return all;
    }
}

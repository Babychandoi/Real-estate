package com.company.bds.moderation.application.service;

import com.company.bds.shared.error.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Read side of the moderation queue (F08.2): server paging with a stable order (oldest submission first, listing id
 * as tie breaker), bounded size, filters for first submission vs edit, SLA breach, open duplicates and claims.
 */
@Service
public class ModerationQueueService {
    public enum Filter { ALL, FIRST_SUBMISSION, EDIT, SLA_BREACH, DUPLICATES, MINE, UNCLAIMED }

    /** The newest submitted revision of each listing that waits for a decision. */
    private static final String PENDING = """
            FROM listing_revisions r
            JOIN listings l ON l.id = r.listing_id
            LEFT JOIN moderation_claims c ON c.listing_id = l.id AND c.expires_at > ?
            WHERE r.status = 'SUBMITTED'
              AND NOT EXISTS (SELECT 1 FROM listing_revisions n WHERE n.listing_id = r.listing_id AND n.status = 'SUBMITTED'
                              AND n.revision_number > r.revision_number)
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ModerationQueueService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public QueuePage page(String filterValue, int page, int size, UUID actorId) {
        Filter filter = parseFilter(filterValue);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(ModerationPolicy.MAX_PAGE_SIZE, Math.max(1, size));
        Instant now = clock.instant();
        Timestamp nowTs = Timestamp.from(now);
        Timestamp slaCutoff = Timestamp.from(now.minus(ModerationPolicy.SLA));
        List<Object> args = new ArrayList<>(List.of(nowTs));
        String where = switch (filter) {
            case ALL -> "";
            case FIRST_SUBMISSION -> " AND l.public_revision_id IS NULL";
            case EDIT -> " AND l.public_revision_id IS NOT NULL";
            case SLA_BREACH -> { args.add(slaCutoff); yield " AND r.submitted_at < ?"; }
            case DUPLICATES -> " AND EXISTS (SELECT 1 FROM listing_duplicate_candidates d WHERE d.status = 'OPEN'"
                    + " AND (d.listing_id = l.id OR d.candidate_listing_id = l.id))";
            case MINE -> { args.add(actorId); yield " AND c.moderator_id = ?"; }
            case UNCLAIMED -> " AND c.moderator_id IS NULL";
        };
        Long total = jdbc.queryForObject("SELECT count(*) " + PENDING + where, Long.class, args.toArray());
        List<Object> dataArgs = new ArrayList<>();
        dataArgs.add(slaCutoff);
        dataArgs.addAll(args);
        dataArgs.add(safeSize);
        dataArgs.add(safePage * safeSize);
        List<QueueItem> items = jdbc.query("""
                SELECT l.id AS listing_id, r.id AS revision_id, r.revision_number, r.title, l.owner_id, u.full_name AS owner_name,
                       r.price_vnd, r.area_m2, r.purpose, r.property_type, r.address_summary, r.district_code, r.submitted_at,
                       l.public_revision_id IS NULL AS first_submission, l.status AS listing_status,
                       (SELECT count(*) FROM listing_media m WHERE m.revision_id = r.id) AS media_count,
                       (SELECT count(*) FROM listing_duplicate_candidates d WHERE d.status = 'OPEN'
                          AND (d.listing_id = l.id OR d.candidate_listing_id = l.id)) AS open_duplicates,
                       c.moderator_id AS claim_moderator, cu.full_name AS claim_moderator_name, c.expires_at AS claim_expires,
                       r.submitted_at < ? AS sla_breached
                """ + PENDING.replace("WHERE r.status", "JOIN users u ON u.id = l.owner_id LEFT JOIN users cu ON cu.id = c.moderator_id WHERE r.status")
                + where + " ORDER BY r.submitted_at ASC NULLS LAST, l.id ASC LIMIT ? OFFSET ?",
                (rs, n) -> {
                    Instant submitted = rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant();
                    UUID claimer = rs.getObject("claim_moderator", UUID.class);
                    return new QueueItem(rs.getObject("listing_id", UUID.class), rs.getObject("revision_id", UUID.class),
                            rs.getInt("revision_number"), rs.getString("title"), rs.getObject("owner_id", UUID.class),
                            rs.getString("owner_name"), rs.getLong("price_vnd"), rs.getBigDecimal("area_m2"),
                            rs.getString("purpose"), rs.getString("property_type"), rs.getString("address_summary"),
                            rs.getString("district_code"), submitted,
                            submitted == null ? null : Duration.between(submitted, now).toMinutes(),
                            submitted == null ? null : submitted.plus(ModerationPolicy.SLA), rs.getBoolean("sla_breached"),
                            rs.getBoolean("first_submission") ? "FIRST_SUBMISSION" : "EDIT", rs.getString("listing_status"),
                            rs.getInt("media_count"), rs.getInt("open_duplicates"),
                            claimer == null ? null : new ClaimView(claimer, rs.getString("claim_moderator_name"),
                                    rs.getTimestamp("claim_expires").toInstant(), claimer.equals(actorId)));
                }, dataArgs.toArray());
        return new QueuePage(items, safePage, safeSize, total == null ? 0 : total, stats(now));
    }

    /** Queue size, SLA breaches and the oldest waiting submission (also exported as metrics). */
    @Transactional(readOnly = true)
    public QueueStats stats(Instant now) {
        return jdbc.queryForObject("SELECT count(*), count(*) FILTER (WHERE r.submitted_at < ?), min(r.submitted_at) " + PENDING,
                (rs, n) -> new QueueStats(rs.getLong(1), rs.getLong(2),
                        rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant()),
                Timestamp.from(now.minus(ModerationPolicy.SLA)), Timestamp.from(now));
    }

    private static Filter parseFilter(String value) {
        if (value == null || value.isBlank()) return Filter.ALL;
        try {
            return Filter.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_FILTER", "Bộ lọc hàng đợi không hợp lệ.");
        }
    }

    public record ClaimView(UUID moderatorId, String moderatorName, Instant expiresAt, boolean mine) {}

    public record QueueItem(UUID listingId, UUID revisionId, int revisionNumber, String title, UUID ownerId, String ownerName,
                            long priceVnd, BigDecimal areaM2, String purpose, String propertyType, String addressSummary,
                            String districtCode, Instant submittedAt, Long ageMinutes, Instant slaDueAt, boolean slaBreached,
                            String kind, String listingStatus, int mediaCount, int openDuplicates, ClaimView claim) {}

    public record QueueStats(long total, long slaBreached, Instant oldestSubmittedAt) {}

    public record QueuePage(List<QueueItem> items, int page, int size, long total, QueueStats stats) {}
}

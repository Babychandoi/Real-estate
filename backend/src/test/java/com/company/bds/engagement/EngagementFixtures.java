package com.company.bds.engagement;

import com.company.bds.engagement.infrastructure.ListingChangeJobHandler;
import com.company.bds.shared.jobs.JobWorker;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Listing state changes the way the write path makes them (public revision swap, status change), plus job draining. */
final class EngagementFixtures {
    private final JdbcTemplate jdbc;
    private final JobWorker worker;

    EngagementFixtures(JdbcTemplate jdbc, JobWorker worker) {
        this.jdbc = jdbc;
        this.worker = worker;
    }

    /** A new APPROVED public revision with another price (copying the current one), as an approved edit does. */
    void changePrice(UUID listingId, long priceVnd) {
        UUID revision = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO listing_revisions(id,listing_id,revision_number,status,title,purpose,property_type,price_vnd,area_m2,
                    description,province_code,district_code,address_summary,public_latitude,public_longitude,created_at,
                    submitted_at,moderated_at,bedrooms,legal_status)
                SELECT ?, listing_id, (SELECT max(revision_number) + 1 FROM listing_revisions WHERE listing_id = r.listing_id),
                       'APPROVED', title, purpose, property_type, ?, area_m2, description, province_code, district_code,
                       address_summary, public_latitude, public_longitude, now(), now(), now(), bedrooms, legal_status
                  FROM listing_revisions r WHERE r.id = (SELECT public_revision_id FROM listings WHERE id = ?)""",
                revision, priceVnd, listingId);
        jdbc.update("UPDATE listings SET public_revision_id = ?, updated_at = ? WHERE id = ?", revision,
                Timestamp.from(Instant.now()), listingId);
    }

    void setStatus(UUID listingId, String status) {
        jdbc.update("UPDATE listings SET status = ?, updated_at = now() WHERE id = ?", status, listingId);
    }

    /** Runs the alert matcher for everything pending (the worker is disabled in tests). */
    void drainChanges() {
        worker.drain(ListingChangeJobHandler.QUEUE);
    }

    int pendingMatches(UUID savedSearchId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM saved_search_matches WHERE saved_search_id = ? AND delivered_at IS NULL",
                Integer.class, savedSearchId);
        return n == null ? 0 : n;
    }

    int notifications(UUID userId, String type) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM user_notifications WHERE user_id = ? AND type = ?", Integer.class,
                userId, type);
        return n == null ? 0 : n;
    }
}

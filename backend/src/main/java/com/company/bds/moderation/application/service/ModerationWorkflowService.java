package com.company.bds.moderation.application.service;

import com.company.bds.analytics.application.AnalyticsRecorder;
import com.company.bds.asset.PropertyAssetService;
import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.exception.ListingDomainException;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.listing.domain.model.ListingRevision;
import com.company.bds.listing.domain.model.RevisionStatus;
import com.company.bds.moderation.domain.model.ApprovalReason;
import com.company.bds.moderation.domain.model.StandardModerationReason;
import com.company.bds.shared.error.ApiException;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Claims and decisions of moderation v2. Every decision is stored in {@code moderation_decisions} with the moderator who
 * took it (from the session, never a constant), a reason code and an optional note. A revision can be decided once
 * (unique index), so two moderators racing on one item produce exactly one effect.
 */
@Service
public class ModerationWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(ModerationWorkflowService.class);
    private final JdbcTemplate jdbc;
    private final ListingPersistencePort listings;
    private final PropertyAssetService assets;
    private final AnalyticsRecorder analytics;
    private final EntityManager entityManager;
    private final Clock clock;
    private final TransactionTemplate perItem;

    public ModerationWorkflowService(JdbcTemplate jdbc, ListingPersistencePort listings, PropertyAssetService assets,
                                     AnalyticsRecorder analytics, EntityManager entityManager, Clock clock,
                                     PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.listings = listings;
        this.assets = assets;
        this.analytics = analytics;
        this.entityManager = entityManager;
        this.clock = clock;
        this.perItem = new TransactionTemplate(transactionManager);
        this.perItem.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---------------------------------------------------------------------------------------------------- claims

    /** Takes (or renews) the claim on a pending listing for 30 minutes; 409 CLAIM_CONFLICT while someone else holds it. */
    @Transactional
    public ModerationQueueService.ClaimView claim(UUID listingId, UUID actorId) {
        UUID revisionId = pendingRevision(listingId)
                .orElseThrow(() -> ApiException.conflict("NOT_PENDING", "Tin này không còn chờ kiểm duyệt."));
        Instant now = clock.instant();
        Instant expires = now.plus(ModerationPolicy.CLAIM_TTL);
        List<UUID> holder = jdbc.query("""
                INSERT INTO moderation_claims(listing_id, revision_id, moderator_id, claimed_at, expires_at) VALUES (?,?,?,?,?)
                ON CONFLICT (listing_id) DO UPDATE SET revision_id = EXCLUDED.revision_id, moderator_id = EXCLUDED.moderator_id,
                    claimed_at = EXCLUDED.claimed_at, expires_at = EXCLUDED.expires_at
                WHERE moderation_claims.expires_at <= EXCLUDED.claimed_at OR moderation_claims.moderator_id = EXCLUDED.moderator_id
                RETURNING moderator_id
                """, (rs, n) -> rs.getObject(1, UUID.class), listingId, revisionId, actorId, Timestamp.from(now), Timestamp.from(expires));
        if (holder.isEmpty()) throw claimConflict(listingId);
        String name = jdbc.queryForObject("SELECT full_name FROM users WHERE id = ?", String.class, actorId);
        return new ModerationQueueService.ClaimView(actorId, name, expires, true);
    }

    /** Releases the actor's own claim (no-op when there is none). */
    @Transactional
    public void release(UUID listingId, UUID actorId) {
        jdbc.update("DELETE FROM moderation_claims WHERE listing_id = ? AND moderator_id = ?", listingId, actorId);
    }

    // ---------------------------------------------------------------------------------------------------- decisions

    @Transactional
    public Decision approve(UUID listingId, UUID revisionId, UUID actorId, String reasonCode, String note) {
        if (!ApprovalReason.isValid(reasonCode)) {
            throw ApiException.badRequest("INVALID_REASON", "Cần chọn lý do phê duyệt hợp lệ.");
        }
        return decide(listingId, revisionId, actorId, "APPROVED", reasonCode, note, null, false);
    }

    @Transactional
    public Decision reject(UUID listingId, UUID revisionId, UUID actorId, String reasonCode, String detail) {
        if (Arrays.stream(StandardModerationReason.values()).noneMatch(r -> r.name().equals(reasonCode))) {
            throw ApiException.badRequest("INVALID_REASON", "Cần chọn lý do từ chối trong danh mục chuẩn.");
        }
        return decide(listingId, revisionId, actorId, "REJECTED", reasonCode, detail, null, false);
    }

    /**
     * Bulk approve/reject of at most 50 items, each in its own transaction, and only for items the actor holds a live
     * claim on; the response lists the outcome of every requested item (nothing outside the stated scope is touched).
     */
    public BulkResult bulk(String action, List<BulkItem> items, UUID actorId, String reasonCode, String note) {
        if (items == null || items.isEmpty()) throw ApiException.badRequest("EMPTY_BULK", "Chưa chọn tin nào.");
        if (items.size() > ModerationPolicy.BULK_MAX) {
            throw ApiException.badRequest("BULK_TOO_LARGE", "Mỗi lần chỉ xử lý tối đa " + ModerationPolicy.BULK_MAX + " tin.");
        }
        boolean approve = "APPROVE".equals(action);
        if (!approve && !"REJECT".equals(action)) throw ApiException.badRequest("INVALID_ACTION", "Thao tác hàng loạt không hợp lệ.");
        boolean validReason = approve ? ApprovalReason.isValid(reasonCode)
                : Arrays.stream(StandardModerationReason.values()).anyMatch(r -> r.name().equals(reasonCode));
        if (!validReason) throw ApiException.badRequest("INVALID_REASON", "Cần chọn lý do trong danh mục chuẩn.");
        UUID batchId = UUID.randomUUID();
        Set<UUID> seen = new HashSet<>();
        List<BulkItemResult> results = new ArrayList<>();
        for (BulkItem item : items) {
            if (item == null || item.listingId() == null || item.revisionId() == null || !seen.add(item.listingId())) {
                results.add(new BulkItemResult(item == null ? null : item.listingId(), item == null ? null : item.revisionId(),
                        "INVALID", "Mục trùng lặp hoặc thiếu mã tin."));
                continue;
            }
            try {
                Decision decision = perItem.execute(status -> decide(item.listingId(), item.revisionId(), actorId,
                        approve ? "APPROVED" : "REJECTED", reasonCode, note, batchId, true));
                results.add(new BulkItemResult(item.listingId(), item.revisionId(), decision.decision(), null));
            } catch (ApiException ex) {
                results.add(new BulkItemResult(item.listingId(), item.revisionId(), ex.code(), ex.getMessage()));
            } catch (ListingDomainException ex) {
                results.add(new BulkItemResult(item.listingId(), item.revisionId(), "STALE_REVISION", ex.getMessage()));
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
                results.add(new BulkItemResult(item.listingId(), item.revisionId(), "ALREADY_DECIDED",
                        "Tin vừa được xử lý bởi thao tác khác."));
            }
        }
        return new BulkResult(batchId, results);
    }

    @Transactional(readOnly = true)
    public List<DecisionView> history(UUID listingId) {
        return jdbc.query("""
                SELECT d.id, d.revision_id, r.revision_number, d.decision, d.reason_code, d.note, d.moderator_id, u.full_name,
                       d.bulk_batch_id, d.created_at
                FROM moderation_decisions d JOIN listing_revisions r ON r.id = d.revision_id
                LEFT JOIN users u ON u.id = d.moderator_id
                WHERE d.listing_id = ? ORDER BY d.created_at DESC, d.id DESC LIMIT 100
                """, (rs, n) -> new DecisionView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7, UUID.class), rs.getString(8),
                rs.getObject(9, UUID.class), rs.getTimestamp(10).toInstant()), listingId);
    }

    private Decision decide(UUID listingId, UUID revisionId, UUID actorId, String decision, String reasonCode, String note,
                            UUID batchId, boolean requireOwnClaim) {
        Instant now = clock.instant();
        List<UUID> claimHolder = jdbc.query("SELECT moderator_id FROM moderation_claims WHERE listing_id = ? AND expires_at > ? FOR UPDATE",
                (rs, n) -> rs.getObject(1, UUID.class), listingId, Timestamp.from(now));
        if (!claimHolder.isEmpty() && !claimHolder.get(0).equals(actorId)) throw claimConflict(listingId);
        if (requireOwnClaim && claimHolder.isEmpty()) {
            throw ApiException.conflict("NOT_CLAIMED", "Cần nhận xử lý tin trước khi duyệt hàng loạt.");
        }
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "Không tìm thấy tin đăng."));
        ListingRevision current = latestSubmitted(listing)
                .orElseThrow(() -> ApiException.conflict("NOT_PENDING", "Tin này không còn chờ kiểm duyệt."));
        if (!current.getId().equals(revisionId)) {
            throw ApiException.conflict("STALE_REVISION", "Người đăng đã gửi phiên bản mới hơn; hãy tải lại trước khi quyết định.");
        }
        String trimmedNote = note == null || note.isBlank() ? null : note.trim();
        if (trimmedNote != null && trimmedNote.length() > 1000) {
            throw ApiException.badRequest("NOTE_TOO_LONG", "Ghi chú tối đa 1000 ký tự.");
        }
        UUID decisionId = UUID.randomUUID();
        // The decision row first: its unique index makes a concurrent second decision fail before any listing change.
        try {
            jdbc.update("""
                    INSERT INTO moderation_decisions(id, listing_id, revision_id, moderator_id, decision, reason_code, note, bulk_batch_id, created_at)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, decisionId, listingId, revisionId, actorId, decision, reasonCode, trimmedNote, batchId, Timestamp.from(now));
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("ALREADY_DECIDED", "Phiên bản này vừa được kiểm duyệt viên khác xử lý.");
        }
        if ("APPROVED".equals(decision)) {
            listing.approveRevision(revisionId, now);
        } else {
            String reason = trimmedNote == null ? reasonCode : reasonCode + ": " + trimmedNote;
            listing.rejectRevision(revisionId, reason, now);
        }
        Listing saved = listings.save(listing);
        entityManager.flush();
        jdbc.update("DELETE FROM moderation_claims WHERE listing_id = ?", listingId);
        if ("APPROVED".equals(decision)) {
            // After commit, in its own transaction: duplicate bookkeeping never blocks or rolls back a decision.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try {
                        perItem.executeWithoutResult(s -> assets.linkAssetOnApproval(listingId, revisionId));
                    } catch (RuntimeException ex) {
                        log.warn("asset_link_failed listing={} error={}", listingId, ex.getClass().getSimpleName());
                    }
                }
            });
            saved.getPublicRevision().ifPresent(revision -> analytics.recordServer("listing_published", 1,
                    saved.getId() + ":" + revision.getRevisionNumber(), saved.getOwnerId(), saved.getId(),
                    Map.of("revisionNumber", revision.getRevisionNumber())));
        }
        return new Decision(decisionId, listingId, revisionId, decision, reasonCode, saved.getStatus().name(), actorId);
    }

    private Optional<UUID> pendingRevision(UUID listingId) {
        return jdbc.query("""
                SELECT id FROM listing_revisions WHERE listing_id = ? AND status = 'SUBMITTED'
                ORDER BY revision_number DESC LIMIT 1
                """, (rs, n) -> rs.getObject(1, UUID.class), listingId).stream().findFirst();
    }

    private static Optional<ListingRevision> latestSubmitted(Listing listing) {
        return listing.getRevisions().stream().filter(r -> r.getStatus() == RevisionStatus.SUBMITTED)
                .max(Comparator.comparingInt(ListingRevision::getRevisionNumber));
    }

    private ApiException claimConflict(UUID listingId) {
        String holder = jdbc.query("""
                SELECT u.full_name FROM moderation_claims c JOIN users u ON u.id = c.moderator_id WHERE c.listing_id = ?
                """, (rs, n) -> rs.getString(1), listingId).stream().findFirst().orElse("kiểm duyệt viên khác");
        return ApiException.conflict("CLAIM_CONFLICT", "Tin đang được " + holder + " xử lý.");
    }

    public record Decision(UUID id, UUID listingId, UUID revisionId, String decision, String reasonCode, String listingStatus,
                           UUID moderatorId) {}

    public record BulkItem(UUID listingId, UUID revisionId) {}

    public record BulkItemResult(UUID listingId, UUID revisionId, String outcome, String message) {}

    public record BulkResult(UUID batchId, List<BulkItemResult> results) {}

    public record DecisionView(UUID id, UUID revisionId, int revisionNumber, String decision, String reasonCode, String note,
                               UUID moderatorId, String moderatorName, UUID bulkBatchId, Instant createdAt) {}
}

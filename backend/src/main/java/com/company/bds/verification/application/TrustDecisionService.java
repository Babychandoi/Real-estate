package com.company.bds.verification.application;

import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.shared.error.ApiException;
import com.company.bds.verification.domain.model.TrustReason;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trust decisions (contract §6, P-04): identity (KYC) and ownership (listing verification) approvals, rejections and
 * revocations with a reason code, the deciding staff member and validity (ownership 180 days, identity 24 months).
 * Each decision appends to {@code trust_decisions}; a decision applies only from the expected state (row lock), so a
 * concurrent second decision gets 409 instead of overwriting the first.
 */
@Service
public class TrustDecisionService {
    public static final Period IDENTITY_VALIDITY = Period.ofMonths(24);
    public static final Period OWNERSHIP_VALIDITY = Period.ofDays(180);

    private final JdbcTemplate jdbc;
    private final ListingPersistencePort listings;
    private final EntityManager entityManager;
    private final RealtimeNotificationService notifications;
    private final Clock clock;

    public TrustDecisionService(JdbcTemplate jdbc, ListingPersistencePort listings, EntityManager entityManager,
                                RealtimeNotificationService notifications, Clock clock) {
        this.jdbc = jdbc;
        this.listings = listings;
        this.entityManager = entityManager;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------------ identity

    @Transactional
    public void approveKyc(UUID kycId, UUID actorId, String reasonCode, String note) {
        TrustReason reason = reason(reasonCode, TrustReason.Kind.APPROVE);
        UUID userId = lockKyc(kycId, "PENDING");
        notOwn(userId, actorId);
        Instant now = clock.instant();
        Instant expires = now.atOffset(ZoneOffset.UTC).plus(IDENTITY_VALIDITY).toInstant();
        jdbc.update("""
                UPDATE user_kyc_profiles SET status = 'VERIFIED', verified_at = ?, expires_at = ?, decided_by = ?,
                    decision_reason_code = ?, rejection_reason = NULL, revoked_at = NULL WHERE id = ?
                """, ts(now), ts(expires), actorId, reason.name(), kycId);
        record("KYC", kycId, userId, null, "APPROVED", reason, note, expires, actorId, now);
        notifyUser(userId, "KYC_APPROVED", "Đã xác minh danh tính",
                "Danh tính của bạn đã được xác minh, hiệu lực đến " + date(expires) + ". Xác minh danh tính không phải xác nhận quyền sở hữu.");
    }

    @Transactional
    public void rejectKyc(UUID kycId, UUID actorId, String reasonCode, String note) {
        TrustReason reason = reason(reasonCode, TrustReason.Kind.REJECT);
        UUID userId = lockKyc(kycId, "PENDING");
        notOwn(userId, actorId);
        Instant now = clock.instant();
        String shown = shown(reason, note);
        jdbc.update("""
                UPDATE user_kyc_profiles SET status = 'REJECTED', verified_at = ?, decided_by = ?, decision_reason_code = ?,
                    rejection_reason = ? WHERE id = ?
                """, ts(now), actorId, reason.name(), shown, kycId);
        record("KYC", kycId, userId, null, "REJECTED", reason, note, null, actorId, now);
        notifyUser(userId, "KYC_REJECTED", "Hồ sơ định danh cần bổ sung", shown + ". Bạn có thể gửi lại hồ sơ.");
    }

    /** Identity has no REVOKED status in the contract: a revoked identity becomes REJECTED with revoked_at. */
    @Transactional
    public void revokeKyc(UUID kycId, UUID actorId, String reasonCode, String note) {
        TrustReason reason = required(reasonCode, TrustReason.Kind.REVOKE);
        UUID userId = lockKyc(kycId, "VERIFIED");
        notOwn(userId, actorId);
        Instant now = clock.instant();
        String shown = shown(reason, note);
        jdbc.update("""
                UPDATE user_kyc_profiles SET status = 'REJECTED', revoked_at = ?, decided_by = ?, decision_reason_code = ?,
                    rejection_reason = ? WHERE id = ?
                """, ts(now), actorId, reason.name(), shown, kycId);
        record("KYC", kycId, userId, null, "REVOKED", reason, note, null, actorId, now);
        int cascaded = cascadeOwnershipOfRevokedKyc(kycId, actorId, reason, now);
        notifyUser(userId, "KYC_REVOKED", "Xác minh danh tính đã bị thu hồi",
                cascaded == 0 ? shown : shown + ". Đối chiếu giấy tờ của " + cascaded + " tin dựa trên danh tính này cũng đã bị thu hồi.");
    }

    /**
     * Ownership checks rest on the identity they were approved with: revoking that identity revokes each of its
     * VERIFIED_OWNER checks (same reason code, history entry "KYC_REVOKED" note) and turns each listing's badge off unless
     * another valid check still backs it. Pending checks stay pending: they cannot be approved without a valid identity.
     * Expiry of an identity does not cascade (each ownership check keeps its own 180-day validity).
     */
    private int cascadeOwnershipOfRevokedKyc(UUID kycId, UUID actorId, TrustReason reason, Instant now) {
        List<UUID[]> rows = jdbc.query("""
                SELECT id, listing_id FROM listing_verifications WHERE user_kyc_id = ? AND status = 'VERIFIED_OWNER' ORDER BY id FOR UPDATE
                """, (rs, n) -> new UUID[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)}, kycId);
        String note = "Danh tính người đăng đã bị thu hồi";
        for (UUID[] row : rows) {
            jdbc.update("""
                    UPDATE listing_verifications SET status = 'REVOKED', revoked_at = ?, decided_by = ?, decision_reason_code = ?,
                        verifier_note = ? WHERE id = ?
                    """, ts(now), actorId, reason.name(), reason.getVietnameseLabel() + ": " + note, row[0]);
            refreshListingFlag(row[1], now);
            record("OWNERSHIP", row[0], null, row[1], "REVOKED", reason, note, null, actorId, now);
        }
        return rows.size();
    }

    /** The listing badge stays on only while some ownership check of it is VERIFIED_OWNER, not revoked and not expired. */
    public void refreshListingFlag(UUID listingId, Instant now) {
        Integer valid = jdbc.queryForObject("""
                SELECT count(*) FROM listing_verifications WHERE listing_id = ? AND status = 'VERIFIED_OWNER'
                  AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > ?)
                """, Integer.class, listingId, ts(now));
        setListingVerified(listingId, valid != null && valid > 0, now);
    }

    // ------------------------------------------------------------------------------------------------ ownership

    @Transactional
    public void approveOwnership(UUID verificationId, UUID actorId, String reasonCode, String note) {
        TrustReason reason = reason(reasonCode, TrustReason.Kind.APPROVE);
        Ownership v = lockOwnership(verificationId, "PENDING");
        notOwn(v.ownerId(), actorId);
        Instant now = clock.instant();
        // Contract §6: identity counts only while VERIFIED, not expired and not revoked (an expired identity keeps
        // status VERIFIED with a past expires_at). Locked so a concurrent identity revocation cannot slip in between.
        Integer validKyc = jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT 1 FROM user_kyc_profiles WHERE id = ? AND status = 'VERIFIED' AND revoked_at IS NULL
                    AND (expires_at IS NULL OR expires_at > ?) FOR SHARE) k
                """, Integer.class, v.kycId(), ts(now));
        if (validKyc == null || validKyc == 0) {
            throw ApiException.conflict("KYC_NOT_VERIFIED", "Không thể duyệt quyền sở hữu khi danh tính người đăng chưa được xác minh hoặc đã hết hạn/bị thu hồi.");
        }
        Instant expires = now.plus(java.time.Duration.ofDays(OWNERSHIP_VALIDITY.getDays()));
        jdbc.update("""
                UPDATE listing_verifications SET status = 'VERIFIED_OWNER', verified_at = ?, expires_at = ?, decided_by = ?,
                    decision_reason_code = ?, verifier_note = ?, revoked_at = NULL WHERE id = ?
                """, ts(now), ts(expires), actorId, reason.name(), note == null || note.isBlank() ? reason.getVietnameseLabel() : note.trim(), verificationId);
        setListingVerified(v.listingId(), true, now);
        record("OWNERSHIP", verificationId, null, v.listingId(), "APPROVED", reason, note, expires, actorId, now);
        if (v.ownerId() != null) {
            notifications.notify(v.ownerId(), "OWNERSHIP_VERIFIED", "Đã đối chiếu giấy tờ chủ sở hữu",
                    "Tin \"" + v.title() + "\" đã được đối chiếu giấy tờ, hiệu lực đến " + date(expires) + ".");
        }
    }

    @Transactional
    public void rejectOwnership(UUID verificationId, UUID actorId, String reasonCode, String note) {
        TrustReason reason = reason(reasonCode, TrustReason.Kind.REJECT);
        Ownership v = lockOwnership(verificationId, "PENDING");
        notOwn(v.ownerId(), actorId);
        Instant now = clock.instant();
        String shown = shown(reason, note);
        jdbc.update("""
                UPDATE listing_verifications SET status = 'REJECTED', verified_at = ?, decided_by = ?, decision_reason_code = ?,
                    verifier_note = ? WHERE id = ?
                """, ts(now), actorId, reason.name(), shown, verificationId);
        record("OWNERSHIP", verificationId, null, v.listingId(), "REJECTED", reason, note, null, actorId, now);
        if (v.ownerId() != null) {
            notifications.notify(v.ownerId(), "OWNERSHIP_REJECTED", "Hồ sơ giấy tờ cần bổ sung", "Tin \"" + v.title() + "\": " + shown);
        }
    }

    @Transactional
    public void revokeOwnership(UUID verificationId, UUID actorId, String reasonCode, String note) {
        TrustReason reason = required(reasonCode, TrustReason.Kind.REVOKE);
        Ownership v = lockOwnership(verificationId, "VERIFIED_OWNER");
        notOwn(v.ownerId(), actorId);
        Instant now = clock.instant();
        String shown = shown(reason, note);
        jdbc.update("""
                UPDATE listing_verifications SET status = 'REVOKED', revoked_at = ?, decided_by = ?, decision_reason_code = ?,
                    verifier_note = ? WHERE id = ?
                """, ts(now), actorId, reason.name(), shown, verificationId);
        refreshListingFlag(v.listingId(), now); // another valid check of the same listing keeps the badge
        record("OWNERSHIP", verificationId, null, v.listingId(), "REVOKED", reason, note, null, actorId, now);
        if (v.ownerId() != null) {
            notifications.notify(v.ownerId(), "OWNERSHIP_REVOKED", "Đối chiếu giấy tờ đã bị thu hồi", "Tin \"" + v.title() + "\": " + shown);
        }
    }

    // ------------------------------------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<Decision> history(String subjectType, UUID subjectId, boolean includeActor) {
        return jdbc.query("""
                SELECT d.id, d.decision, d.reason_code, d.note, d.expires_at, d.actor_id, u.full_name, d.created_at
                FROM trust_decisions d LEFT JOIN users u ON u.id = d.actor_id
                WHERE d.subject_type = ? AND d.subject_id = ? ORDER BY d.created_at DESC, d.id DESC LIMIT 50
                """, (rs, n) -> {
            String code = rs.getString(3);
            String label = labelOf(code);
            return new Decision(rs.getObject(1, UUID.class), rs.getString(2), code, label, includeActor ? rs.getString(4) : null,
                    instant(rs, 5), includeActor ? rs.getObject(6, UUID.class) : null, includeActor ? rs.getString(7) : null, instant(rs, 8));
        }, subjectType, subjectId);
    }

    /** Validity and decider columns that the JPA model does not map, for a set of verifications (one query). */
    @Transactional(readOnly = true)
    public Map<UUID, OwnershipExtras> ownershipExtras(Collection<UUID> verificationIds) {
        Map<UUID, OwnershipExtras> result = new HashMap<>();
        if (verificationIds.isEmpty()) return result;
        String placeholders = String.join(",", verificationIds.stream().map(id -> "?").toList());
        jdbc.query("""
                SELECT v.id, v.expires_at, v.revoked_at, v.decided_by, du.full_name, v.decision_reason_code, r.title, r.address_summary,
                       l.status, l.owner_id
                FROM listing_verifications v
                LEFT JOIN listings l ON l.id = v.listing_id
                LEFT JOIN listing_revisions r ON r.id = COALESCE(l.public_revision_id,
                    (SELECT x.id FROM listing_revisions x WHERE x.listing_id = l.id ORDER BY x.revision_number DESC LIMIT 1))
                LEFT JOIN users du ON du.id = v.decided_by
                WHERE v.id IN (%s)
                """.formatted(placeholders), rs -> {
            result.put(rs.getObject(1, UUID.class), new OwnershipExtras(instant(rs, 2), instant(rs, 3), rs.getObject(4, UUID.class),
                    rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getObject(10, UUID.class)));
        }, verificationIds.toArray());
        return result;
    }

    public static String labelOf(String code) {
        if (code == null || "LEGACY".equals(code)) return "Quyết định trước khi có mã lý do";
        for (TrustReason r : TrustReason.values()) if (r.name().equals(code)) return r.getVietnameseLabel();
        return code;
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private UUID lockKyc(UUID kycId, String expectedStatus) {
        List<String[]> rows = jdbc.query("SELECT user_id, status FROM user_kyc_profiles WHERE id = ? FOR UPDATE",
                (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, kycId);
        if (rows.isEmpty()) throw ApiException.notFound("KYC_NOT_FOUND", "Không tìm thấy hồ sơ định danh.");
        if (!expectedStatus.equals(rows.get(0)[1])) {
            throw ApiException.conflict("INVALID_TRUST_STATE", "Hồ sơ đang ở trạng thái " + rows.get(0)[1] + ", không thể thực hiện quyết định này.");
        }
        return UUID.fromString(rows.get(0)[0]);
    }

    private Ownership lockOwnership(UUID verificationId, String expectedStatus) {
        List<Ownership> rows = jdbc.query("""
                SELECT v.listing_id, v.user_kyc_id, v.status, l.owner_id, r.title
                FROM listing_verifications v
                LEFT JOIN listings l ON l.id = v.listing_id
                LEFT JOIN listing_revisions r ON r.id = COALESCE(l.public_revision_id,
                    (SELECT x.id FROM listing_revisions x WHERE x.listing_id = l.id ORDER BY x.revision_number DESC LIMIT 1))
                WHERE v.id = ? FOR UPDATE OF v
                """, (rs, n) -> new Ownership(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getObject(4, UUID.class), rs.getString(5) == null ? "(không rõ tiêu đề)" : rs.getString(5)), verificationId);
        if (rows.isEmpty()) throw ApiException.notFound("VERIFICATION_NOT_FOUND", "Không tìm thấy hồ sơ giấy tờ.");
        if (!expectedStatus.equals(rows.get(0).status())) {
            throw ApiException.conflict("INVALID_TRUST_STATE", "Hồ sơ đang ở trạng thái " + rows.get(0).status() + ", không thể thực hiện quyết định này.");
        }
        return rows.get(0);
    }

    public void setListingVerified(UUID listingId, boolean verified, Instant now) {
        listings.findById(listingId).ifPresent(listing -> {
            if (listing.isVerifiedOwner() != verified) {
                listing.markVerifiedOwner(verified, now);
                listings.save(listing);
                entityManager.flush();
            }
        });
    }

    public void record(String type, UUID subjectId, UUID userId, UUID listingId, String decision, TrustReason reason, String note,
                Instant expires, UUID actorId, Instant at) {
        jdbc.update("""
                INSERT INTO trust_decisions(id, subject_type, subject_id, user_id, listing_id, decision, reason_code, note, expires_at, actor_id, created_at)
                VALUES (?,?,?,(SELECT u.id FROM users u WHERE u.id = ?),?,?,?,?,?,?,?)
                """, UUID.randomUUID(), type, subjectId, userId, listingId, decision, reason == null ? "EXPIRED" : reason.name(),
                note == null || note.isBlank() ? null : note.trim(), expires == null ? null : ts(expires), actorId, ts(at));
    }

    /** In-app notice; skipped for identity profiles whose account no longer exists (no foreign key on user_kyc_profiles). */
    private void notifyUser(UUID userId, String type, String title, String message) {
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, userId);
        if (exists != null && exists > 0) notifications.notify(userId, type, title, message);
    }

    /** Nobody decides on their own identity or their own listing (four-eyes principle). */
    private static void notOwn(UUID subjectUserId, UUID actorId) {
        if (subjectUserId != null && subjectUserId.equals(actorId)) {
            throw ApiException.conflict("OWN_DECISION", "Không thể tự thẩm định hồ sơ của chính mình.");
        }
    }

    private static TrustReason reason(String code, TrustReason.Kind kind) {
        TrustReason reason = TrustReason.parse(code, kind);
        if (reason == null) throw ApiException.badRequest("INVALID_REASON", "Mã lý do không hợp lệ cho quyết định này.");
        return reason;
    }

    private static TrustReason required(String code, TrustReason.Kind kind) {
        if (code == null || code.isBlank()) throw ApiException.badRequest("REASON_REQUIRED", "Cần chọn lý do thu hồi.");
        return reason(code, kind);
    }

    private static String shown(TrustReason reason, String note) {
        String text = note == null || note.isBlank() ? reason.getVietnameseLabel() : reason.getVietnameseLabel() + ": " + note.trim();
        return text.length() > 900 ? text.substring(0, 900) : text;
    }

    private static Timestamp ts(Instant instant) { return Timestamp.from(instant); }

    private static String date(Instant instant) {
        return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(instant);
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private record Ownership(UUID listingId, UUID kycId, String status, UUID ownerId, String title) {}

    public record Decision(UUID id, String decision, String reasonCode, String reasonLabel, String note, Instant expiresAt,
                           UUID actorId, String actorName, Instant createdAt) {}

    public record OwnershipExtras(Instant expiresAt, Instant revokedAt, UUID decidedBy, String decidedByName, String decisionReasonCode,
                                  String listingTitle, String listingAddress, String listingStatus, UUID listingOwnerId) {}
}

package com.company.bds.verification.application;

import com.company.bds.iam.application.AuthService;
import com.company.bds.shared.error.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Staff access to someone's identity documents: the staff member re-confirms their own password (the existing
 * short-lived access grant), states a reason, and every opening is written to {@code kyc_access_log}. Reading a private
 * image then needs both the grant token and a live log entry for the image's owner.
 */
@Service
public class KycDocumentAccessService {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final Clock clock;

    public KycDocumentAccessService(JdbcTemplate jdbc, AuthService auth, Clock clock) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.clock = clock;
    }

    @Transactional
    public DocumentAccess open(UUID actorId, UUID subjectUserId, String password, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 5) throw ApiException.badRequest("REASON_REQUIRED", "Cần nhập lý do xem giấy tờ (ít nhất 5 ký tự).");
        if (why.length() > 500) throw ApiException.badRequest("REASON_TOO_LONG", "Lý do tối đa 500 ký tự.");
        if (password == null || password.isBlank()) throw ApiException.badRequest("PASSWORD_REQUIRED", "Cần xác nhận lại mật khẩu.");
        List<Documents> profile = jdbc.query("""
                SELECT id_card_front_url, id_card_back_url, selfie_url FROM user_kyc_profiles WHERE user_id = ?
                """, (rs, n) -> new Documents(rs.getString(1), rs.getString(2), rs.getString(3)), subjectUserId);
        List<String> ownershipDocuments = jdbc.queryForList("""
                SELECT v.document_urls FROM listing_verifications v JOIN listings l ON l.id = v.listing_id
                WHERE l.owner_id = ? AND v.document_urls IS NOT NULL ORDER BY v.created_at DESC, v.id DESC LIMIT 20
                """, String.class, subjectUserId);
        if (profile.isEmpty() && ownershipDocuments.isEmpty()) {
            throw ApiException.notFound("KYC_NOT_FOUND", "Tài khoản chưa nộp giấy tờ định danh hay hồ sơ sở hữu.");
        }
        AuthService.KycDocumentAccess grant = auth.grantKycDocumentAccess(actorId, password);
        jdbc.update("""
                INSERT INTO kyc_access_log(id, subject_user_id, actor_id, reason, grant_expires_at, grant_token_hash, created_at)
                VALUES (?,?,?,?,?,?,?)
                """, UUID.randomUUID(), subjectUserId, actorId, why, Timestamp.from(grant.expiresAt()),
                AuthService.sha256(grant.token()), Timestamp.from(clock.instant()));
        return new DocumentAccess(grant.token(), grant.expiresAt(), profile.isEmpty() ? null : profile.get(0), ownershipDocuments);
    }

    /**
     * Whether a staff member may stream this private object now: a valid grant whose own log row (bound by the token
     * hash) names the image's owner with a reason. Another grant of the same actor does not borrow that reason.
     */
    @Transactional(readOnly = true)
    public boolean staffMayRead(UUID actorId, String grantToken, String objectKey) {
        if (grantToken == null || !auth.hasKycDocumentAccess(actorId, grantToken)) return false;
        Integer allowed = jdbc.queryForObject("""
                SELECT count(*) FROM media_objects m JOIN kyc_access_log a ON a.subject_user_id = m.owner_id
                WHERE m.object_key = ? AND a.actor_id = ? AND a.grant_expires_at > ? AND a.grant_token_hash = ?
                """, Integer.class, objectKey, actorId, Timestamp.from(clock.instant()), AuthService.sha256(grantToken));
        return allowed != null && allowed > 0;
    }

    @Transactional(readOnly = true)
    public List<AccessLogEntry> log(UUID subjectUserId) {
        return jdbc.query("""
                SELECT a.id, a.actor_id, u.full_name, a.reason, a.created_at, a.grant_expires_at
                FROM kyc_access_log a LEFT JOIN users u ON u.id = a.actor_id
                WHERE a.subject_user_id = ? ORDER BY a.created_at DESC, a.id DESC LIMIT 100
                """, (rs, n) -> new AccessLogEntry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant()), subjectUserId);
    }

    public record Documents(String idCardFrontUrl, String idCardBackUrl, String selfieUrl) {}

    public record DocumentAccess(String token, Instant expiresAt, Documents identity, List<String> ownershipDocumentUrls) {}

    public record AccessLogEntry(UUID id, UUID actorId, String actorName, String reason, Instant createdAt, Instant grantExpiresAt) {}
}

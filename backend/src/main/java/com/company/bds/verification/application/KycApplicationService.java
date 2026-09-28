package com.company.bds.verification.application;

import com.company.bds.media.MediaStorageService;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.security.PiiProtectionService;
import com.company.bds.verification.domain.model.KycStatus;
import com.company.bds.verification.domain.model.UserKycProfile;
import com.company.bds.verification.domain.port.UserKycPersistencePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class KycApplicationService {
    private final UserKycPersistencePort persistence;
    private final PiiProtectionService pii;
    private final ObjectProvider<MediaStorageService> media;
    private final JdbcTemplate jdbc;

    public KycApplicationService(UserKycPersistencePort p, PiiProtectionService pii, ObjectProvider<MediaStorageService> media,
                                 JdbcTemplate jdbc) {
        this.persistence = p;
        this.pii = pii;
        this.media = media;
        this.jdbc = jdbc;
    }

    /**
     * First submission, or resubmission after a rejection/revocation or once the previous verification expired (the
     * profile row is reused: one profile per user). Refused while a submission waits or a verification is still valid.
     */
    public UserKycProfile submitKyc(UUID userId, String rawIdNumber, String fullName, String dob, String address, String front, String back, String selfie) {
        if (front == null || back == null || selfie == null || !front.startsWith("/api/v1/media/kyc/") || !back.startsWith("/api/v1/media/kyc/")
                || !selfie.startsWith("/api/v1/media/kyc/")) {
            throw new IllegalArgumentException("Cần đủ ảnh CCCD mặt trước, mặt sau và ảnh chân dung riêng tư.");
        }
        Optional<UserKycProfile> mine = persistence.findByUserId(userId);
        if (mine.isPresent() && mine.get().getStatus() == KycStatus.PENDING) {
            throw ApiException.conflict("KYC_PENDING", "Hồ sơ trước đang chờ duyệt; vui lòng chờ kết quả trước khi gửi lại.");
        }
        if (mine.isPresent() && mine.get().getStatus() == KycStatus.VERIFIED && !expired(mine.get().getId())) {
            throw ApiException.conflict("KYC_ALREADY_VERIFIED", "Danh tính đã được xác minh và còn hiệu lực.");
        }
        MediaStorageService storage = media.getIfAvailable();
        if (storage == null) throw new IllegalStateException("Kho tài liệu định danh chưa sẵn sàng.");
        storage.validateKycOwnership(userId, List.of(front, back, selfie));
        String hash = pii.blindIndex(rawIdNumber);
        Optional<UserKycProfile> existing = persistence.findByIdNumberLookupHash(hash);
        if (existing.isPresent() && !existing.get().getUserId().equals(userId)) throw new IllegalStateException("CCCD đã liên kết với tài khoản khác.");
        var protectedId = pii.protect(rawIdNumber);
        Instant now = Instant.now();
        UUID id = mine.map(UserKycProfile::getId).orElseGet(UUID::randomUUID);
        UserKycProfile saved = persistence.save(new UserKycProfile(id, userId, protectedId.encrypted(), protectedId.blindIndex(), fullName.trim(), dob,
                address, front, back, selfie, null, KycStatus.PENDING, null, now, null));
        if (mine.isPresent()) {
            // A resubmission starts a new review: previous validity/decision columns no longer apply.
            jdbc.update("""
                    UPDATE user_kyc_profiles SET created_at = ?, expires_at = NULL, decided_by = NULL, decision_reason_code = NULL,
                        revoked_at = NULL WHERE id = ?
                    """, Timestamp.from(now), id);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<UserKycProfile> getKycByUserId(UUID userId) { return persistence.findByUserId(userId); }

    @Transactional(readOnly = true)
    public List<UserKycProfile> getQueue(KycStatus status, int page, int size) {
        return status == null ? persistence.findPage(page, size) : persistence.findByStatus(status, page, size);
    }

    /** Validity of a user's identity check for the /kyc page (null values when never decided). */
    @Transactional(readOnly = true)
    public Validity validity(UUID userId) {
        return jdbc.query("SELECT expires_at, revoked_at, decision_reason_code FROM user_kyc_profiles WHERE user_id = ?",
                (rs, n) -> new Validity(rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
                        rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant(), rs.getString(3)), userId)
                .stream().findFirst().orElse(new Validity(null, null, null));
    }

    private boolean expired(UUID kycId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM user_kyc_profiles WHERE id = ? AND expires_at IS NOT NULL AND expires_at <= now()",
                Integer.class, kycId);
        return count != null && count > 0;
    }

    public record Validity(Instant expiresAt, Instant revokedAt, String decisionReasonCode) {}
}

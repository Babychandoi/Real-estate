package com.company.bds.verification.application;

import com.company.bds.shared.error.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Evidence comparison for an ownership decision (UI-22): identity name vs name on the document, certificate number
 * (and whether it is used by another owner's listing), listing address vs identity address, validity of the identity,
 * the private document images and the decision history. Nothing here is cached or public.
 */
@Service
public class VerificationEvidenceService {
    private final JdbcTemplate jdbc;
    private final TrustDecisionService trust;

    public VerificationEvidenceService(JdbcTemplate jdbc, TrustDecisionService trust) {
        this.jdbc = jdbc;
        this.trust = trust;
    }

    @Transactional(readOnly = true)
    public Evidence evidence(UUID verificationId) {
        List<Evidence> rows = jdbc.query("""
                SELECT v.id, v.listing_id, v.verification_type, v.status, v.certificate_number, v.document_urls, v.owner_name_on_doc,
                       v.created_at, v.expires_at, v.revoked_at, v.verifier_note,
                       l.owner_id, l.status AS listing_status, l.slug, r.title, r.address_summary, r.district_code,
                       k.id AS kyc_id, k.full_name AS kyc_name, k.status AS kyc_status, k.address AS kyc_address, k.verified_at AS kyc_verified_at,
                       k.expires_at AS kyc_expires_at,
                       bds_normalize_text(k.full_name) = bds_normalize_text(v.owner_name_on_doc) AS name_match,
                       (SELECT count(*) FROM listing_verifications o JOIN listings ol ON ol.id = o.listing_id
                        WHERE o.id <> v.id AND o.certificate_number IS NOT NULL
                          AND bds_normalize_text(o.certificate_number) = bds_normalize_text(v.certificate_number)
                          AND ol.owner_id <> l.owner_id) AS certificate_elsewhere,
                       similarity(bds_normalize_text(k.address), bds_normalize_text(r.address_summary)) AS address_similarity
                FROM listing_verifications v
                LEFT JOIN listings l ON l.id = v.listing_id
                LEFT JOIN listing_revisions r ON r.id = COALESCE(l.public_revision_id,
                    (SELECT x.id FROM listing_revisions x WHERE x.listing_id = l.id ORDER BY x.revision_number DESC LIMIT 1))
                LEFT JOIN user_kyc_profiles k ON k.id = v.user_kyc_id
                WHERE v.id = ?
                """, (rs, n) -> {
            List<Comparison> comparisons = new ArrayList<>();
            String kycName = rs.getString("kyc_name");
            String docName = rs.getString("owner_name_on_doc");
            comparisons.add(new Comparison("OWNER_NAME", "Họ tên chủ sở hữu", kycName, docName,
                    kycName == null || docName == null ? "MISSING" : rs.getBoolean("name_match") ? "MATCH" : "MISMATCH"));
            String certificate = rs.getString("certificate_number");
            long elsewhere = rs.getLong("certificate_elsewhere");
            comparisons.add(new Comparison("CERTIFICATE_NUMBER", "Số giấy chứng nhận", null, certificate,
                    certificate == null || certificate.isBlank() ? "MISSING" : elsewhere > 0 ? "USED_ELSEWHERE" : "UNIQUE"));
            String kycAddress = rs.getString("kyc_address");
            String listingAddress = rs.getString("address_summary");
            double similarity = rs.getDouble("address_similarity");
            comparisons.add(new Comparison("ADDRESS", "Địa chỉ (định danh ↔ tin đăng)", kycAddress, listingAddress,
                    kycAddress == null || listingAddress == null ? "MISSING" : similarity >= 0.5 ? "SIMILAR" : "DIFFERENT"));
            Instant kycExpires = ts(rs.getTimestamp("kyc_expires_at"));
            String identity = rs.getString("kyc_status") == null ? "NOT_SUBMITTED"
                    : "VERIFIED".equals(rs.getString("kyc_status")) && kycExpires != null && kycExpires.isBefore(Instant.now()) ? "EXPIRED"
                    : rs.getString("kyc_status");
            String urls = rs.getString("document_urls");
            List<String> documents = urls == null || urls.isBlank() ? List.of()
                    : Arrays.stream(urls.split("[,;\\s]+")).filter(u -> !u.isBlank()).limit(20).toList();
            return new Evidence(rs.getObject("id", UUID.class), rs.getObject("listing_id", UUID.class), rs.getString("title"),
                    rs.getString("slug"), rs.getString("listing_status"), listingAddress, rs.getString("district_code"),
                    rs.getObject("owner_id", UUID.class), rs.getString("verification_type"), rs.getString("status"),
                    certificate, docName, documents, ts(rs.getTimestamp("created_at")), ts(rs.getTimestamp("expires_at")),
                    ts(rs.getTimestamp("revoked_at")), rs.getString("verifier_note"),
                    new Identity(rs.getObject("kyc_id", UUID.class), identity, kycName, ts(rs.getTimestamp("kyc_verified_at")), kycExpires),
                    comparisons, List.of());
        }, verificationId);
        if (rows.isEmpty()) throw ApiException.notFound("VERIFICATION_NOT_FOUND", "Không tìm thấy hồ sơ giấy tờ.");
        Evidence e = rows.get(0);
        return new Evidence(e.id(), e.listingId(), e.listingTitle(), e.listingSlug(), e.listingStatus(), e.listingAddress(),
                e.districtCode(), e.listingOwnerId(), e.verificationType(), e.status(), e.certificateNumber(), e.ownerNameOnDoc(),
                e.documentUrls(), e.submittedAt(), e.expiresAt(), e.revokedAt(), e.verifierNote(), e.identity(), e.comparisons(),
                trust.history("OWNERSHIP", verificationId, true));
    }

    private static Instant ts(Timestamp t) { return t == null ? null : t.toInstant(); }

    public record Identity(UUID kycId, String status, String fullName, Instant verifiedAt, Instant expiresAt) {}

    /** {@code result}: MATCH/MISMATCH/MISSING (name), UNIQUE/USED_ELSEWHERE/MISSING (certificate), SIMILAR/DIFFERENT/MISSING (address). */
    public record Comparison(String field, String label, String identityValue, String documentValue, String result) {}

    public record Evidence(UUID id, UUID listingId, String listingTitle, String listingSlug, String listingStatus, String listingAddress,
                           String districtCode, UUID listingOwnerId, String verificationType, String status, String certificateNumber,
                           String ownerNameOnDoc, List<String> documentUrls, Instant submittedAt, Instant expiresAt, Instant revokedAt,
                           String verifierNote, Identity identity, List<Comparison> comparisons,
                           List<TrustDecisionService.Decision> history) {}
}

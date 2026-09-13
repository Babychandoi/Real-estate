package com.company.bds.verification.application;

import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.verification.domain.model.ListingVerification;
import com.company.bds.verification.domain.model.VerificationStatus;
import com.company.bds.verification.domain.model.VerificationType;
import com.company.bds.verification.domain.model.KycStatus;
import com.company.bds.verification.domain.port.ListingVerificationPersistencePort;
import com.company.bds.verification.domain.port.UserKycPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ListingVerificationApplicationService {

    private final ListingVerificationPersistencePort verificationPersistencePort;
    private final UserKycPersistencePort kycPersistencePort;
    private final ListingPersistencePort listingPersistencePort;

    public ListingVerificationApplicationService(
            ListingVerificationPersistencePort verificationPersistencePort,
            UserKycPersistencePort kycPersistencePort,
            ListingPersistencePort listingPersistencePort) {
        this.verificationPersistencePort = verificationPersistencePort;
        this.kycPersistencePort = kycPersistencePort;
        this.listingPersistencePort = listingPersistencePort;
    }

    /**
     * Chủ nhà / Môi giới nộp hồ sơ pháp lý xin cấp nhãn Tin Chính Chủ (FR03, UC03).
     */
    public ListingVerification submitVerification(
            UUID listingId,
            UUID userId,
            VerificationType type,
            String certificateNumber,
            String documentUrls,
            String ownerNameOnDoc) {

        Listing listing = listingPersistencePort.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Tin đăng không tồn tại ID: " + listingId));

        if (!listing.getOwnerId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Chỉ chủ tin đăng mới có thể nộp hồ sơ xác minh.");
        }

        // Lấy hồ sơ eKYC của người dùng nếu có
        var kyc = kycPersistencePort.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Tài khoản cần hoàn tất eKYC trước khi xác minh tin."));
        if (kyc.getStatus() != KycStatus.VERIFIED) {
            throw new IllegalStateException("Hồ sơ eKYC phải được duyệt trước khi xác minh tin.");
        }
        UUID userKycId = kyc.getId();

        ListingVerification verification = ListingVerification.create(
                listingId,
                userKycId,
                type,
                certificateNumber,
                documentUrls,
                ownerNameOnDoc,
                Instant.now()
        );

        return verificationPersistencePort.save(verification);
    }

    /**
     * Thẩm định viên đối soát và Phê duyệt cấp nhãn Tin Chính Chủ (FR03).
     * Tự động kích hoạt cờ isVerifiedOwner trên tin đăng để xuất hiện tích xanh bảo chứng.
     */
    public ListingVerification approveVerification(UUID verificationId, String verifierNote) {
        ListingVerification verification = verificationPersistencePort.findById(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ thẩm định ID: " + verificationId));

        if (verification.getUserKycId() == null) {
            throw new IllegalStateException("Hồ sơ xác minh tin không có eKYC đã liên kết.");
        }
        var kyc = kycPersistencePort.findById(verification.getUserKycId())
                .orElseThrow(() -> new IllegalStateException("Hồ sơ eKYC liên kết không còn tồn tại."));
        if (kyc.getStatus() != KycStatus.VERIFIED) {
            throw new IllegalStateException("Không thể duyệt tin khi eKYC chưa được xác minh.");
        }

        Instant now = Instant.now();
        verification.approve(verifierNote, now);
        ListingVerification saved = verificationPersistencePort.save(verification);

        // Kích hoạt nhãn chính chủ trên tin đăng
        Listing listing = listingPersistencePort.findById(verification.getListingId()).orElse(null);
        if (listing != null) {
            listing.markVerifiedOwner(true, now);
            listingPersistencePort.save(listing);
        }

        return saved;
    }

    /**
     * Thẩm định viên từ chối hồ sơ do thông tin không trùng khớp.
     */
    public ListingVerification rejectVerification(UUID verificationId, String reason) {
        ListingVerification verification = verificationPersistencePort.findById(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ thẩm định ID: " + verificationId));

        Instant now = Instant.now();
        verification.reject(reason, now);
        return verificationPersistencePort.save(verification);
    }

    /**
     * Thu hồi nhãn Tin Chính Chủ (VD: phát hiện hợp đồng hết hạn hoặc có tranh chấp pháp lý).
     */
    public ListingVerification revokeVerification(UUID verificationId, String reason) {
        ListingVerification verification = verificationPersistencePort.findById(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ thẩm định ID: " + verificationId));

        Instant now = Instant.now();
        verification.revoke(reason, now);
        ListingVerification saved = verificationPersistencePort.save(verification);

        // Gỡ bỏ nhãn chính chủ trên tin đăng
        Listing listing = listingPersistencePort.findById(verification.getListingId()).orElse(null);
        if (listing != null) {
            listing.markVerifiedOwner(false, now);
            listingPersistencePort.save(listing);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ListingVerification> getQueue(VerificationStatus status, int page, int size) {
        if (status != null) {
            return verificationPersistencePort.findByStatus(status, page, size);
        }
        return verificationPersistencePort.findPage(page, size);
    }

    @Transactional(readOnly = true)
    public ListingVerification getVerificationDetail(UUID verificationId) {
        return verificationPersistencePort.findById(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ thẩm định ID: " + verificationId));
    }

    @Transactional(readOnly = true)
    public List<ListingVerification> getVerificationsByListing(UUID listingId) {
        return verificationPersistencePort.findByListingId(listingId, 0, 100);
    }
}

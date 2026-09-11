package com.company.bds.verification.application;

import com.company.bds.verification.domain.model.KycStatus;
import com.company.bds.verification.domain.model.UserKycProfile;
import com.company.bds.verification.domain.port.UserKycPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class KycApplicationService {

    private final UserKycPersistencePort kycPersistencePort;

    public KycApplicationService(UserKycPersistencePort kycPersistencePort) {
        this.kycPersistencePort = kycPersistencePort;
    }

    /**
     * Người dùng / Chủ nhà nộp hồ sơ eKYC xác thực định danh (FR01, NFR12).
     */
    public UserKycProfile submitKyc(
            UUID userId,
            String rawIdNumber,
            String fullName,
            String dob,
            String address,
            String idCardFrontUrl,
            String idCardBackUrl,
            String selfieUrl) {

        // 1. Kiểm tra chống trùng lặp / chống mạo danh bằng hash tra cứu NFR12
        String lookupHash = Integer.toHexString(rawIdNumber.trim().hashCode());
        Optional<UserKycProfile> existingByHash = kycPersistencePort.findByIdNumberLookupHash(lookupHash);
        if (existingByHash.isPresent() && !existingByHash.get().getUserId().equals(userId)) {
            throw new IllegalStateException("Số Căn cước công dân này đã được liên kết với một tài khoản khác trong hệ thống.");
        }

        // 2. Tính điểm khớp khuôn mặt AI Face Matching mô phỏng (95.0% - 99.5%)
        double aiScore = 95.0 + Math.round((Math.random() * 4.5) * 10.0) / 10.0;

        // 3. Khởi tạo và lưu hồ sơ eKYC
        UserKycProfile profile = UserKycProfile.create(
                userId,
                rawIdNumber.trim(),
                fullName,
                dob,
                address,
                idCardFrontUrl,
                idCardBackUrl,
                selfieUrl,
                aiScore,
                Instant.now()
        );

        return kycPersistencePort.save(profile);
    }

    /**
     * Thẩm định viên phê duyệt hồ sơ eKYC.
     */
    public UserKycProfile approveKyc(UUID kycId) {
        UserKycProfile profile = kycPersistencePort.findById(kycId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ eKYC ID: " + kycId));
        profile.approve(Instant.now());
        return kycPersistencePort.save(profile);
    }

    /**
     * Từ chối hồ sơ eKYC kèm lý do.
     */
    public UserKycProfile rejectKyc(UUID kycId, String reason) {
        UserKycProfile profile = kycPersistencePort.findById(kycId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ eKYC ID: " + kycId));
        profile.reject(reason, Instant.now());
        return kycPersistencePort.save(profile);
    }

    @Transactional(readOnly = true)
    public Optional<UserKycProfile> getKycByUserId(UUID userId) {
        return kycPersistencePort.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<UserKycProfile> getQueue(KycStatus status) {
        if (status != null) {
            return kycPersistencePort.findByStatus(status);
        }
        return kycPersistencePort.findAll();
    }
}

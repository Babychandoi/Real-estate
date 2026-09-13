package com.company.bds.verification.domain.port;

import com.company.bds.verification.domain.model.KycStatus;
import com.company.bds.verification.domain.model.UserKycProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserKycPersistencePort {
    UserKycProfile save(UserKycProfile profile);
    Optional<UserKycProfile> findById(UUID id);
    Optional<UserKycProfile> findByUserId(UUID userId);
    Optional<UserKycProfile> findByIdNumberLookupHash(String lookupHash);
    List<UserKycProfile> findByStatus(KycStatus status, int page, int size);
    List<UserKycProfile> findPage(int page, int size);
    long countByStatus(KycStatus status);
}

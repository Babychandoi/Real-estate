package com.company.bds.verification.domain.port;

import com.company.bds.verification.domain.model.ListingVerification;
import com.company.bds.verification.domain.model.VerificationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingVerificationPersistencePort {
    ListingVerification save(ListingVerification verification);
    Optional<ListingVerification> findById(UUID id);
    List<ListingVerification> findByListingId(UUID listingId, int page, int size);
    List<ListingVerification> findByStatus(VerificationStatus status, int page, int size);
    Optional<ListingVerification> findActiveVerifiedOwner(UUID listingId);
    List<ListingVerification> findPage(int page, int size);
    long countByStatus(VerificationStatus status);
}

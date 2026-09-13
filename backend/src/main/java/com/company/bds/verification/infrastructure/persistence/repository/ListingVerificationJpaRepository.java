package com.company.bds.verification.infrastructure.persistence.repository;

import com.company.bds.verification.domain.model.VerificationStatus;
import com.company.bds.verification.infrastructure.persistence.entity.ListingVerificationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ListingVerificationJpaRepository extends JpaRepository<ListingVerificationJpaEntity, UUID> {

    List<ListingVerificationJpaEntity> findByListingIdOrderByCreatedAtDesc(UUID listingId, Pageable pageable);

    List<ListingVerificationJpaEntity> findByStatusOrderByCreatedAtDesc(VerificationStatus status, Pageable pageable);

    Optional<ListingVerificationJpaEntity> findFirstByListingIdAndStatus(UUID listingId, VerificationStatus status);

    long countByStatus(VerificationStatus status);
}

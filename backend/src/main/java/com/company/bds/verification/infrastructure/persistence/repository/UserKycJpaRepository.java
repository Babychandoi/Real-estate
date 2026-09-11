package com.company.bds.verification.infrastructure.persistence.repository;

import com.company.bds.verification.domain.model.KycStatus;
import com.company.bds.verification.infrastructure.persistence.entity.UserKycJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserKycJpaRepository extends JpaRepository<UserKycJpaEntity, UUID> {

    Optional<UserKycJpaEntity> findByUserId(UUID userId);

    Optional<UserKycJpaEntity> findByIdNumberLookupHash(String lookupHash);

    List<UserKycJpaEntity> findByStatusOrderByCreatedAtDesc(KycStatus status);

    long countByStatus(KycStatus status);
}

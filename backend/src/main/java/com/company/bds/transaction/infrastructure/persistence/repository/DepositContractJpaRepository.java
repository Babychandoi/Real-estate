package com.company.bds.transaction.infrastructure.persistence.repository;

import com.company.bds.transaction.infrastructure.persistence.entity.DepositContractJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DepositContractJpaRepository extends JpaRepository<DepositContractJpaEntity, UUID> {
    List<DepositContractJpaEntity> findByListingIdOrderByCreatedAtDesc(UUID listingId);
    List<DepositContractJpaEntity> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);
    List<DepositContractJpaEntity> findBySellerIdOrderByCreatedAtDesc(UUID sellerId);
}

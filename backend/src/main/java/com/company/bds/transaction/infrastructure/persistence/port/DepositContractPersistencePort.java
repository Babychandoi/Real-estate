package com.company.bds.transaction.infrastructure.persistence.port;

import com.company.bds.transaction.domain.model.DepositContract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepositContractPersistencePort {
    DepositContract save(DepositContract contract);
    Optional<DepositContract> findById(UUID id);
    List<DepositContract> findByListingId(UUID listingId);
    List<DepositContract> findByBuyerId(UUID buyerId);
    List<DepositContract> findBySellerId(UUID sellerId);
}

package com.company.bds.transaction.infrastructure.persistence.adapter;

import com.company.bds.transaction.domain.model.DepositContract;
import com.company.bds.transaction.domain.model.EscrowTransaction;
import com.company.bds.transaction.infrastructure.persistence.entity.DepositContractJpaEntity;
import com.company.bds.transaction.infrastructure.persistence.entity.EscrowTransactionJpaEntity;
import com.company.bds.transaction.infrastructure.persistence.port.DepositContractPersistencePort;
import com.company.bds.transaction.infrastructure.persistence.repository.DepositContractJpaRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DepositContractPersistenceAdapter implements DepositContractPersistencePort {

    private final DepositContractJpaRepository repository;

    public DepositContractPersistenceAdapter(DepositContractJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public DepositContract save(DepositContract contract) {
        DepositContractJpaEntity entity = toEntity(contract);
        DepositContractJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<DepositContract> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<DepositContract> findByListingId(UUID listingId) {
        return repository.findByListingIdOrderByCreatedAtDesc(listingId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<DepositContract> findByBuyerId(UUID buyerId) {
        return repository.findByBuyerIdOrderByCreatedAtDesc(buyerId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<DepositContract> findBySellerId(UUID sellerId) {
        return repository.findBySellerIdOrderByCreatedAtDesc(sellerId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    private DepositContractJpaEntity toEntity(DepositContract domain) {
        DepositContractJpaEntity entity = new DepositContractJpaEntity();
        entity.setId(domain.getId());
        entity.setListingId(domain.getListingId());
        entity.setBuyerId(domain.getBuyerId());
        entity.setBuyerName(domain.getBuyerName());
        entity.setBuyerPhone(domain.getBuyerPhone());
        entity.setBuyerIdMasked(domain.getBuyerIdMasked());
        entity.setSellerId(domain.getSellerId());
        entity.setSellerName(domain.getSellerName());
        entity.setSellerPhone(domain.getSellerPhone());
        entity.setDepositAmount(domain.getDepositAmount());
        entity.setListingPrice(domain.getListingPrice());
        entity.setStatus(domain.getStatus());
        entity.setTermsConditions(domain.getTermsConditions());
        entity.setBuyerSignedAt(domain.getBuyerSignedAt());
        entity.setBuyerOtpVerified(domain.isBuyerOtpVerified());
        entity.setSellerSignedAt(domain.getSellerSignedAt());
        entity.setSellerOtpVerified(domain.isSellerOtpVerified());
        entity.setEscrowLockedAt(domain.getEscrowLockedAt());
        entity.setCompletedAt(domain.getCompletedAt());
        entity.setDisputeReason(domain.getDisputeReason());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        List<EscrowTransactionJpaEntity> txEntities = new ArrayList<>();
        if (domain.getEscrowTransactions() != null) {
            for (EscrowTransaction tx : domain.getEscrowTransactions()) {
                EscrowTransactionJpaEntity txEntity = new EscrowTransactionJpaEntity();
                txEntity.setId(tx.getId());
                txEntity.setContract(entity);
                txEntity.setAction(tx.getAction());
                txEntity.setAmount(tx.getAmount());
                txEntity.setPerformedBy(tx.getPerformedBy());
                txEntity.setNote(tx.getNote());
                txEntity.setCreatedAt(tx.getCreatedAt());
                txEntities.add(txEntity);
            }
        }
        entity.setEscrowTransactions(txEntities);
        return entity;
    }

    private DepositContract toDomain(DepositContractJpaEntity entity) {
        List<EscrowTransaction> domainTxs = new ArrayList<>();
        if (entity.getEscrowTransactions() != null) {
            for (EscrowTransactionJpaEntity tx : entity.getEscrowTransactions()) {
                domainTxs.add(new EscrowTransaction(
                        tx.getId(),
                        entity.getId(),
                        tx.getAction(),
                        tx.getAmount(),
                        tx.getPerformedBy(),
                        tx.getNote(),
                        tx.getCreatedAt()
                ));
            }
        }

        return new DepositContract(
                entity.getId(),
                entity.getListingId(),
                entity.getBuyerId(),
                entity.getBuyerName(),
                entity.getBuyerPhone(),
                entity.getBuyerIdMasked(),
                entity.getSellerId(),
                entity.getSellerName(),
                entity.getSellerPhone(),
                entity.getDepositAmount(),
                entity.getListingPrice(),
                entity.getStatus(),
                entity.getTermsConditions(),
                entity.getBuyerSignedAt(),
                entity.isBuyerOtpVerified(),
                entity.getSellerSignedAt(),
                entity.isSellerOtpVerified(),
                entity.getEscrowLockedAt(),
                entity.getCompletedAt(),
                entity.getDisputeReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                domainTxs
        );
    }
}

package com.company.bds.verification.infrastructure.persistence.adapter;

import com.company.bds.verification.domain.model.ListingVerification;
import com.company.bds.verification.domain.model.VerificationStatus;
import com.company.bds.verification.domain.port.ListingVerificationPersistencePort;
import com.company.bds.verification.infrastructure.persistence.entity.ListingVerificationJpaEntity;
import com.company.bds.verification.infrastructure.persistence.repository.ListingVerificationJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ListingVerificationPersistenceAdapter implements ListingVerificationPersistencePort {

    private final ListingVerificationJpaRepository verificationJpaRepository;

    public ListingVerificationPersistenceAdapter(ListingVerificationJpaRepository verificationJpaRepository) {
        this.verificationJpaRepository = verificationJpaRepository;
    }

    @Override
    public ListingVerification save(ListingVerification verification) {
        ListingVerificationJpaEntity entity = toEntity(verification);
        ListingVerificationJpaEntity saved = verificationJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<ListingVerification> findById(UUID id) {
        return verificationJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ListingVerification> findByListingId(UUID listingId) {
        return verificationJpaRepository.findByListingIdOrderByCreatedAtDesc(listingId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ListingVerification> findByStatus(VerificationStatus status) {
        return verificationJpaRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<ListingVerification> findActiveVerifiedOwner(UUID listingId) {
        return verificationJpaRepository.findFirstByListingIdAndStatus(listingId, VerificationStatus.VERIFIED_OWNER)
                .map(this::toDomain);
    }

    @Override
    public List<ListingVerification> findAll() {
        return verificationJpaRepository.findAll()
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countByStatus(VerificationStatus status) {
        return verificationJpaRepository.countByStatus(status);
    }

    private ListingVerificationJpaEntity toEntity(ListingVerification d) {
        return new ListingVerificationJpaEntity(
                d.getId(),
                d.getListingId(),
                d.getUserKycId(),
                d.getVerificationType(),
                d.getCertificateNumber(),
                d.getDocumentUrls(),
                d.getOwnerNameOnDoc(),
                d.getStatus(),
                d.getVerifierNote(),
                d.getCreatedAt(),
                d.getVerifiedAt()
        );
    }

    private ListingVerification toDomain(ListingVerificationJpaEntity e) {
        return new ListingVerification(
                e.getId(),
                e.getListingId(),
                e.getUserKycId(),
                e.getVerificationType(),
                e.getCertificateNumber(),
                e.getDocumentUrls(),
                e.getOwnerNameOnDoc(),
                e.getStatus(),
                e.getVerifierNote(),
                e.getCreatedAt(),
                e.getVerifiedAt()
        );
    }
}

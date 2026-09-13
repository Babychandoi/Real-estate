package com.company.bds.verification.infrastructure.persistence.adapter;

import com.company.bds.verification.domain.model.KycStatus;
import com.company.bds.verification.domain.model.UserKycProfile;
import com.company.bds.verification.domain.port.UserKycPersistencePort;
import com.company.bds.verification.infrastructure.persistence.entity.UserKycJpaEntity;
import com.company.bds.verification.infrastructure.persistence.repository.UserKycJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class UserKycPersistenceAdapter implements UserKycPersistencePort {

    private final UserKycJpaRepository kycJpaRepository;

    public UserKycPersistenceAdapter(UserKycJpaRepository kycJpaRepository) {
        this.kycJpaRepository = kycJpaRepository;
    }

    @Override
    public UserKycProfile save(UserKycProfile profile) {
        UserKycJpaEntity entity = toEntity(profile);
        UserKycJpaEntity saved = kycJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<UserKycProfile> findById(UUID id) {
        return kycJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<UserKycProfile> findByUserId(UUID userId) {
        return kycJpaRepository.findByUserId(userId).map(this::toDomain);
    }

    @Override
    public Optional<UserKycProfile> findByIdNumberLookupHash(String lookupHash) {
        return kycJpaRepository.findByIdNumberLookupHash(lookupHash).map(this::toDomain);
    }

    @Override
    public List<UserKycProfile> findByStatus(KycStatus status, int page, int size) {
        return kycJpaRepository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, size))
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<UserKycProfile> findPage(int page, int size) {
        return kycJpaRepository.findAll(PageRequest.of(page, size)).getContent()
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countByStatus(KycStatus status) {
        return kycJpaRepository.countByStatus(status);
    }

    private UserKycJpaEntity toEntity(UserKycProfile d) {
        return new UserKycJpaEntity(
                d.getId(),
                d.getUserId(),
                d.getIdNumberEncrypted(),
                d.getIdNumberLookupHash(),
                d.getFullName(),
                d.getDob(),
                d.getAddress(),
                d.getIdCardFrontUrl(),
                d.getIdCardBackUrl(),
                d.getSelfieUrl(),
                d.getFaceMatchScore(),
                d.getStatus(),
                d.getRejectionReason(),
                d.getCreatedAt(),
                d.getVerifiedAt()
        );
    }

    private UserKycProfile toDomain(UserKycJpaEntity e) {
        return new UserKycProfile(
                e.getId(),
                e.getUserId(),
                e.getIdNumberEncrypted(),
                e.getIdNumberLookupHash(),
                e.getFullName(),
                e.getDob(),
                e.getAddress(),
                e.getIdCardFrontUrl(),
                e.getIdCardBackUrl(),
                e.getSelfieUrl(),
                e.getFaceMatchScore(),
                e.getStatus(),
                e.getRejectionReason(),
                e.getCreatedAt(),
                e.getVerifiedAt()
        );
    }
}

package com.company.bds.lead.infrastructure.persistence.adapter;

import com.company.bds.lead.domain.model.Lead;
import com.company.bds.lead.domain.port.LeadPersistencePort;
import com.company.bds.lead.infrastructure.persistence.entity.LeadJpaEntity;
import com.company.bds.lead.infrastructure.persistence.repository.LeadJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class LeadPersistenceAdapter implements LeadPersistencePort {

    private final LeadJpaRepository leadJpaRepository;

    public LeadPersistenceAdapter(LeadJpaRepository leadJpaRepository) {
        this.leadJpaRepository = leadJpaRepository;
    }

    @Override
    public Lead save(Lead lead) {
        LeadJpaEntity entity = toEntity(lead);
        LeadJpaEntity saved = leadJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Lead> findById(UUID id) {
        return leadJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Lead> findByListingId(UUID listingId) {
        return leadJpaRepository.findByListingIdOrderByCreatedAtDesc(listingId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Lead> findByListingIds(List<UUID> listingIds) {
        if (listingIds == null || listingIds.isEmpty()) {
            return List.of();
        }
        return leadJpaRepository.findByListingIdInOrderByCreatedAtDesc(listingIds)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Lead> findAll() {
        return leadJpaRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countByPhoneLookupHash(String phoneLookupHash) {
        return leadJpaRepository.countByPhoneLookupHash(phoneLookupHash);
    }

    private LeadJpaEntity toEntity(Lead domain) {
        return new LeadJpaEntity(
                domain.getId(),
                domain.getListingId(),
                domain.getFullName(),
                domain.getPhoneEncrypted(),
                domain.getPhoneLookupHash(),
                domain.getNote(),
                domain.isConsentPolicy(),
                domain.getStatus(),
                domain.getCreatedAt()
        );
    }

    private Lead toDomain(LeadJpaEntity entity) {
        return new Lead(
                entity.getId(),
                entity.getListingId(),
                entity.getFullName(),
                entity.getPhoneEncrypted(),
                entity.getPhoneLookupHash(),
                entity.getNote(),
                entity.isConsentPolicy(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}

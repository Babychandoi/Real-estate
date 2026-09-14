package com.company.bds.lead.infrastructure.persistence.adapter;

import com.company.bds.lead.domain.model.Lead;
import com.company.bds.lead.domain.model.LeadPage;
import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.lead.domain.port.LeadPersistencePort;
import com.company.bds.lead.infrastructure.persistence.entity.LeadJpaEntity;
import com.company.bds.lead.infrastructure.persistence.repository.LeadJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.EnumMap;
import java.util.Map;
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
    public List<Lead> findByListingId(UUID listingId, int page, int size) {
        return leadJpaRepository.findByListingIdOrderByCreatedAtDesc(listingId, PageRequest.of(page, size))
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Lead> findByListingIds(List<UUID> listingIds, int page, int size) {
        if (listingIds == null || listingIds.isEmpty()) {
            return List.of();
        }
        return leadJpaRepository.findByListingIdInOrderByCreatedAtDesc(listingIds, PageRequest.of(page, size))
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Lead> findPage(int page, int size) {
        return leadJpaRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public LeadPage search(List<UUID> listingIds, LeadStatus status, String keyword, int page, int size) {
        if (listingIds == null || listingIds.isEmpty()) return LeadPage.empty(page, size);
        Page<LeadJpaEntity> result = leadJpaRepository.searchByListingIds(listingIds, status, keyword, PageRequest.of(page, size));
        return toLeadPage(result, page, size, leadJpaRepository.countByListingIdsGroupedByStatus(listingIds, keyword));
    }

    @Override
    public LeadPage searchAll(LeadStatus status, String keyword, int page, int size) {
        Page<LeadJpaEntity> result = leadJpaRepository.searchAll(status, keyword, PageRequest.of(page, size));
        return toLeadPage(result, page, size, leadJpaRepository.countAllGroupedByStatus(keyword));
    }

    @Override
    public long countByPhoneLookupHashSince(String phoneLookupHash, java.time.Instant since) {
        return leadJpaRepository.countByPhoneLookupHashAndCreatedAtAfter(phoneLookupHash, since);
    }

    @Override public long countAll() { return leadJpaRepository.count(); }

    @Override
    public long countByStatuses(List<com.company.bds.lead.domain.model.LeadStatus> statuses) {
        return statuses.isEmpty() ? 0 : leadJpaRepository.countByStatusIn(statuses);
    }

    private LeadPage toLeadPage(Page<LeadJpaEntity> result, int page, int size,
            List<com.company.bds.lead.infrastructure.persistence.repository.LeadStatusTotal> totals) {
        Map<LeadStatus, Long> statusCounts = new EnumMap<>(LeadStatus.class);
        for (LeadStatus status : LeadStatus.values()) statusCounts.put(status, 0L);
        totals.forEach(total -> statusCounts.put(total.getStatus(), total.getTotal()));
        return new LeadPage(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(), page, size, statusCounts);
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

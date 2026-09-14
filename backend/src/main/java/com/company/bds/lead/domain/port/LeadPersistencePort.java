package com.company.bds.lead.domain.port;

import com.company.bds.lead.domain.model.Lead;
import com.company.bds.lead.domain.model.LeadPage;
import com.company.bds.lead.domain.model.LeadStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadPersistencePort {
    Lead save(Lead lead);
    Optional<Lead> findById(UUID id);
    List<Lead> findByListingId(UUID listingId, int page, int size);
    List<Lead> findByListingIds(List<UUID> listingIds, int page, int size);
    List<Lead> findPage(int page, int size);
    LeadPage search(List<UUID> listingIds, LeadStatus status, String keyword, int page, int size);
    LeadPage searchAll(LeadStatus status, String keyword, int page, int size);
    long countByPhoneLookupHashSince(String phoneLookupHash, java.time.Instant since);
    long countAll();
    long countByStatuses(List<com.company.bds.lead.domain.model.LeadStatus> statuses);
}

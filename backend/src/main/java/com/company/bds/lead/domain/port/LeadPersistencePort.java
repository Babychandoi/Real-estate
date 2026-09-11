package com.company.bds.lead.domain.port;

import com.company.bds.lead.domain.model.Lead;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadPersistencePort {
    Lead save(Lead lead);
    Optional<Lead> findById(UUID id);
    List<Lead> findByListingId(UUID listingId);
    List<Lead> findByListingIds(List<UUID> listingIds);
    List<Lead> findAll();
    long countByPhoneLookupHash(String phoneLookupHash);
}

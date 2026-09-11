package com.company.bds.listing.infrastructure.persistence.adapter;

import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.listing.infrastructure.persistence.entity.ListingJpaEntity;
import com.company.bds.listing.infrastructure.persistence.mapper.ListingEntityMapper;
import com.company.bds.listing.infrastructure.persistence.repository.ListingJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Hiện thực Output Port ListingPersistencePort, giao tiếp trực tiếp với JPA Repositories.
 */
@Component
@Transactional(readOnly = true)
public class ListingPersistenceAdapter implements ListingPersistencePort {

    private final ListingJpaRepository listingRepository;
    private final ListingEntityMapper mapper;

    public ListingPersistenceAdapter(ListingJpaRepository listingRepository, ListingEntityMapper mapper) {
        this.listingRepository = listingRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Listing save(Listing listing) {
        ListingJpaEntity entity = mapper.toJpaEntity(listing);
        ListingJpaEntity saved = listingRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Listing> findById(UUID id) {
        return listingRepository.findByIdWithRevisions(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<Listing> findByOwnerId(UUID ownerId) {
        return listingRepository.findByOwnerIdWithRevisions(ownerId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Listing> findPublicActiveListings(String purpose, int page, int size) {
        return listingRepository.findPublicActiveListings(PageRequest.of(page, size)).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Listing> findPendingReviewListings() {
        return listingRepository.findPendingReviewListings().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Listing> searchListings(com.company.bds.listing.domain.model.ListingSearchCriteria criteria, int page, int size) {
        String purpose = criteria.purpose() != null ? criteria.purpose().name() : null;
        String propertyType = criteria.propertyType() != null ? criteria.propertyType().name() : null;

        return listingRepository.searchPublicActiveListings(
                purpose,
                propertyType,
                criteria.minPriceVnd(),
                criteria.maxPriceVnd(),
                criteria.minAreaM2(),
                criteria.maxAreaM2(),
                criteria.keyword(),
                criteria.minLat(),
                criteria.maxLat(),
                criteria.minLng(),
                criteria.maxLng(),
                PageRequest.of(page, size)
        ).stream()
        .map(mapper::toDomain)
        .collect(Collectors.toList());
    }
}

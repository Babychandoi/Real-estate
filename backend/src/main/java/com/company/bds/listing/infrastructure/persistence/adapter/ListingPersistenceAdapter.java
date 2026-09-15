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
import java.util.Map;
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
    private final com.company.bds.search.ElasticsearchListingIndex searchIndex;

    public ListingPersistenceAdapter(ListingJpaRepository listingRepository, ListingEntityMapper mapper, com.company.bds.search.ElasticsearchListingIndex searchIndex) {
        this.listingRepository = listingRepository;
        this.mapper = mapper;
        this.searchIndex = searchIndex;
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
    public Optional<Listing> findBySlug(String slug) {
        return listingRepository.findBySlugWithRevisions(slug).map(mapper::toDomain);
    }

    @Override
    public boolean existsBySlug(String slug) { return listingRepository.existsBySlug(slug); }

    @Override
    public List<Listing> findByOwnerId(UUID ownerId) {
        return listingRepository.findByOwnerIdWithRevisions(ownerId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Listing> findAll(int page, int size) {
        List<UUID> ids = listingRepository.findAllListingIds(PageRequest.of(page, size));
        if (ids.isEmpty()) return List.of();
        Map<UUID, ListingJpaEntity> entities = listingRepository.findAllByIdWithRevisions(ids).stream()
                .collect(Collectors.toMap(ListingJpaEntity::getId, entity -> entity));
        return ids.stream().map(entities::get).filter(java.util.Objects::nonNull).map(mapper::toDomain).toList();
    }

    @Override
    public List<Listing> findPublicActiveListings(String purpose, int page, int size) {
        return hydrateInOrder(listingRepository.findPublicActiveListingIds(PageRequest.of(page, size)));
    }

    @Override
    public List<Listing> findPendingReviewListings() {
        return listingRepository.findPendingReviewListings().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Listing> searchListings(com.company.bds.listing.domain.model.ListingSearchCriteria criteria, int page, int size) {
        var elasticIds = searchIndex.search(criteria, page, size);
        if (elasticIds.isPresent()) return hydrateInOrder(elasticIds.get());
        String purpose = criteria.purpose() != null ? criteria.purpose().name() : null;
        String propertyType = criteria.propertyType() != null ? criteria.propertyType().name() : null;

        return hydrateInOrder(listingRepository.searchPublicActiveListingIds(
                purpose,
                propertyType,
                criteria.minPriceVnd(),
                criteria.maxPriceVnd(),
                criteria.minAreaM2(),
                criteria.maxAreaM2(),
                criteria.keyword() == null ? "" : criteria.keyword(),
                criteria.minLat(),
                criteria.maxLat(),
                criteria.minLng(),
                criteria.maxLng(),
                criteria.sortBy(),
                PageRequest.of(page, size)
        ));
    }

    private List<Listing> hydrateInOrder(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        Map<UUID, ListingJpaEntity> entities = listingRepository.findAllPublicActiveByIdWithRevisions(ids).stream()
                .collect(Collectors.toMap(ListingJpaEntity::getId, entity -> entity));
        return ids.stream().map(entities::get).filter(java.util.Objects::nonNull)
                .map(mapper::toDomain).collect(Collectors.toList());
    }
}

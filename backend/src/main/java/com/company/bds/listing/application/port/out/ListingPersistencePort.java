package com.company.bds.listing.application.port.out;

import com.company.bds.listing.domain.model.Listing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng ra (Output Port) tầng persistence theo kiến trúc Hexagonal/Onion.
 */
public interface ListingPersistencePort {
    Listing save(Listing listing);
    Optional<Listing> findById(UUID id);
    List<Listing> findByOwnerId(UUID ownerId);
    List<Listing> findPublicActiveListings(String purpose, int page, int size);
    List<Listing> findPendingReviewListings();
    List<Listing> searchListings(com.company.bds.listing.domain.model.ListingSearchCriteria criteria, int page, int size);
}

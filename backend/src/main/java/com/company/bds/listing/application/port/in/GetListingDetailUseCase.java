package com.company.bds.listing.application.port.in;

import com.company.bds.listing.domain.model.Listing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetListingDetailUseCase {
    Optional<Listing> getListingById(UUID id);
    List<Listing> getMyListings(UUID ownerId);
}

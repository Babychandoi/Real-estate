package com.company.bds.listing.infrastructure.persistence.repository;

import com.company.bds.listing.infrastructure.persistence.entity.ListingRevisionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ListingRevisionJpaRepository extends JpaRepository<ListingRevisionJpaEntity, UUID> {
}

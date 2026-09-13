package com.company.bds.lead.infrastructure.persistence.repository;

import com.company.bds.lead.infrastructure.persistence.entity.LeadJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import com.company.bds.lead.domain.model.LeadStatus;

@Repository
public interface LeadJpaRepository extends JpaRepository<LeadJpaEntity, UUID> {

    List<LeadJpaEntity> findByListingIdOrderByCreatedAtDesc(UUID listingId, Pageable pageable);

    @Query("SELECT l FROM LeadJpaEntity l WHERE l.listingId IN :listingIds ORDER BY l.createdAt DESC")
    List<LeadJpaEntity> findByListingIdInOrderByCreatedAtDesc(@Param("listingIds") List<UUID> listingIds, Pageable pageable);

    List<LeadJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByPhoneLookupHash(String phoneLookupHash);
    long countByStatusIn(List<LeadStatus> statuses);
}

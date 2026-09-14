package com.company.bds.lead.infrastructure.persistence.repository;

import com.company.bds.lead.infrastructure.persistence.entity.LeadJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
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

    @Query("""
            SELECT l FROM LeadJpaEntity l
            WHERE l.listingId IN :listingIds
              AND (:status IS NULL OR l.status = :status)
              AND (:keyword = '' OR LOWER(l.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(l.note, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY l.createdAt DESC
            """)
    Page<LeadJpaEntity> searchByListingIds(@Param("listingIds") List<UUID> listingIds,
            @Param("status") LeadStatus status, @Param("keyword") String keyword, Pageable pageable);

    @Query("""
            SELECT l FROM LeadJpaEntity l
            WHERE (:status IS NULL OR l.status = :status)
              AND (:keyword = '' OR LOWER(l.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(l.note, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY l.createdAt DESC
            """)
    Page<LeadJpaEntity> searchAll(@Param("status") LeadStatus status,
            @Param("keyword") String keyword, Pageable pageable);

    @Query("""
            SELECT l.status AS status, COUNT(l) AS total FROM LeadJpaEntity l
            WHERE l.listingId IN :listingIds
              AND (:keyword = '' OR LOWER(l.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(l.note, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            GROUP BY l.status
            """)
    List<LeadStatusTotal> countByListingIdsGroupedByStatus(@Param("listingIds") List<UUID> listingIds,
            @Param("keyword") String keyword);

    @Query("""
            SELECT l.status AS status, COUNT(l) AS total FROM LeadJpaEntity l
            WHERE (:keyword = '' OR LOWER(l.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(COALESCE(l.note, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            GROUP BY l.status
            """)
    List<LeadStatusTotal> countAllGroupedByStatus(@Param("keyword") String keyword);

    long countByPhoneLookupHashAndCreatedAtAfter(String phoneLookupHash, java.time.Instant since);
    long countByStatusIn(List<LeadStatus> statuses);
}

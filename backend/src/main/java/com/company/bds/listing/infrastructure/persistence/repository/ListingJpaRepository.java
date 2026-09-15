package com.company.bds.listing.infrastructure.persistence.repository;

import com.company.bds.listing.infrastructure.persistence.entity.ListingJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository cho Listing.
 * Fetch revisions không đồng thời fetch nhiều bags để tránh MultipleBagFetchException.
 */
@Repository
public interface ListingJpaRepository extends JpaRepository<ListingJpaEntity, UUID> {

    @Query("SELECT DISTINCT l FROM ListingJpaEntity l " +
           "LEFT JOIN FETCH l.revisions r " +
           "WHERE l.id = :id")
    Optional<ListingJpaEntity> findByIdWithRevisions(@Param("id") UUID id);

    @Query("SELECT DISTINCT l FROM ListingJpaEntity l LEFT JOIN FETCH l.revisions WHERE l.slug = :slug")
    Optional<ListingJpaEntity> findBySlugWithRevisions(@Param("slug") String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT l.id FROM ListingJpaEntity l ORDER BY l.updatedAt DESC, l.id DESC")
    List<UUID> findAllListingIds(Pageable pageable);

    @Query("SELECT DISTINCT l FROM ListingJpaEntity l LEFT JOIN FETCH l.revisions WHERE l.id IN :ids")
    List<ListingJpaEntity> findAllByIdWithRevisions(@Param("ids") List<UUID> ids);

    @Query("SELECT DISTINCT l FROM ListingJpaEntity l " +
           "LEFT JOIN FETCH l.revisions r " +
           "WHERE l.ownerId = :ownerId " +
           "ORDER BY l.createdAt DESC")
    List<ListingJpaEntity> findByOwnerIdWithRevisions(@Param("ownerId") UUID ownerId);

    @Query("SELECT l.id FROM ListingJpaEntity l " +
           "WHERE l.status = 'ACTIVE' " +
           "ORDER BY l.createdAt DESC")
    List<UUID> findPublicActiveListingIds(Pageable pageable);

    @Query("SELECT DISTINCT l FROM ListingJpaEntity l LEFT JOIN FETCH l.revisions " +
           "WHERE l.id IN :ids AND l.status = 'ACTIVE' AND l.publicRevisionId IS NOT NULL")
    List<ListingJpaEntity> findAllPublicActiveByIdWithRevisions(@Param("ids") List<UUID> ids);

    @Query("SELECT DISTINCT l FROM ListingJpaEntity l " +
           "LEFT JOIN FETCH l.revisions r " +
           "WHERE l.status = 'PENDING_REVIEW' " +
           "ORDER BY l.updatedAt ASC")
    List<ListingJpaEntity> findPendingReviewListings();

    @Query("SELECT l.id FROM ListingJpaEntity l " +
           "JOIN l.revisions r " +
           "WHERE l.status = 'ACTIVE' " +
           "AND r.id = l.publicRevisionId " +
           "AND (:purpose IS NULL OR r.purpose = :purpose) " +
           "AND (:propertyType IS NULL OR r.propertyType = :propertyType) " +
           "AND (:minPrice IS NULL OR r.priceVnd >= :minPrice) " +
           "AND (:maxPrice IS NULL OR r.priceVnd <= :maxPrice) " +
           "AND (:minArea IS NULL OR r.areaM2 >= :minArea) " +
           "AND (:maxArea IS NULL OR r.areaM2 <= :maxArea) " +
           "AND (:keyword = '' OR LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.addressSummary) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:minLat IS NULL OR (r.publicLatitude >= :minLat AND r.publicLatitude <= :maxLat AND r.publicLongitude >= :minLng AND r.publicLongitude <= :maxLng)) " +
           "ORDER BY " +
           "CASE WHEN :sortBy = 'PRICE_ASC' THEN r.priceVnd END ASC, " +
           "CASE WHEN :sortBy = 'PRICE_DESC' THEN r.priceVnd END DESC, " +
           "CASE WHEN :sortBy = 'AREA_DESC' THEN r.areaM2 END DESC, " +
           "l.createdAt DESC, l.id DESC")
    List<UUID> searchPublicActiveListingIds(
            @Param("purpose") String purpose,
            @Param("propertyType") String propertyType,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            @Param("minArea") java.math.BigDecimal minArea,
            @Param("maxArea") java.math.BigDecimal maxArea,
            @Param("keyword") String keyword,
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng,
            @Param("sortBy") String sortBy,
            Pageable pageable);
}

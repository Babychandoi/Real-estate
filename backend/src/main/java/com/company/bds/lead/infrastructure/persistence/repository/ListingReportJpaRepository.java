package com.company.bds.lead.infrastructure.persistence.repository;

import com.company.bds.lead.domain.model.ReportSeverity;
import com.company.bds.lead.domain.model.ReportStatus;
import com.company.bds.lead.infrastructure.persistence.entity.ListingReportJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ListingReportJpaRepository extends JpaRepository<ListingReportJpaEntity, UUID> {

    List<ListingReportJpaEntity> findAllByOrderByCreatedAtDesc();

    List<ListingReportJpaEntity> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<ListingReportJpaEntity> findBySeverityOrderByCreatedAtDesc(ReportSeverity severity);

    List<ListingReportJpaEntity> findByListingIdOrderByCreatedAtDesc(UUID listingId);

    Optional<ListingReportJpaEntity> findByCaseNumber(String caseNumber);

    long countByStatus(ReportStatus status);
}

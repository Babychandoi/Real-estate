package com.company.bds.lead.domain.port;

import com.company.bds.lead.domain.model.ListingReport;
import com.company.bds.lead.domain.model.ReportSeverity;
import com.company.bds.lead.domain.model.ReportStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingReportPersistencePort {
    ListingReport save(ListingReport report);
    Optional<ListingReport> findById(UUID id);
    Optional<ListingReport> findByCaseNumber(String caseNumber);
    List<ListingReport> findAll();
    List<ListingReport> findByStatus(ReportStatus status);
    List<ListingReport> findBySeverity(ReportSeverity severity);
    List<ListingReport> findByListingId(UUID listingId);
    long countByStatus(ReportStatus status);
}

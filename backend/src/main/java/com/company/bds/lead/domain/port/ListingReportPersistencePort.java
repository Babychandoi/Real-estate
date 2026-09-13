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
    List<ListingReport> findPage(int page, int size);
    List<ListingReport> findByStatus(ReportStatus status, int page, int size);
    List<ListingReport> findBySeverity(ReportSeverity severity, int page, int size);
    List<ListingReport> findByListingId(UUID listingId, int page, int size);
    long countByStatus(ReportStatus status);
}

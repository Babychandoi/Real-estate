package com.company.bds.lead.api.response;

import com.company.bds.lead.domain.model.ListingReport;
import com.company.bds.lead.domain.model.ReportCategory;
import com.company.bds.lead.domain.model.ReportSeverity;
import com.company.bds.lead.domain.model.ReportStatus;

import java.time.Instant;
import java.util.UUID;

public record ListingReportResponse(
        UUID id,
        UUID listingId,
        String caseNumber,
        String reporterType,
        String reporterPhone,
        ReportCategory category,
        ReportSeverity severity,
        ReportStatus status,
        String description,
        String evidenceUrls,
        String resolutionNote,
        Instant createdAt,
        Instant resolvedAt
) {
    public static ListingReportResponse fromDomain(ListingReport report) {
        return new ListingReportResponse(
                report.getId(),
                report.getListingId(),
                report.getCaseNumber(),
                report.getReporterType(),
                report.getReporterPhone(),
                report.getCategory(),
                report.getSeverity(),
                report.getStatus(),
                report.getDescription(),
                report.getEvidenceUrls(),
                report.getResolutionNote(),
                report.getCreatedAt(),
                report.getResolvedAt()
        );
    }
}

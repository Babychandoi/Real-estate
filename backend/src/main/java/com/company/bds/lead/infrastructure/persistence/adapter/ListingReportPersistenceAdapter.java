package com.company.bds.lead.infrastructure.persistence.adapter;

import com.company.bds.lead.domain.model.ListingReport;
import com.company.bds.lead.domain.model.ReportSeverity;
import com.company.bds.lead.domain.model.ReportStatus;
import com.company.bds.lead.domain.port.ListingReportPersistencePort;
import com.company.bds.lead.infrastructure.persistence.entity.ListingReportJpaEntity;
import com.company.bds.lead.infrastructure.persistence.repository.ListingReportJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ListingReportPersistenceAdapter implements ListingReportPersistencePort {

    private final ListingReportJpaRepository reportJpaRepository;

    public ListingReportPersistenceAdapter(ListingReportJpaRepository reportJpaRepository) {
        this.reportJpaRepository = reportJpaRepository;
    }

    @Override
    public ListingReport save(ListingReport report) {
        ListingReportJpaEntity entity = toEntity(report);
        ListingReportJpaEntity saved = reportJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<ListingReport> findById(UUID id) {
        return reportJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ListingReport> findByCaseNumber(String caseNumber) {
        return reportJpaRepository.findByCaseNumber(caseNumber).map(this::toDomain);
    }

    @Override
    public List<ListingReport> findAll() {
        return reportJpaRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ListingReport> findByStatus(ReportStatus status) {
        return reportJpaRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ListingReport> findBySeverity(ReportSeverity severity) {
        return reportJpaRepository.findBySeverityOrderByCreatedAtDesc(severity)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ListingReport> findByListingId(UUID listingId) {
        return reportJpaRepository.findByListingIdOrderByCreatedAtDesc(listingId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countByStatus(ReportStatus status) {
        return reportJpaRepository.countByStatus(status);
    }

    private ListingReportJpaEntity toEntity(ListingReport domain) {
        return new ListingReportJpaEntity(
                domain.getId(),
                domain.getListingId(),
                domain.getCaseNumber(),
                domain.getReporterType(),
                domain.getReporterPhone(),
                domain.getCategory(),
                domain.getSeverity(),
                domain.getStatus(),
                domain.getDescription(),
                domain.getEvidenceUrls(),
                domain.getResolutionNote(),
                domain.getCreatedAt(),
                domain.getResolvedAt()
        );
    }

    private ListingReport toDomain(ListingReportJpaEntity entity) {
        return new ListingReport(
                entity.getId(),
                entity.getListingId(),
                entity.getCaseNumber(),
                entity.getReporterType(),
                entity.getReporterPhone(),
                entity.getCategory(),
                entity.getSeverity(),
                entity.getStatus(),
                entity.getDescription(),
                entity.getEvidenceUrls(),
                entity.getResolutionNote(),
                entity.getCreatedAt(),
                entity.getResolvedAt()
        );
    }
}

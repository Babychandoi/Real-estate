package com.company.bds.lead.infrastructure.persistence.entity;

import com.company.bds.lead.domain.model.ReportCategory;
import com.company.bds.lead.domain.model.ReportSeverity;
import com.company.bds.lead.domain.model.ReportStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity biểu diễn bảng listing_reports (Vụ việc Báo xấu & Khiếu nại Tin đăng).
 */
@Entity
@Table(name = "listing_reports")
public class ListingReportJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "case_number", nullable = false, length = 30, unique = true)
    private String caseNumber;

    @Column(name = "reporter_type", nullable = false, length = 30)
    private String reporterType;

    @Column(name = "reporter_phone", length = 50)
    private String reporterPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ReportCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private ReportSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReportStatus status;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "evidence_urls", columnDefinition = "TEXT")
    private String evidenceUrls;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public ListingReportJpaEntity() {}

    public ListingReportJpaEntity(
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
            Instant resolvedAt) {
        this.id = id;
        this.listingId = listingId;
        this.caseNumber = caseNumber;
        this.reporterType = reporterType;
        this.reporterPhone = reporterPhone;
        this.category = category;
        this.severity = severity;
        this.status = status;
        this.description = description;
        this.evidenceUrls = evidenceUrls;
        this.resolutionNote = resolutionNote;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.resolvedAt = resolvedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }
    public String getCaseNumber() { return caseNumber; }
    public void setCaseNumber(String caseNumber) { this.caseNumber = caseNumber; }
    public String getReporterType() { return reporterType; }
    public void setReporterType(String reporterType) { this.reporterType = reporterType; }
    public String getReporterPhone() { return reporterPhone; }
    public void setReporterPhone(String reporterPhone) { this.reporterPhone = reporterPhone; }
    public ReportCategory getCategory() { return category; }
    public void setCategory(ReportCategory category) { this.category = category; }
    public ReportSeverity getSeverity() { return severity; }
    public void setSeverity(ReportSeverity severity) { this.severity = severity; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEvidenceUrls() { return evidenceUrls; }
    public void setEvidenceUrls(String evidenceUrls) { this.evidenceUrls = evidenceUrls; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}

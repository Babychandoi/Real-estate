package com.company.bds.lead.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root quản lý Vụ việc Báo xấu & Khiếu nại Tin đăng (Violation Reports Desk).
 * Tuân thủ quy chuẩn FR21, FR27, FR31, UC04.
 */
public class ListingReport {

    private final UUID id;
    private final UUID listingId;
    private final String caseNumber;
    private final String reporterType;
    private final String reporterPhone;
    private final ReportCategory category;
    private final ReportSeverity severity;
    private ReportStatus status;
    private final String description;
    private String evidenceUrls;
    private String resolutionNote;
    private final Instant createdAt;
    private Instant resolvedAt;

    public ListingReport(
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
        this.id = id != null ? id : UUID.randomUUID();
        this.listingId = Objects.requireNonNull(listingId, "listingId không được để trống");
        this.caseNumber = caseNumber != null ? caseNumber : "CASE-" + (System.currentTimeMillis() % 100000);
        this.reporterType = reporterType != null ? reporterType : "ANONYMOUS";
        this.reporterPhone = reporterPhone;
        this.category = category != null ? category : ReportCategory.OTHER;
        this.severity = severity != null ? severity : ReportSeverity.MEDIUM;
        this.status = status != null ? status : ReportStatus.PENDING;
        this.description = Objects.requireNonNull(description, "Mô tả vi phạm không được để trống");
        this.evidenceUrls = evidenceUrls;
        this.resolutionNote = resolutionNote;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.resolvedAt = resolvedAt;
    }

    public static ListingReport create(
            UUID listingId,
            ReportCategory category,
            ReportSeverity severity,
            String description,
            String evidenceUrls,
            String reporterPhone,
            Instant now) {
        String caseNum = "CASE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new ListingReport(
                UUID.randomUUID(),
                listingId,
                caseNum,
                "ANONYMOUS",
                reporterPhone,
                category,
                severity,
                ReportStatus.PENDING,
                description,
                evidenceUrls,
                null,
                now,
                null
        );
    }

    public void resolve(String note, Instant now) {
        this.status = ReportStatus.RESOLVED;
        this.resolutionNote = note;
        this.resolvedAt = now;
    }

    public void dismiss(String note, Instant now) {
        this.status = ReportStatus.DISMISSED;
        this.resolutionNote = note;
        this.resolvedAt = now;
    }

    public void markWaitingReply() {
        this.status = ReportStatus.WAITING_REPLY;
    }

    public void appeal(String newEvidence, Instant now) {
        this.status = ReportStatus.APPEALED;
        if (newEvidence != null && !newEvidence.isBlank()) {
            this.evidenceUrls = (this.evidenceUrls != null ? this.evidenceUrls + ";" : "") + newEvidence;
        }
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public String getCaseNumber() { return caseNumber; }
    public String getReporterType() { return reporterType; }
    public String getReporterPhone() { return reporterPhone; }
    public ReportCategory getCategory() { return category; }
    public ReportSeverity getSeverity() { return severity; }
    public ReportStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getEvidenceUrls() { return evidenceUrls; }
    public String getResolutionNote() { return resolutionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}

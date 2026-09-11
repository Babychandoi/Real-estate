package com.company.bds.lead.application;

import com.company.bds.lead.domain.model.ListingReport;
import com.company.bds.lead.domain.model.ReportCategory;
import com.company.bds.lead.domain.model.ReportSeverity;
import com.company.bds.lead.domain.model.ReportStatus;
import com.company.bds.lead.domain.port.ListingReportPersistencePort;
import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.model.Listing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ViolationReportApplicationService {

    private final ListingReportPersistencePort reportPersistencePort;
    private final ListingPersistencePort listingPersistencePort;

    public ViolationReportApplicationService(
            ListingReportPersistencePort reportPersistencePort,
            ListingPersistencePort listingPersistencePort) {
        this.reportPersistencePort = reportPersistencePort;
        this.listingPersistencePort = listingPersistencePort;
    }

    /**
     * Tiếp nhận Báo xấu từ người dùng hoặc người xem tin (FR21, FR27).
     * Đặc biệt: Với vi phạm P0 khẩn cấp (Lừa cọc, giả mạo chủ nhà), hệ thống tự động tạm ẩn tin ngay lập tức.
     */
    public ListingReport submitReport(
            UUID listingId,
            ReportCategory category,
            ReportSeverity severity,
            String description,
            String evidenceUrls,
            String reporterPhone) {

        Listing listing = listingPersistencePort.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Tin đăng không tồn tại ID: " + listingId));

        Instant now = Instant.now();
        ListingReport report = ListingReport.create(
                listingId,
                category,
                severity,
                description,
                evidenceUrls,
                reporterPhone,
                now
        );

        // Kích hoạt cơ chế an toàn khẩn cấp P0 (FR27): Tự động tạm ẩn tin ngăn chặn lừa đảo
        if (severity == ReportSeverity.P0_EMERGENCY) {
            listing.pause(now);
            listingPersistencePort.save(listing);
        }

        return reportPersistencePort.save(report);
    }

    /**
     * Bàn điều phối xử lý: Tạm ẩn tin khẩn cấp từ giao diện bàn giải quyết vi phạm (FR27).
     */
    public ListingReport emergencyHideListing(UUID reportId, String reason) {
        ListingReport report = reportPersistencePort.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vụ việc ID: " + reportId));

        Listing listing = listingPersistencePort.findById(report.getListingId())
                .orElseThrow(() -> new IllegalArgumentException("Tin đăng liên quan không tồn tại"));

        Instant now = Instant.now();
        listing.pause(now);
        listingPersistencePort.save(listing);

        report.markWaitingReply();
        return reportPersistencePort.save(report);
    }

    /**
     * Hoàn tất giải quyết vi phạm (Xác thực vi phạm, khóa tin hoặc đóng hồ sơ).
     */
    public ListingReport resolveReport(
            UUID reportId,
            String resolutionNote,
            boolean permanentlyLockListing) {

        ListingReport report = reportPersistencePort.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vụ việc ID: " + reportId));

        Instant now = Instant.now();
        if (permanentlyLockListing) {
            Listing listing = listingPersistencePort.findById(report.getListingId()).orElse(null);
            if (listing != null) {
                listing.lock(now);
                listingPersistencePort.save(listing);
            }
        }

        report.resolve(resolutionNote, now);
        return reportPersistencePort.save(report);
    }

    /**
     * Bác bỏ báo xấu (Báo xấu sai sự thật hoặc cạnh tranh không lành mạnh).
     * Phục hồi lại tin đăng nếu trước đó bị tạm ẩn.
     */
    public ListingReport dismissReport(UUID reportId, String dismissNote, boolean resumeListing) {
        ListingReport report = reportPersistencePort.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vụ việc ID: " + reportId));

        Instant now = Instant.now();
        if (resumeListing) {
            Listing listing = listingPersistencePort.findById(report.getListingId()).orElse(null);
            if (listing != null) {
                listing.resume(now);
                listingPersistencePort.save(listing);
            }
        }

        report.dismiss(dismissNote, now);
        return reportPersistencePort.save(report);
    }

    /**
     * Môi giới/Chủ tin gửi giải trình khiếu nại (FR27, UC04).
     */
    public ListingReport appealReport(UUID reportId, String newEvidence) {
        ListingReport report = reportPersistencePort.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vụ việc ID: " + reportId));

        Instant now = Instant.now();
        report.appeal(newEvidence, now);
        return reportPersistencePort.save(report);
    }

    /**
     * Danh sách tất cả các vụ việc báo xấu phục vụ bàn xử lý.
     */
    @Transactional(readOnly = true)
    public List<ListingReport> getReports(ReportStatus status, ReportSeverity severity) {
        if (status != null) {
            return reportPersistencePort.findByStatus(status);
        }
        if (severity != null) {
            return reportPersistencePort.findBySeverity(severity);
        }
        return reportPersistencePort.findAll();
    }

    @Transactional(readOnly = true)
    public ListingReport getReportById(UUID reportId) {
        return reportPersistencePort.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy báo xấu ID: " + reportId));
    }
}

package com.company.bds.lead.api;

import com.company.bds.lead.api.request.AppealReportRequest;
import com.company.bds.lead.api.request.DismissReportRequest;
import com.company.bds.lead.api.request.ResolveReportRequest;
import com.company.bds.lead.api.request.SubmitReportRequest;
import com.company.bds.lead.api.response.ListingReportResponse;
import com.company.bds.lead.application.ViolationReportApplicationService;
import com.company.bds.lead.domain.model.ListingReport;
import com.company.bds.lead.domain.model.ReportSeverity;
import com.company.bds.lead.domain.model.ReportStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller cho Bàn xử lý Báo xấu & Khiếu nại Vi phạm Tin đăng (FR27, UC04).
 */
@RestController
@RequestMapping("/api/v1")
public class ViolationReportController {

    private final ViolationReportApplicationService reportApplicationService;

    public ViolationReportController(ViolationReportApplicationService reportApplicationService) {
        this.reportApplicationService = reportApplicationService;
    }

    /**
     * Tiếp nhận Báo xấu vi phạm từ người dùng/người xem tin (Public API).
     */
    @PostMapping("/public/reports")
    public ResponseEntity<ListingReportResponse> submitReport(@Valid @RequestBody SubmitReportRequest request) {
        ListingReport report = reportApplicationService.submitReport(
                request.listingId(),
                request.category(),
                request.severity() != null ? request.severity() : ReportSeverity.MEDIUM,
                request.description(),
                request.evidenceUrls(),
                request.reporterPhone()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ListingReportResponse.fromDomain(report));
    }

    /**
     * Bàn kiểm duyệt/Điều phối: Lấy danh sách các vụ việc báo xấu theo bộ lọc.
     */
    @GetMapping("/reports")
    public ResponseEntity<List<ListingReportResponse>> getReports(
            @RequestParam(name = "status", required = false) ReportStatus status,
            @RequestParam(name = "severity", required = false) ReportSeverity severity,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {

        List<ListingReport> reports = reportApplicationService.getReports(
                status, severity, Math.max(0, page), Math.max(1, Math.min(100, size)));
        List<ListingReportResponse> response = reports.stream()
                .map(ListingReportResponse::fromDomain)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Xem chi tiết một vụ việc báo xấu.
     */
    @GetMapping("/reports/{id}")
    public ResponseEntity<ListingReportResponse> getReportById(@PathVariable("id") UUID id) {
        ListingReport report = reportApplicationService.getReportById(id);
        return ResponseEntity.ok(ListingReportResponse.fromDomain(report));
    }

    /**
     * Tạm ẩn tin đăng khẩn cấp từ Bàn xử lý sự cố (FR27).
     */
    @PostMapping("/reports/{id}/emergency-hide")
    public ResponseEntity<ListingReportResponse> emergencyHideListing(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) Map<String, String> body) {

        String reason = body != null ? body.getOrDefault("reason", "Khẩn cấp: Tạm ẩn để xác minh dấu hiệu vi phạm") : "Tạm ẩn khẩn cấp";
        ListingReport report = reportApplicationService.emergencyHideListing(id, reason);
        return ResponseEntity.ok(ListingReportResponse.fromDomain(report));
    }

    /**
     * Hoàn tất giải quyết vụ việc (Khóa tin / Đóng hồ sơ vi phạm).
     */
    @PostMapping("/reports/{id}/resolve")
    public ResponseEntity<ListingReportResponse> resolveReport(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ResolveReportRequest request) {

        ListingReport report = reportApplicationService.resolveReport(
                id,
                request.resolutionNote(),
                request.permanentlyLockListing()
        );
        return ResponseEntity.ok(ListingReportResponse.fromDomain(report));
    }

    /**
     * Bác bỏ báo xấu sai sự thật và phục hồi lại tin đăng.
     */
    @PostMapping("/reports/{id}/dismiss")
    public ResponseEntity<ListingReportResponse> dismissReport(
            @PathVariable("id") UUID id,
            @Valid @RequestBody DismissReportRequest request) {

        ListingReport report = reportApplicationService.dismissReport(
                id,
                request.dismissNote(),
                request.resumeListing()
        );
        return ResponseEntity.ok(ListingReportResponse.fromDomain(report));
    }

    /**
     * Môi giới gửi giải trình/khiếu nại kèm bằng chứng đối soát.
     */
    @PostMapping("/reports/{id}/appeal")
    public ResponseEntity<ListingReportResponse> appealReport(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AppealReportRequest request) {

        ListingReport report = reportApplicationService.appealReport(
                id,
                request.newEvidence()
        );
        return ResponseEntity.ok(ListingReportResponse.fromDomain(report));
    }
}

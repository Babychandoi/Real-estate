package com.company.bds.lead.api;

import com.company.bds.lead.api.request.AppealReportRequest;
import com.company.bds.lead.api.request.DismissReportRequest;
import com.company.bds.lead.api.request.ResolveReportRequest;
import com.company.bds.lead.api.request.SubmitReportRequest;
import com.company.bds.lead.api.response.ListingReportResponse;
import com.company.bds.lead.application.ReportActionService;
import com.company.bds.lead.application.ReportDeskService;
import com.company.bds.lead.application.ViolationReportApplicationService;
import com.company.bds.shared.security.CurrentUser;
import org.springframework.security.core.Authentication;
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
    private final ReportDeskService desk;
    private final ReportActionService actions;

    public ViolationReportController(ViolationReportApplicationService reportApplicationService, ReportDeskService desk,
                                     ReportActionService actions) {
        this.reportApplicationService = reportApplicationService;
        this.desk = desk;
        this.actions = actions;
    }

    /**
     * Tiếp nhận Báo xấu vi phạm từ người dùng/người xem tin (Public API).
     */
    @PostMapping("/public/reports")
    public ResponseEntity<ListingReportResponse> submitReport(@Valid @RequestBody SubmitReportRequest request) {
        ListingReport report = reportApplicationService.submitReport(
                request.listingId(),
                request.category(),
                ReportSeverity.MEDIUM,
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

    /** Staff queue: open cases by SLA due time (P0 1 h, HIGH 4 h, MEDIUM 24 h, LOW 72 h), claims, owner outcome. */
    @GetMapping("/reports/queue")
    public ReportDeskService.QueuePage queue(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) String severity,
                                             @RequestParam(required = false) Boolean breached,
                                             @RequestParam(defaultValue = "false") boolean mine,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             Authentication authentication) {
        return desk.queue(status, severity, breached, mine, CurrentUser.id(authentication), page, size);
    }

    @PostMapping("/reports/{id}/claim")
    public ReportDeskService.Claim claim(@PathVariable UUID id, Authentication authentication) {
        return desk.claim(id, CurrentUser.id(authentication));
    }

    @DeleteMapping("/reports/{id}/claim")
    public ResponseEntity<Void> release(@PathVariable UUID id, Authentication authentication) {
        desk.release(id, CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reports/{id}/events")
    public List<ReportDeskService.Event> events(@PathVariable UUID id) {
        return desk.events(id);
    }

    @PostMapping("/reports/{id}/notes")
    public ResponseEntity<Void> addNote(@PathVariable UUID id, @RequestBody Map<String, String> body, Authentication authentication) {
        String note = body == null ? null : body.get("note");
        if (note == null || note.isBlank()) throw new IllegalArgumentException("Ghi chú không được để trống.");
        desk.assertActionable(id, CurrentUser.id(authentication));
        desk.recordEvent(id, "NOTE", CurrentUser.id(authentication), note, null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reports/{id}/severity")
    public ResponseEntity<Void> escalate(@PathVariable UUID id, @RequestBody Map<String, String> body, Authentication authentication) {
        desk.escalate(id, body == null ? null : body.get("severity"), body == null ? null : body.get("reason"), CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    /**
     * Tạm ẩn tin đăng khẩn cấp từ Bàn xử lý sự cố (FR27).
     */
    @PostMapping("/reports/{id}/emergency-hide")
    public ResponseEntity<ListingReportResponse> emergencyHideListing(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {

        String reason = body != null ? body.getOrDefault("reason", "Khẩn cấp: Tạm ẩn để xác minh dấu hiệu vi phạm") : "Tạm ẩn khẩn cấp";
        ListingReport report = actions.emergencyHide(id, reason, CurrentUser.id(authentication));
        return ResponseEntity.ok(ListingReportResponse.fromDomain(report));
    }

    /**
     * Hoàn tất giải quyết vụ việc (Khóa tin / Đóng hồ sơ vi phạm).
     */
    @PostMapping("/reports/{id}/resolve")
    public ResponseEntity<ListingReportResponse> resolveReport(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ResolveReportRequest request,
            Authentication authentication) {

        ListingReport report = actions.resolve(id, request.resolutionNote(), request.permanentlyLockListing(),
                CurrentUser.id(authentication));
        return ResponseEntity.ok(ListingReportResponse.fromDomain(report));
    }

    /**
     * Bác bỏ báo xấu sai sự thật và phục hồi lại tin đăng.
     */
    @PostMapping("/reports/{id}/dismiss")
    public ResponseEntity<ListingReportResponse> dismissReport(
            @PathVariable("id") UUID id,
            @Valid @RequestBody DismissReportRequest request,
            Authentication authentication) {

        ListingReport report = actions.dismiss(id, request.dismissNote(), request.resumeListing(), CurrentUser.id(authentication));
        return ResponseEntity.ok(ListingReportResponse.fromDomain(report));
    }

    /**
     * Môi giới gửi giải trình/khiếu nại kèm bằng chứng đối soát.
     */
    @PostMapping("/reports/{id}/appeal")
    public ResponseEntity<ListingReportResponse> appealReport(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AppealReportRequest request,
            Authentication authentication) {

        ListingReport report = actions.appeal(id, request.newEvidence(), CurrentUser.id(authentication));
        return ResponseEntity.ok(ListingReportResponse.fromDomain(report));
    }
}

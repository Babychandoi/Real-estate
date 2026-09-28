package com.company.bds.analytics.api;

import com.company.bds.analytics.application.AnalyticsDashboardService;
import com.company.bds.analytics.application.InvalidEventsException;
import com.company.bds.shared.error.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Staff analytics dashboard ({@code /api/v1/analytics/**} is ADMIN/MODERATOR in SecurityConfig, never cached). */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsDashboardController {
    private final AnalyticsDashboardService dashboard;

    public AnalyticsDashboardController(AnalyticsDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    /** {@code from}/{@code to}: inclusive Vietnam calendar days (≤ 92 days; default the last 28 days). */
    @GetMapping("/dashboard")
    public ResponseEntity<AnalyticsDashboardService.Dashboard> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String device,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String source) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(dashboard.dashboard(new AnalyticsDashboardService.DashboardRequest(from, to, device, area, source)));
    }

    @ExceptionHandler(InvalidEventsException.class)
    ResponseEntity<ProblemDetails> invalid(InvalidEventsException ex, HttpServletRequest request) {
        List<ProblemDetails.ValidationErrorItem> errors = ex.violations().stream()
                .map(violation -> new ProblemDetails.ValidationErrorItem(violation.field(), violation.code(), violation.message()))
                .toList();
        ProblemDetails body = new ProblemDetails(URI.create("https://api.bds.vn/problems/invalid-filter"), "Bộ lọc không hợp lệ",
                400, "Kiểm tra lại khoảng thời gian và bộ lọc.", request.getRequestURI(), "INVALID_FILTER",
                UUID.randomUUID().toString(), errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}

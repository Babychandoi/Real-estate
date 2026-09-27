package com.company.bds.analytics.api;

import com.company.bds.analytics.application.EventIngestionService;
import com.company.bds.analytics.application.InvalidEventsException;
import com.company.bds.shared.error.ProblemDetails;
import com.company.bds.shared.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Public product-analytics ingestion (contract §5). Accepts {@code application/json} and {@code text/plain} (what
 * {@code navigator.sendBeacon} sends for a string) up to {@value #MAX_BODY_BYTES} bytes. The user id comes only from a valid
 * bearer token; rate limiting is applied by RequestRateLimitFilter (policy owned by stream S5).
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventIngestionController {
    static final int MAX_BODY_BYTES = 64 * 1024;
    private static final String PROBLEM_BASE = "https://api.bds.vn/problems/";

    private final EventIngestionService ingestion;
    private final ObjectMapper json;

    public EventIngestionController(EventIngestionService ingestion, ObjectMapper json) {
        this.ingestion = ingestion;
        this.json = json;
    }

    @PostMapping
    public ResponseEntity<EventIngestionService.IngestionResult> ingest(HttpServletRequest request, Authentication authentication)
            throws IOException {
        if (!supported(request.getContentType())) {
            throw new PayloadRejected(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                    "Chỉ nhận application/json hoặc text/plain.");
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw new PayloadRejected(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                    "Lô sự kiện vượt quá " + (MAX_BODY_BYTES / 1024) + " KB.");
        }
        JsonNode batch;
        try {
            batch = json.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new InvalidEventsException(List.of(new ProblemDetails.ValidationErrorItem("body", "MALFORMED_JSON", "Nội dung không phải JSON hợp lệ.")));
        }
        EventIngestionService.Viewer viewer = new EventIngestionService.Viewer(userId(authentication), staff(authentication),
                request.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(ingestion.ingest(batch, viewer));
    }

    @ExceptionHandler(InvalidEventsException.class)
    ResponseEntity<ProblemDetails> invalid(InvalidEventsException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_EVENTS", "Sự kiện không hợp lệ",
                "Lô sự kiện bị từ chối: " + ex.errors().size() + " lỗi, không sự kiện nào được lưu.", request, ex.errors());
    }

    @ExceptionHandler(PayloadRejected.class)
    ResponseEntity<ProblemDetails> rejected(PayloadRejected ex, HttpServletRequest request) {
        return problem(ex.status, ex.code, "Không thể nhận sự kiện", ex.getMessage(), request, null);
    }

    private static ResponseEntity<ProblemDetails> problem(HttpStatus status, String code, String title, String detail,
                                                          HttpServletRequest request, List<ProblemDetails.ValidationErrorItem> errors) {
        ProblemDetails body = new ProblemDetails(URI.create(PROBLEM_BASE + code.toLowerCase().replace('_', '-')), title, status.value(),
                detail, request.getRequestURI(), code, UUID.randomUUID().toString(), errors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private static boolean supported(String contentType) {
        if (contentType == null) return false;
        try {
            MediaType type = MediaType.parseMediaType(contentType);
            return type.isCompatibleWith(MediaType.APPLICATION_JSON) || type.isCompatibleWith(MediaType.TEXT_PLAIN);
        } catch (InvalidMediaTypeException ex) {
            return false;
        }
    }

    private static UUID userId(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken || !authentication.isAuthenticated()) return null;
        try {
            return CurrentUser.id(authentication);
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    private static boolean staff(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN") || authority.getAuthority().equals("ROLE_MODERATOR"));
    }

    static final class PayloadRejected extends RuntimeException {
        private final HttpStatus status;
        private final String code;

        PayloadRejected(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}

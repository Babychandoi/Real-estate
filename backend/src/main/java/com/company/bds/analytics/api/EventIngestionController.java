package com.company.bds.analytics.api;

import com.company.bds.analytics.application.ConsentService;
import com.company.bds.analytics.application.EventIngestionService;
import com.company.bds.analytics.application.EventViolation;
import com.company.bds.analytics.application.InvalidEventsException;
import com.company.bds.analytics.domain.InternalNetworks;
import com.company.bds.shared.error.ProblemDetails;
import com.company.bds.shared.security.ClientIpResolver;
import com.company.bds.shared.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * {@code navigator.sendBeacon} sends for a string); every request is capped at {@value #MAX_BODY_BYTES} bytes and
 * {@value EventIngestionService#MAX_EVENTS} events. The user id comes only from a valid bearer token.
 *
 * <p>Abuse protection: this endpoint writes to the database for anonymous callers, so the per-client request rate must
 * come from the rate-limit policy for {@code POST /api/v1/events} (RequestRateLimitFilter, owned by stream S5). Until that
 * policy is deployed keep {@code app.analytics.ingestion.enabled=false} (env {@code APP_ANALYTICS_INGESTION_ENABLED},
 * default false): the endpoint then answers 503 {@code ANALYTICS_INGESTION_DISABLED} without reading the body.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventIngestionController {
    static final int MAX_BODY_BYTES = 64 * 1024;
    static final int MAX_CONSENT_BYTES = 2 * 1024;
    private static final String PROBLEM_BASE = "https://api.bds.vn/problems/";

    private final EventIngestionService ingestion;
    private final ConsentService consents;
    private final ObjectMapper json;
    private final boolean enabled;
    private final ClientIpResolver clientIp;
    private final InternalNetworks internalNetworks;

    @Autowired
    public EventIngestionController(EventIngestionService ingestion, ConsentService consents, ObjectMapper json,
                                    @Value("${app.analytics.ingestion.enabled:false}") boolean enabled,
                                    ClientIpResolver clientIp,
                                    @Value("${app.analytics.internal-networks:}") String internalNetworks) {
        this.ingestion = ingestion;
        this.consents = consents;
        this.json = json;
        this.enabled = enabled;
        this.clientIp = clientIp;
        this.internalNetworks = InternalNetworks.parse(internalNetworks);
    }

    /** Without internal networks (tests and standalone setups). */
    public EventIngestionController(EventIngestionService ingestion, ObjectMapper json, boolean enabled) {
        this(ingestion, null, json, enabled, new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED_PROXIES), "");
    }

    @PostMapping
    public ResponseEntity<EventIngestionService.IngestionResult> ingest(HttpServletRequest request, Authentication authentication)
            throws IOException {
        if (!enabled) {
            throw new PayloadRejected(HttpStatus.SERVICE_UNAVAILABLE, "ANALYTICS_INGESTION_DISABLED",
                    "Hệ thống tạm dừng nhận sự kiện phân tích; lô sự kiện không được lưu.");
        }
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
            throw new InvalidEventsException(List.of(new EventViolation("body", "MALFORMED_JSON", "Nội dung không phải JSON hợp lệ.")));
        }
        EventIngestionService.Viewer viewer = new EventIngestionService.Viewer(userId(authentication), staff(authentication),
                request.getHeader(HttpHeaders.USER_AGENT), internalNetworks.contains(clientIp.resolve(request)));
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(ingestion.ingest(batch, viewer));
    }

    /**
     * Records one analytics consent decision (granted or withdrawn) as proof of consent. Public: the banner asks
     * before sign-in; the user id comes only from a bearer token. Rate limited by policy {@code analytics-consent}.
     */
    @PostMapping("/consent")
    public ResponseEntity<ConsentService.ConsentReceipt> consent(HttpServletRequest request, Authentication authentication)
            throws IOException {
        if (!supported(request.getContentType()) || MediaType.parseMediaType(request.getContentType()).isCompatibleWith(MediaType.TEXT_PLAIN)) {
            throw new PayloadRejected(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Chỉ nhận application/json.");
        }
        byte[] body = request.getInputStream().readNBytes(MAX_CONSENT_BYTES + 1);
        if (body.length > MAX_CONSENT_BYTES) {
            throw new PayloadRejected(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Nội dung quá lớn.");
        }
        JsonNode node;
        try {
            node = json.readTree(body);
        } catch (JsonProcessingException ex) {
            node = null;
        }
        if (node == null || !node.isObject()) {
            throw new InvalidEventsException(List.of(new EventViolation("body", "MALFORMED_JSON", "Nội dung không phải JSON hợp lệ.")));
        }
        ConsentService.ConsentCommand command = new ConsentService.ConsentCommand(text(node, "consentId"), text(node, "purpose"),
                text(node, "choice"), text(node, "policyVersion"), text(node, "source"));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(consents.record(command, userId(authentication)));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    @ExceptionHandler(InvalidEventsException.class)
    ResponseEntity<ProblemDetails> invalid(InvalidEventsException ex, HttpServletRequest request) {
        List<ProblemDetails.ValidationErrorItem> errors = ex.violations().stream()
                .map(violation -> new ProblemDetails.ValidationErrorItem(violation.field(), violation.code(), violation.message()))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "INVALID_EVENTS", "Sự kiện không hợp lệ",
                "Lô sự kiện bị từ chối: " + errors.size() + " lỗi, không sự kiện nào được lưu.", request, errors);
    }

    @ExceptionHandler(PayloadRejected.class)
    ResponseEntity<ProblemDetails> rejected(PayloadRejected ex, HttpServletRequest request) {
        return problem(ex.status, ex.code, "Không thể nhận sự kiện", ex.getMessage(), request, null);
    }

    private static ResponseEntity<ProblemDetails> problem(HttpStatus status, String code, String title, String detail,
                                                          HttpServletRequest request, List<ProblemDetails.ValidationErrorItem> errors) {
        ProblemDetails body = new ProblemDetails(URI.create(PROBLEM_BASE + code.toLowerCase().replace('_', '-')), title, status.value(),
                detail, request.getRequestURI(), code, UUID.randomUUID().toString(), errors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).cacheControl(CacheControl.noStore()).body(body);
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

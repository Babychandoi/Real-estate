package com.company.bds.shared.security;

import com.company.bds.shared.error.ProblemDetails;
import com.company.bds.shared.security.ratelimit.RateLimitDecision;
import com.company.bds.shared.security.ratelimit.RateLimitPolicies;
import com.company.bds.shared.security.ratelimit.RateLimitPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Problem Details responses and policy lookup shared by the two rate-limit filters. */
final class RateLimitResponses {
    private static final URI RATE_LIMITED = URI.create("https://api.bds.vn/problems/rate-limited");
    private static final URI PAYLOAD_TOO_LARGE = URI.create("https://api.bds.vn/problems/payload-too-large");

    private RateLimitResponses() {}

    /**
     * Policy for the request, classified on the decoded path like Spring MVC routing ({@code /api/v1/auth/%6Cogin}
     * reaches the login handler, so it spends the login quota); {@code null} when not rate limited.
     */
    static RateLimitPolicy policyFor(RateLimitPolicies policies, HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return null;
        return policies.resolve(request.getMethod(), UrlPathHelper.defaultInstance.getPathWithinApplication(request));
    }

    static void tooManyRequests(HttpServletRequest request, HttpServletResponse response, RateLimitDecision decision,
                                ObjectMapper mapper) throws IOException {
        long seconds = decision.retryAfterSeconds();
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        write(response, mapper, HttpStatus.TOO_MANY_REQUESTS, new ProblemDetails(RATE_LIMITED, "Quá nhiều yêu cầu",
                HttpStatus.TOO_MANY_REQUESTS.value(), "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau " + humanWait(seconds) + ".",
                request.getRequestURI(), "RATE_LIMITED", UUID.randomUUID().toString(), null));
    }

    static void payloadTooLarge(HttpServletRequest request, HttpServletResponse response, int maxBytes, ObjectMapper mapper)
            throws IOException {
        write(response, mapper, HttpStatus.PAYLOAD_TOO_LARGE, new ProblemDetails(PAYLOAD_TOO_LARGE, "Dữ liệu gửi lên quá lớn",
                HttpStatus.PAYLOAD_TOO_LARGE.value(), "Yêu cầu vượt quá " + (maxBytes / 1024) + " KB cho phép.",
                request.getRequestURI(), "PAYLOAD_TOO_LARGE", UUID.randomUUID().toString(), null));
    }

    private static void write(HttpServletResponse response, ObjectMapper mapper, HttpStatus status, ProblemDetails problem)
            throws IOException {
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(mapper.writeValueAsBytes(problem));
    }

    private static String humanWait(long seconds) {
        return seconds < 90 ? seconds + " giây" : "khoảng " + ((seconds + 59) / 60) + " phút";
    }
}

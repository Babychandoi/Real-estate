package com.company.bds.shared.security;

import com.company.bds.shared.error.ProblemDetails;
import com.company.bds.shared.error.ProblemResponses;
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

/** Problem Details responses and policy lookup shared by the two rate-limit filters. */
final class RateLimitResponses {

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
        ProblemResponses.write(response, mapper, ProblemDetails.of(HttpStatus.TOO_MANY_REQUESTS.value(), "RATE_LIMITED",
                "Quá nhiều yêu cầu", "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau " + humanWait(seconds) + ".",
                request.getRequestURI()));
    }

    static void payloadTooLarge(HttpServletRequest request, HttpServletResponse response, int maxBytes, ObjectMapper mapper)
            throws IOException {
        ProblemResponses.write(response, mapper, ProblemDetails.of(HttpStatus.PAYLOAD_TOO_LARGE.value(), "PAYLOAD_TOO_LARGE",
                "Dữ liệu gửi lên quá lớn", "Yêu cầu vượt quá " + (maxBytes / 1024) + " KB cho phép.", request.getRequestURI()));
    }

    private static String humanWait(long seconds) {
        return seconds < 90 ? seconds + " giây" : "khoảng " + ((seconds + 59) / 60) + " phút";
    }
}

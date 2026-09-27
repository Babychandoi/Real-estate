package com.company.bds.shared.security;

import com.company.bds.shared.error.ProblemDetails;
import com.company.bds.shared.security.ratelimit.RateLimitDecision;
import com.company.bds.shared.security.ratelimit.RateLimitDimension;
import com.company.bds.shared.security.ratelimit.RateLimitPolicies;
import com.company.bds.shared.security.ratelimit.RateLimitPolicy;
import com.company.bds.shared.security.ratelimit.RateLimitProperties;
import com.company.bds.shared.security.ratelimit.RateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Rate limiting for {@code /api/**} (audit F13): explicit route policies ({@link RateLimitPolicies}), quotas per
 * client IP (resolved by {@link ClientIpResolver}, so forged forwarding headers are ignored), per signed-in account
 * and, for credential endpoints, per targeted e-mail. Runs after the bearer-token filter so the account is known and
 * before the audit filter so rejected floods do not write audit rows. Rejections are {@code 429} Problem Details with
 * an exact {@code Retry-After}.
 */
@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_EMAIL_BODY_BYTES = 8 * 1024;
    private static final int MAX_EMAIL_LENGTH = 320;
    private static final URI PROBLEM_TYPE = URI.create("https://api.bds.vn/problems/rate-limited");

    private final boolean enabled;
    private final RateLimitPolicies policies;
    private final RateLimiter limiter;
    private final ClientIpResolver clientIp;
    private final ObjectMapper mapper;

    public RequestRateLimitFilter(RateLimitProperties properties, RateLimitPolicies policies, RateLimiter limiter,
                                  ClientIpResolver clientIp, ObjectMapper mapper) {
        this.enabled = properties.isEnabled();
        this.policies = policies;
        this.limiter = limiter;
        this.clientIp = clientIp;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || "OPTIONS".equalsIgnoreCase(request.getMethod()) || policyFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitPolicy policy = policyFor(request);
        if (policy == null) {
            chain.doFilter(request, response);
            return;
        }
        Map<RateLimitDimension, String> subjects = new EnumMap<>(RateLimitDimension.class);
        HttpServletRequest downstream = request;
        if (policy.uses(RateLimitDimension.IP)) subjects.put(RateLimitDimension.IP, clientIp.quotaSubject(request));
        if (policy.uses(RateLimitDimension.ACCOUNT)) {
            String account = currentAccount();
            if (account != null) subjects.put(RateLimitDimension.ACCOUNT, account);
        }
        if (policy.uses(RateLimitDimension.EMAIL)) {
            BufferedBodyRequest buffered = BufferedBodyRequest.wrap(request, MAX_EMAIL_BODY_BYTES);
            if (buffered.completeBody() == null) {
                // Credential bodies are tiny; a padded body must not dodge the per-account quota.
                rejectTooLarge(request, response);
                return;
            }
            downstream = buffered;
            String email = emailFrom(buffered.completeBody());
            if (email != null) subjects.put(RateLimitDimension.EMAIL, email);
        }
        RateLimitDecision decision = limiter.check(policy, subjects);
        if (!decision.allowed()) {
            reject(request, response, decision);
            return;
        }
        chain.doFilter(downstream, response);
    }

    /**
     * Classified on the decoded path, like Spring MVC routing: {@code /api/v1/auth/%6Cogin} reaches the login handler,
     * so it must spend the login quota too.
     */
    private RateLimitPolicy policyFor(HttpServletRequest request) {
        return policies.resolve(request.getMethod(), UrlPathHelper.defaultInstance.getPathWithinApplication(request));
    }

    private static String currentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return CurrentUser.id(authentication).toString();
        } catch (IllegalStateException ex) {
            return authentication.getName();
        }
    }

    /** Same normalisation as {@code AuthService.normalizeEmail}, so case or padding cannot split one account's quota. */
    private String emailFrom(byte[] body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode email = root == null ? null : root.get("email");
            if (email == null || !email.isTextual()) return null;
            String normalized = email.asText().trim().toLowerCase(Locale.ROOT);
            return normalized.isEmpty() || normalized.length() > MAX_EMAIL_LENGTH ? null : normalized;
        } catch (IOException ex) {
            return null; // the controller rejects the malformed body; the IP quota still applies
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, RateLimitDecision decision) throws IOException {
        long seconds = decision.retryAfterSeconds();
        ProblemDetails problem = new ProblemDetails(PROBLEM_TYPE, "Quá nhiều yêu cầu", HttpStatus.TOO_MANY_REQUESTS.value(),
                "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau " + humanWait(seconds) + ".",
                request.getRequestURI(), "RATE_LIMITED", UUID.randomUUID().toString(), null);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(mapper.writeValueAsBytes(problem));
    }

    private void rejectTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemDetails problem = new ProblemDetails(URI.create("https://api.bds.vn/problems/payload-too-large"), "Dữ liệu gửi lên quá lớn",
                HttpStatus.PAYLOAD_TOO_LARGE.value(), "Yêu cầu vượt quá " + (MAX_EMAIL_BODY_BYTES / 1024) + " KB cho phép.",
                request.getRequestURI(), "PAYLOAD_TOO_LARGE", UUID.randomUUID().toString(), null);
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(mapper.writeValueAsBytes(problem));
    }

    private static String humanWait(long seconds) {
        return seconds < 90 ? seconds + " giây" : "khoảng " + ((seconds + 59) / 60) + " phút";
    }

    /**
     * Reads at most {@code max} bytes of the body so the e-mail can be counted, then replays them (followed by any
     * unread remainder) to the controller. Bodies declared or found larger than {@code max} are not parsed.
     */
    static final class BufferedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] prefix;
        private final boolean complete;
        private ServletInputStream stream;
        private BufferedReader reader;

        private BufferedBodyRequest(HttpServletRequest request, byte[] prefix, boolean complete) {
            super(request);
            this.prefix = prefix;
            this.complete = complete;
        }

        static BufferedBodyRequest wrap(HttpServletRequest request, int max) throws IOException {
            if (request.getContentLengthLong() > max) return new BufferedBodyRequest(request, new byte[0], false);
            byte[] read = request.getInputStream().readNBytes(max + 1);
            return new BufferedBodyRequest(request, read, read.length <= max);
        }

        /** The whole body when it fitted into the buffer, otherwise {@code null}. */
        byte[] completeBody() { return complete ? prefix : null; }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (reader != null) throw new IllegalStateException("getReader() đã được gọi cho request này");
            if (stream == null) stream = new ReplayingInputStream(prefix, complete ? null : super.getInputStream());
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                if (stream != null) throw new IllegalStateException("getInputStream() đã được gọi cho request này");
                String encoding = getCharacterEncoding();
                Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
                reader = new BufferedReader(new InputStreamReader(new ReplayingInputStream(prefix, complete ? null : super.getInputStream()), charset));
            }
            return reader;
        }
    }

    private static final class ReplayingInputStream extends ServletInputStream {
        private final byte[] prefix;
        private final ServletInputStream rest;
        private int position;

        private ReplayingInputStream(byte[] prefix, ServletInputStream rest) {
            this.prefix = prefix;
            this.rest = rest;
        }

        @Override
        public int read() throws IOException {
            if (position < prefix.length) return prefix[position++] & 0xFF;
            return rest == null ? -1 : rest.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (position < prefix.length) {
                int count = Math.min(length, prefix.length - position);
                System.arraycopy(prefix, position, buffer, offset, count);
                position += count;
                return count;
            }
            return rest == null ? -1 : rest.read(buffer, offset, length);
        }

        @Override
        public int available() throws IOException {
            return (prefix.length - position) + (rest == null ? 0 : rest.available());
        }

        @Override
        public boolean isFinished() {
            return position >= prefix.length && (rest == null || rest.isFinished());
        }

        @Override
        public boolean isReady() {
            return position < prefix.length || rest == null || rest.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            if (rest != null && position >= prefix.length) {
                rest.setReadListener(listener);
                return;
            }
            throw new UnsupportedOperationException("Đọc bất đồng bộ không được hỗ trợ cho endpoint xác thực");
        }
    }
}

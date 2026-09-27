package com.company.bds.shared.security;

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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Rate limiting for {@code /api/**}, phase 1 of 2 (audit F13): explicit route policies ({@link RateLimitPolicies}),
 * quotas per client IP (resolved by {@link ClientIpResolver}, so forged forwarding headers are ignored) and, for
 * credential endpoints, per targeted e-mail read from the body. It runs before the bearer-token lookup, so a flood of
 * requests with random tokens is cut off here instead of costing a database query each. Per-account quotas need the
 * authenticated user and are phase 2 ({@link AccountRateLimitFilter}). Rejections are {@code 429} Problem Details with
 * an exact {@code Retry-After}.
 */
@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {
    static final int MAX_EMAIL_BODY_BYTES = 8 * 1024;
    private static final int MAX_EMAIL_LENGTH = 320;

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
        return !enabled || RateLimitResponses.policyFor(policies, request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitPolicy policy = RateLimitResponses.policyFor(policies, request);
        if (policy == null) {
            chain.doFilter(request, response);
            return;
        }
        Map<RateLimitDimension, String> subjects = new EnumMap<>(RateLimitDimension.class);
        HttpServletRequest downstream = request;
        if (policy.uses(RateLimitDimension.IP)) subjects.put(RateLimitDimension.IP, clientIp.quotaSubject(request));
        if (policy.uses(RateLimitDimension.EMAIL)) {
            BufferedBodyRequest buffered = BufferedBodyRequest.wrap(request, MAX_EMAIL_BODY_BYTES);
            if (buffered.completeBody() == null) {
                // Credential bodies are tiny; a padded body must not dodge the per-account quota.
                RateLimitResponses.payloadTooLarge(request, response, MAX_EMAIL_BODY_BYTES, mapper);
                return;
            }
            downstream = buffered;
            String email = emailFrom(buffered.completeBody());
            if (email != null) subjects.put(RateLimitDimension.EMAIL, email);
        }
        if (!subjects.isEmpty()) {
            RateLimitDecision decision = limiter.check(policy, subjects);
            if (!decision.allowed()) {
                RateLimitResponses.tooManyRequests(request, response, decision, mapper);
                return;
            }
        }
        chain.doFilter(downstream, response);
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

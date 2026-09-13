package com.company.bds.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;

    public RequestRateLimitFilter(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis.getIfAvailable();
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/auth/") || path.equals("/api/v1/public/leads")
                || path.equals("/api/v1/public/reports") || path.equals("/api/v1/search/geocode"));
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String forwarded = request.getHeader("X-Real-IP");
        String client = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.trim();
        String key = client + ':' + request.getRequestURI();
        long minute = Instant.now().getEpochSecond() / 60;
        int limit = request.getRequestURI().startsWith("/api/v1/auth/") ? 10 : 30;
        long count = increment(key, minute);
        if (count > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"title\":\"Quá nhiều yêu cầu\",\"status\":429,\"detail\":\"Vui lòng thử lại sau ít phút.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private long increment(String identity, long minute) {
        if (redis != null) {
            try {
                String key = "bds:rate:" + minute + ':' + Integer.toHexString(identity.hashCode());
                Long count = redis.opsForValue().increment(key);
                if (count != null && count == 1) redis.expire(key, java.time.Duration.ofMinutes(2));
                if (count != null) return count;
            } catch (RuntimeException ignored) {
                // Fail over to the bounded in-process limiter while Redis is unavailable.
            }
        }
        if (windows.size() > 10_000) windows.entrySet().removeIf(entry -> entry.getValue().minute < minute - 1);
        Window window = windows.compute(identity, (k, old) -> old == null || old.minute != minute ? new Window(minute) : old);
        return window.count.incrementAndGet();
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();
        private Window(long minute) { this.minute = minute; }
    }
}

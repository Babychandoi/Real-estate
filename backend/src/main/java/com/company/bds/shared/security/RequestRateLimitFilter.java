package com.company.bds.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/auth/") || path.equals("/api/v1/public/leads") || path.equals("/api/v1/reports"));
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String forwarded = request.getHeader("X-Forwarded-For");
        String client = forwarded == null ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        String key = client + ':' + request.getRequestURI();
        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(key, (k, old) -> old == null || old.minute != minute ? new Window(minute) : old);
        int limit = request.getRequestURI().startsWith("/api/v1/auth/") ? 10 : 30;
        if (window.count.incrementAndGet() > limit) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"title\":\"Quá nhiều yêu cầu\",\"status\":429,\"detail\":\"Vui lòng thử lại sau ít phút.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();
        private Window(long minute) { this.minute = minute; }
    }
}

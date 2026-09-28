package com.company.bds.engagement.api;

import com.company.bds.search.domain.InvalidFilterException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A saved search with an invalid filter answers like the search API: 400 {@code INVALID_FILTER} with every error. */
@RestControllerAdvice(basePackages = "com.company.bds.engagement.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EngagementProblemAdvice {
    private static final MediaType PROBLEM = MediaType.valueOf("application/problem+json");

    @ExceptionHandler(InvalidFilterException.class)
    public ResponseEntity<Map<String, Object>> invalid(InvalidFilterException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", URI.create("https://api.bds.vn/problems/" + ex.code().toLowerCase()));
        body.put("title", "Bộ lọc tìm kiếm không hợp lệ");
        body.put("status", 400);
        body.put("detail", "Có " + ex.errors().size() + " tham số cần sửa.");
        body.put("instance", request.getRequestURI());
        body.put("code", ex.code());
        body.put("traceId", UUID.randomUUID().toString());
        body.put("errors", ex.errors().stream().map(e -> Map.of("param", e.param(), "message", e.message())).toList());
        return ResponseEntity.status(400).contentType(PROBLEM).body(body);
    }
}

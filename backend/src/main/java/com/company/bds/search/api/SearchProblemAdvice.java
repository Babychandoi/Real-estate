package com.company.bds.search.api;

import com.company.bds.search.application.SearchProblemException;
import com.company.bds.search.domain.InvalidFilterException;
import com.company.bds.shared.error.ProblemDetails;
import com.company.bds.shared.observability.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Problem Details for the search/listing read API: {@code code} is stable ({@code INVALID_FILTER}, {@code BBOX_TOO_LARGE},
 * {@code CURSOR_INVALID}, {@code CURSOR_ENGINE_CHANGED}, {@code LISTING_NOT_FOUND}, {@code LISTING_GONE}, …) and
 * validation errors are listed as {@code errors=[{param, message}]} (contract §7). The other members, the media type
 * and the {@code traceId} are those of {@link ProblemDetails}.
 */
@RestControllerAdvice(basePackages = "com.company.bds.search.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SearchProblemAdvice {

    @ExceptionHandler(InvalidFilterException.class)
    public ResponseEntity<Map<String, Object>> invalid(InvalidFilterException ex, HttpServletRequest request) {
        Map<String, Object> body = base(400, ex.code(), "Tham số tìm kiếm không hợp lệ",
                "Có " + ex.errors().size() + " tham số cần sửa.", request);
        body.put("errors", ex.errors().stream().map(e -> Map.of("param", e.param(), "message", e.message())).toList());
        return ResponseEntity.status(400).contentType(ProblemDetails.MEDIA_TYPE).body(body);
    }

    @ExceptionHandler(SearchProblemException.class)
    public ResponseEntity<Map<String, Object>> problem(SearchProblemException ex, HttpServletRequest request) {
        Map<String, Object> body = base(ex.status(), ex.code(), ex.title(), ex.getMessage(), request);
        body.putAll(ex.extra());
        return ResponseEntity.status(ex.status()).contentType(ProblemDetails.MEDIA_TYPE).header("Cache-Control", "no-store").body(body);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> badParameter(Exception ex, HttpServletRequest request) {
        String param = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName()
                : ((MissingServletRequestParameterException) ex).getParameterName();
        Map<String, Object> body = base(400, "INVALID_FILTER", "Tham số không hợp lệ", "Tham số " + param + " không hợp lệ.", request);
        body.put("errors", List.of(Map.of("param", param, "message", "Giá trị không hợp lệ hoặc bị thiếu.")));
        return ResponseEntity.status(400).contentType(ProblemDetails.MEDIA_TYPE).body(body);
    }

    private static Map<String, Object> base(int status, String code, String title, String detail, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", ProblemDetails.typeOf(code).toString());
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("code", code);
        body.put("traceId", RequestCorrelation.traceId());
        return body;
    }
}

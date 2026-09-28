package com.company.bds.shared.error;

import com.company.bds.shared.observability.RequestCorrelation;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.MediaType;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Cấu trúc phản hồi lỗi API chuẩn hóa theo RFC 9457 mở rộng.
 * Tham khảo: PROJECT_CODE_RULES_BDS.md mục 8.4
 *
 * <p>Every error body of the API has this shape (audit F22.3): {@code type} is {@link #BASE_TYPE} + the kebab-case
 * {@code code}, {@code title} is the generic title of the problem (never data of the resource), {@code detail} the
 * user-facing Vietnamese explanation, {@code instance} the request path, {@code code} the stable machine code and
 * {@code traceId} the correlation id of the request (also in the {@code X-Request-Id} response header and in every
 * log line of the request). {@code errors} lists field errors of a validation failure. The media type is
 * {@code application/problem+json}. Build instances with {@link #of}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetails(
        URI type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        String traceId,
        List<ValidationErrorItem> errors
) {
    public static final String BASE_TYPE = "https://api.bds.vn/problems/";
    public static final MediaType MEDIA_TYPE = MediaType.APPLICATION_PROBLEM_JSON;

    public record ValidationErrorItem(
            String field,
            String code,
            String message
    ) {}

    public static ProblemDetails of(int status, String code, String title, String detail, String instance,
                                    List<ValidationErrorItem> errors) {
        return new ProblemDetails(typeOf(code), title, status, detail, instance, code, RequestCorrelation.traceId(),
                errors == null || errors.isEmpty() ? null : List.copyOf(errors));
    }

    public static ProblemDetails of(int status, String code, String title, String detail, String instance) {
        return of(status, code, title, detail, instance, null);
    }

    /** {@code VALIDATION_ERROR} → {@code https://api.bds.vn/problems/validation-error}. */
    public static URI typeOf(String code) {
        return URI.create(BASE_TYPE + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}

package com.company.bds.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.net.URI;
import java.util.List;

/**
 * Cấu trúc phản hồi lỗi API chuẩn hóa theo RFC 9457 mở rộng.
 * Tham khảo: PROJECT_CODE_RULES_BDS.md mục 8.4
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
    public record ValidationErrorItem(
            String field,
            String code,
            String message
    ) {}
}

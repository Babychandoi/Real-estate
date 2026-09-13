package com.company.bds.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bộ xử lý ngoại lệ trung tâm cho toàn bộ REST API của ứng dụng.
 * Chuyển đổi mọi lỗi sang định dạng Problem Details RFC 9457 mà không làm lộ stacktrace hay PII.
 * Tham khảo: PROJECT_CODE_RULES_BDS.md mục 8.4
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE_PROBLEM_TYPE = "https://api.bds.vn/problems/";

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        List<ProblemDetails.ValidationErrorItem> errors = new ArrayList<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String localizedMsg = fieldError.getDefaultMessage();
            errors.add(new ProblemDetails.ValidationErrorItem(
                    fieldError.getField(),
                    fieldError.getCode(),
                    localizedMsg
            ));
        }

        ProblemDetails problem = new ProblemDetails(
                URI.create(BASE_PROBLEM_TYPE + "validation-error"),
                "Dữ liệu không hợp lệ",
                HttpStatus.BAD_REQUEST.value(),
                "Có " + errors.size() + " trường dữ liệu cần kiểm tra lại.",
                request.getRequestURI(),
                "VALIDATION_ERROR",
                traceId,
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetails> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        ProblemDetails problem = new ProblemDetails(
                URI.create(BASE_PROBLEM_TYPE + "unauthorized"),
                "Chưa xác thực danh tính",
                HttpStatus.UNAUTHORIZED.value(),
                messageSource.getMessage("error.unauthorized", null, "Yêu cầu đăng nhập", LocaleContextHolder.getLocale()),
                request.getRequestURI(),
                "UNAUTHORIZED",
                traceId,
                null
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetails> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        ProblemDetails problem = new ProblemDetails(
                URI.create(BASE_PROBLEM_TYPE + "forbidden"),
                "Từ chối quyền truy cập",
                HttpStatus.FORBIDDEN.value(),
                messageSource.getMessage("error.forbidden", null, "Không có quyền thao tác", LocaleContextHolder.getLocale()),
                request.getRequestURI(),
                "FORBIDDEN",
                traceId,
                null
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ProblemDetails> handleNotFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        ProblemDetails problem = new ProblemDetails(
                URI.create(BASE_PROBLEM_TYPE + "not-found"),
                "Tài nguyên không tìm thấy",
                HttpStatus.NOT_FOUND.value(),
                messageSource.getMessage("error.not_found", null, "Đường dẫn không tồn tại", LocaleContextHolder.getLocale()),
                request.getRequestURI(),
                "NOT_FOUND",
                traceId,
                null
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetails> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return simple(ex, request, HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Yêu cầu không hợp lệ");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetails> handleConflict(IllegalStateException ex, HttpServletRequest request) {
        return simple(ex, request, HttpStatus.CONFLICT, "CONFLICT", "Không thể thực hiện thao tác");
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ProblemDetails> handleUnavailable(UnsupportedOperationException ex, HttpServletRequest request) {
        return simple(ex, request, HttpStatus.SERVICE_UNAVAILABLE, "FEATURE_UNAVAILABLE", "Tính năng chưa được cấu hình");
    }

    private ResponseEntity<ProblemDetails> simple(Exception ex, HttpServletRequest request, HttpStatus status,
                                                   String code, String title) {
        ProblemDetails problem = new ProblemDetails(URI.create(BASE_PROBLEM_TYPE + code.toLowerCase()), title,
                status.value(), ex.getMessage(), request.getRequestURI(), code, UUID.randomUUID().toString(), null);
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleGenericException(
            Exception ex, HttpServletRequest request) {

        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled server exception [traceId={}]: {}", traceId, ex.getMessage(), ex);

        ProblemDetails problem = new ProblemDetails(
                URI.create(BASE_PROBLEM_TYPE + "internal-error"),
                "Lỗi hệ thống ngoài dự kiến",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                messageSource.getMessage("error.generic", null, "Đã xảy ra lỗi nội bộ", LocaleContextHolder.getLocale()),
                request.getRequestURI(),
                "INTERNAL_SERVER_ERROR",
                traceId,
                null
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}

package com.company.bds.shared.error;

import com.company.bds.listing.domain.exception.ListingDomainException;
import com.company.bds.listing.domain.exception.ListingValidationException;
import com.company.bds.listing.domain.exception.ListingVersionConflictException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Bộ xử lý ngoại lệ trung tâm cho toàn bộ REST API của ứng dụng.
 * Chuyển đổi mọi lỗi sang định dạng Problem Details RFC 9457 mà không làm lộ stacktrace hay PII.
 * Tham khảo: PROJECT_CODE_RULES_BDS.md mục 8.4
 *
 * <p>Every body is built by {@link ProblemDetails#of} (shape, {@code type} from {@code code}, {@code traceId} = request
 * id) and sent as {@code application/problem+json}. Framework errors keep their real status (400/404/405/406/413/415…)
 * instead of falling into the 500 handler (audit F22.3).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_TITLE = "Dữ liệu không hợp lệ";

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    // ---- validation ------------------------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        return validation(fieldErrors(ex), request);
    }

    /** {@code @ModelAttribute} binding (query objects) failed. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemDetails> handleBind(BindException ex, HttpServletRequest request) {
        return validation(fieldErrors(ex), request);
    }

    /** {@code @Validated} parameters of a controller method ({@code @RequestParam @Min(1) int page}). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetails> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<ProblemDetails.ValidationErrorItem> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> errors.add(new ProblemDetails.ValidationErrorItem(
                    name, error.getCodes() != null && error.getCodes().length > 0 ? lastSegment(error.getCodes()[error.getCodes().length - 1]) : "INVALID",
                    error.getDefaultMessage())));
        });
        return validation(errors, request);
    }

    /** Bean Validation on a service or on a {@code @Validated} controller (method-level constraints). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetails> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ProblemDetails.ValidationErrorItem> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ProblemDetails.ValidationErrorItem(leafOf(violation.getPropertyPath().toString()),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        violation.getMessage()))
                .toList();
        return validation(errors, request);
    }

    @ExceptionHandler(ListingValidationException.class)
    public ResponseEntity<ProblemDetails> handleListingValidation(ListingValidationException ex, HttpServletRequest request) {
        List<ProblemDetails.ValidationErrorItem> errors = ex.issues().stream()
                .map(issue -> new ProblemDetails.ValidationErrorItem(issue.field(), "INVALID", issue.message())).toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", VALIDATION_TITLE, ex.getMessage(), request, errors);
    }

    /** Malformed JSON or an unknown enum value: 400 naming the field instead of a 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetails> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        List<ProblemDetails.ValidationErrorItem> errors = null;
        if (ex.getCause() instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException mismatch
                && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : String.valueOf(ref.getIndex()))
                    .reduce((a, b) -> a + "." + b).orElse(null);
            errors = List.of(new ProblemDetails.ValidationErrorItem(field, "INVALID_FORMAT", "Giá trị không hợp lệ."));
        }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", VALIDATION_TITLE, "Nội dung yêu cầu không đọc được.",
                request, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetails> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", VALIDATION_TITLE,
                "Thiếu tham số " + ex.getParameterName() + ".", request,
                List.of(new ProblemDetails.ValidationErrorItem(ex.getParameterName(), "REQUIRED", "Tham số bắt buộc.")));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetails> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", VALIDATION_TITLE, "Thiếu header " + ex.getHeaderName() + ".",
                request, List.of(new ProblemDetails.ValidationErrorItem(ex.getHeaderName(), "REQUIRED", "Header bắt buộc.")));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ProblemDetails> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", VALIDATION_TITLE, "Thiếu phần " + ex.getRequestPartName() + ".",
                request, List.of(new ProblemDetails.ValidationErrorItem(ex.getRequestPartName(), "REQUIRED", "Phần bắt buộc.")));
    }

    /** {@code ?page=abc} or a malformed UUID in the path. The rejected value is not echoed (it may be PII). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetails> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", VALIDATION_TITLE, "Tham số " + ex.getName() + " không hợp lệ.",
                request, List.of(new ProblemDetails.ValidationErrorItem(ex.getName(), "INVALID_FORMAT", "Giá trị không hợp lệ.")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetails> handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Dữ liệu gửi lên quá lớn",
                "Tệp hoặc dữ liệu gửi lên vượt quá dung lượng cho phép.", request, null);
    }

    // ---- security --------------------------------------------------------------------------------------------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetails> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực danh tính",
                message("error.unauthorized", "Yêu cầu đăng nhập"), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetails> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", "Từ chối quyền truy cập",
                message("error.forbidden", "Không có quyền thao tác"), request, null);
    }

    // ---- business refusals -----------------------------------------------------------------------------------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetails> handleApiException(ApiException ex, HttpServletRequest request) {
        return respond(ex.status(), ex.code(), ex.status().is4xxClientError() ? "Không thể thực hiện thao tác" : "Lỗi hệ thống",
                ex.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetails> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Yêu cầu không hợp lệ", ex.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetails> handleConflict(IllegalStateException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "CONFLICT", "Không thể thực hiện thao tác", ex.getMessage(), request, null);
    }

    @ExceptionHandler(ListingDomainException.class)
    public ResponseEntity<ProblemDetails> handleListingDomainException(ListingDomainException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case "LISTING_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.CONFLICT;
        };
        return respond(status, ex.getErrorCode(), "Không thể thực hiện thao tác", ex.getMessage(), request, null);
    }

    /** Lost-update protection (R-4): the client must reload the draft; the current version travels in the ETag. */
    @ExceptionHandler(ListingVersionConflictException.class)
    public ResponseEntity<ProblemDetails> handleVersionConflict(ListingVersionConflictException ex, HttpServletRequest request) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CONFLICT).contentType(ProblemDetails.MEDIA_TYPE);
        if (ex.currentVersion() != null) builder.eTag("\"v" + ex.currentVersion() + "\"");
        return builder.body(ProblemDetails.of(409, "VERSION_CONFLICT", "Dữ liệu đã thay đổi", ex.getMessage(),
                request.getRequestURI()));
    }

    /** JPA {@code @Version} on a listing draft: same answer as {@link ListingVersionConflictException}. */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetails> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Dữ liệu đã thay đổi",
                "Dữ liệu vừa được cập nhật ở nơi khác. Tải lại rồi thử lại.", request, null);
    }

    /** Two writers raced on the same row (a compare-and-set): the loser reloads and retries. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetails> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "Dữ liệu vừa được người khác thay đổi",
                "Dữ liệu vừa được thay đổi bởi thao tác khác. Vui lòng tải lại rồi thử lại.", request, null);
    }

    /**
     * PostgreSQL gave up a lock wait (lock_timeout, a deadlock victim, a serialization failure): nothing was written and
     * the same request can simply be sent again — 503 with Retry-After, never a 500.
     */
    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetails> handleLockFailure(org.springframework.dao.PessimisticLockingFailureException ex,
                                                            HttpServletRequest request) {
        log.warn("lock_failure type={} path={}", ex.getClass().getSimpleName(), request.getRequestURI());
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "1");
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "TRY_AGAIN", "Hệ thống đang bận",
                "Thao tác trùng thời điểm với một thao tác khác và chưa được lưu. Vui lòng thử lại.", request, null, headers);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ProblemDetails> handleUnavailable(UnsupportedOperationException ex, HttpServletRequest request) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "FEATURE_UNAVAILABLE", "Tính năng chưa được cấu hình", ex.getMessage(),
                request, null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetails> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        String code = status == HttpStatus.NOT_IMPLEMENTED.value() ? "FEATURE_NOT_IMPLEMENTED" : codeFor(status);
        String title = status == HttpStatus.NOT_IMPLEMENTED.value() ? "Tính năng chưa được triển khai" : titleFor(status);
        return respond(ex.getStatusCode(), code, title, ex.getReason() != null ? ex.getReason() : title, request, null,
                ex.getHeaders());
    }

    // ---- framework errors and the rest -----------------------------------------------------------------------

    /**
     * The client of a streaming response (SSE) went away: nothing can be written any more and it is not a server error.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientGone(AsyncRequestNotUsableException ex) {
        log.debug("client_disconnected reason={}", ex.getClass().getSimpleName());
    }

    /** The client accepts no representation we can produce, not even Problem Details: status only. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleGenericException(Exception ex, HttpServletRequest request) {
        // Spring MVC's own 4xx/5xx (no handler/resource 404, 405 with Allow, 415, 413 …) carry their status
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            if (status.is5xxServerError()) log.error("framework_error status={} type={}", status.value(), ex.getClass().getName(), ex);
            return respond(status, codeFor(status.value()), titleFor(status.value()), detailFor(status.value()), request, null,
                    errorResponse.getHeaders());
        }
        log.error("unhandled_exception type={} message={}", ex.getClass().getName(), ex.getMessage(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Lỗi hệ thống ngoài dự kiến",
                message("error.generic", "Đã xảy ra lỗi nội bộ"), request, null);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private ResponseEntity<ProblemDetails> validation(List<ProblemDetails.ValidationErrorItem> errors, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", VALIDATION_TITLE,
                "Có " + errors.size() + " trường dữ liệu cần kiểm tra lại.", request, errors);
    }

    private static ResponseEntity<ProblemDetails> respond(HttpStatusCode status, String code, String title, String detail,
                                                          HttpServletRequest request, List<ProblemDetails.ValidationErrorItem> errors) {
        return respond(status, code, title, detail, request, errors, HttpHeaders.EMPTY);
    }

    private static ResponseEntity<ProblemDetails> respond(HttpStatusCode status, String code, String title, String detail,
                                                          HttpServletRequest request, List<ProblemDetails.ValidationErrorItem> errors,
                                                          HttpHeaders headers) {
        return ResponseEntity.status(status).headers(headers).contentType(ProblemDetails.MEDIA_TYPE)
                .body(ProblemDetails.of(status.value(), code, title, detail, request.getRequestURI(), errors));
    }

    private static List<ProblemDetails.ValidationErrorItem> fieldErrors(BindException ex) {
        List<ProblemDetails.ValidationErrorItem> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(new ProblemDetails.ValidationErrorItem(fieldError.getField(), fieldError.getCode(), fieldError.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errors.add(new ProblemDetails.ValidationErrorItem(error.getObjectName(), error.getCode(), error.getDefaultMessage())));
        return errors;
    }

    static String codeFor(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 409 -> "CONFLICT";
            case 410 -> "GONE";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 422 -> "UNPROCESSABLE_CONTENT";
            case 429 -> "RATE_LIMITED";
            case 503 -> "SERVICE_UNAVAILABLE";
            default -> status >= 500 ? "INTERNAL_SERVER_ERROR" : "HTTP_" + status;
        };
    }

    static String titleFor(int status) {
        return switch (status) {
            case 401 -> "Chưa xác thực danh tính";
            case 403 -> "Từ chối quyền truy cập";
            case 404 -> "Tài nguyên không tìm thấy";
            case 405 -> "Phương thức không được hỗ trợ";
            case 406 -> "Định dạng phản hồi không được hỗ trợ";
            case 410 -> "Tài nguyên không còn";
            case 413 -> "Dữ liệu gửi lên quá lớn";
            case 415 -> "Định dạng dữ liệu không được hỗ trợ";
            case 429 -> "Quá nhiều yêu cầu";
            default -> status >= 500 ? "Lỗi hệ thống" : "Yêu cầu không thể xử lý";
        };
    }

    private String detailFor(int status) {
        return switch (status) {
            case 404 -> message("error.not_found", "Đường dẫn không tồn tại");
            case 405 -> "Đường dẫn này không hỗ trợ phương thức đã gửi.";
            case 415 -> "Định dạng nội dung gửi lên không được hỗ trợ.";
            default -> status >= 500 ? message("error.generic", "Đã xảy ra lỗi nội bộ") : titleFor(status) + ".";
        };
    }

    private String message(String key, String fallback) {
        return messageSource.getMessage(key, null, fallback, LocaleContextHolder.getLocale());
    }

    private static String leafOf(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }

    private static String lastSegment(String code) {
        int dot = code.lastIndexOf('.');
        return dot < 0 ? code : code.substring(dot + 1);
    }
}

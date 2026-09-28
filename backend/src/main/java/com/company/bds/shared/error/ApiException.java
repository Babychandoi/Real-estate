package com.company.bds.shared.error;

import org.springframework.http.HttpStatus;

/**
 * A business refusal with an HTTP status and a stable machine code (Problem Details {@code code}), e.g.
 * {@code 409 CLAIM_CONFLICT}. The message is user-facing Vietnamese text and must never contain PII.
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * For subclasses outside the web layer (application services must not import {@code org.springframework.http}, see
     * {@code ArchitectureTests}): the status is a plain RFC 9110 code.
     */
    protected ApiException(int status, String code, String message) {
        this(HttpStatus.valueOf(status), code, message);
    }

    public static ApiException unauthorized(String code, String message) { return new ApiException(HttpStatus.UNAUTHORIZED, code, message); }

    public static ApiException gone(String code, String message) { return new ApiException(HttpStatus.GONE, code, message); }

    public static ApiException preconditionRequired(String code, String message) { return new ApiException(HttpStatus.PRECONDITION_REQUIRED, code, message); }

    public static ApiException tooManyRequests(String code, String message) { return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message); }

    public static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }

    public static ApiException badRequest(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }

    public static ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }

    public static ApiException forbidden(String code, String message) { return new ApiException(HttpStatus.FORBIDDEN, code, message); }

    public HttpStatus status() { return status; }

    /** The numeric status, for callers outside the web layer. */
    public int statusCode() { return status.value(); }

    public String code() { return code; }
}

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

    public static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }

    public static ApiException badRequest(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }

    public static ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }

    public static ApiException forbidden(String code, String message) { return new ApiException(HttpStatus.FORBIDDEN, code, message); }

    public HttpStatus status() { return status; }

    public String code() { return code; }
}

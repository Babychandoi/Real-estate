package com.company.bds.shared.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's whitelabel/JSON {@code /error} body with Problem Details, for errors that never reach a
 * controller advice (a filter threw, the container rejected the request, {@code sendError} was called). Same shape,
 * media type and {@code traceId} as {@link GlobalExceptionHandler}; the exception message is never exposed.
 */
@RestController
public class ProblemErrorController implements ErrorController {
    private static final Logger log = LoggerFactory.getLogger(ProblemErrorController.class);

    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<ProblemDetails> error(HttpServletRequest request) {
        int status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code ? code : 500;
        if (status < 400) status = 500;
        String path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String uri ? uri : request.getRequestURI();
        if (status >= 500 && request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable failure) {
            log.error("error_dispatch status={} type={}", status, failure.getClass().getName(), failure);
        }
        String title = GlobalExceptionHandler.titleFor(status);
        String detail = status >= 500 ? "Đã xảy ra lỗi nội bộ" : title + ".";
        return ResponseEntity.status(status).contentType(ProblemDetails.MEDIA_TYPE)
                .body(ProblemDetails.of(status, GlobalExceptionHandler.codeFor(status), title, detail, path));
    }
}

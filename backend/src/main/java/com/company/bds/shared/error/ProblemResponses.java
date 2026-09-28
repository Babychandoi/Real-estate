package com.company.bds.shared.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes a {@link ProblemDetails} straight to the servlet response, for filters and Spring Security handlers that
 * answer before Spring MVC (rate limits, authentication entry point, access denied). Same shape and media type as the
 * {@link GlobalExceptionHandler} responses; never cached.
 */
public final class ProblemResponses {
    private static final ObjectMapper FALLBACK_MAPPER = new ObjectMapper();

    private ProblemResponses() {}

    public static void write(HttpServletResponse response, ObjectMapper mapper, ProblemDetails problem) throws IOException {
        if (response.isCommitted()) return;
        response.resetBuffer();
        response.setStatus(problem.status());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(ProblemDetails.MEDIA_TYPE.toString());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write((mapper != null ? mapper : FALLBACK_MAPPER).writeValueAsBytes(problem));
    }
}

package com.company.bds.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * First filter of every request: takes a safe {@code X-Request-Id} from the caller (Nginx sets one per request) or makes
 * one, reuses the W3C {@code traceparent} trace id when present, echoes the request id in the response and puts both
 * into the logging MDC until the request ends. Runs before Spring Security so rejections are correlated too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final String TRACE_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId;
        String traceId;
        if (request.getAttribute(REQUEST_ID_ATTRIBUTE) instanceof String known) {
            requestId = known;
            traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        } else {
            requestId = RequestCorrelation.acceptRequestId(request.getHeader(RequestCorrelation.REQUEST_ID_HEADER));
            traceId = RequestCorrelation.traceIdFrom(request.getHeader(RequestCorrelation.TRACEPARENT_HEADER), requestId);
            request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
            request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        }
        MDC.put(RequestCorrelation.MDC_REQUEST_ID, requestId);
        MDC.put(RequestCorrelation.MDC_TRACE_ID, traceId);
        response.setHeader(RequestCorrelation.REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestCorrelation.MDC_REQUEST_ID);
            MDC.remove(RequestCorrelation.MDC_TRACE_ID);
        }
    }

    /** Async (SSE) and error dispatches keep the ids of the original request instead of getting new ones. */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}

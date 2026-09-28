package com.company.bds.shared.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlation ids of the current request (audit F22.2). {@link RequestIdFilter} puts them into the logging MDC for the
 * whole request; every log line and every Problem Details body ({@code traceId}) of that request carries the same
 * values, so a support ticket quoting the id finds the matching log lines.
 */
public final class RequestCorrelation {
    /** Request/response header carrying the request id (echoed back, exposed to the browser by CORS). */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    /** W3C Trace Context header; its trace id is reused when a caller (proxy, other service) already started a trace. */
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_TRACE_ID = "traceId";

    /** Accepted inbound ids: short, printable, no separators that could forge log fields or headers. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{7,63}");
    private static final Pattern TRACEPARENT = Pattern.compile("[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}");

    private RequestCorrelation() {}

    /** The trace id of the current request, or a fresh id outside a request (jobs, schedulers). */
    public static String traceId() {
        String traceId = MDC.get(MDC_TRACE_ID);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }

    /** The request id of the current request, or {@code null} outside a request. */
    public static String requestId() {
        return MDC.get(MDC_REQUEST_ID);
    }

    static String acceptRequestId(String inbound) {
        return inbound != null && SAFE_ID.matcher(inbound).matches() ? inbound : UUID.randomUUID().toString();
    }

    /** The trace id of a valid {@code traceparent} header (not the all-zero invalid id), otherwise {@code fallback}. */
    static String traceIdFrom(String traceparent, String fallback) {
        if (traceparent == null) return fallback;
        var matcher = TRACEPARENT.matcher(traceparent.trim());
        if (!matcher.matches() || matcher.group(1).chars().allMatch(c -> c == '0')) return fallback;
        return matcher.group(1);
    }
}

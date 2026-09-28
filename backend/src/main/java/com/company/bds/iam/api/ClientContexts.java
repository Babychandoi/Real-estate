package com.company.bds.iam.api;

import com.company.bds.iam.domain.ClientContext;
import com.company.bds.shared.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** Coarse client description of a request (trusted client IP reduced to a prefix, browser/OS label). */
@Component
public class ClientContexts {
    private final ClientIpResolver ips;

    public ClientContexts(ClientIpResolver ips) { this.ips = ips; }

    public ClientContext of(HttpServletRequest request) {
        return ClientContext.of(ips.resolve(request), request.getHeader(HttpHeaders.USER_AGENT));
    }
}

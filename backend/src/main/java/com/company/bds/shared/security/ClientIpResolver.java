package com.company.bds.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Resolves the address of the client behind the proxy chain (audit F13.1).
 *
 * <p>Forwarding headers are honoured only when the direct peer (the socket address) is a declared proxy in
 * {@code app.security.trusted-proxies}; from any other peer they are client input and are ignored.</p>
 *
 * <p>From a trusted peer, {@code X-Forwarded-For} wins: its right-most hop that is not itself a trusted proxy is the
 * address the last trusted proxy saw, and a client can only prepend entries to it. {@code X-Real-IP} is used only when
 * there is no {@code X-Forwarded-For}, because some proxies (Caddy, for example) pass a client-supplied
 * {@code X-Real-IP} through untouched. Nginx here sets both headers to the address it resolved from Cloudflare's
 * {@code CF-Connecting-IP}.</p>
 */
@Component
public class ClientIpResolver {
    /** Loopback plus the address pools Docker assigns to bridge and Compose networks. */
    public static final String DEFAULT_TRUSTED_PROXIES = "127.0.0.0/8,::1/128,172.16.0.0/12,192.168.0.0/16";
    private static final int MAX_FORWARDED_HOPS = 20;

    private final List<IpAddresses.Range> trustedProxies;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:" + DEFAULT_TRUSTED_PROXIES + "}") String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(IpAddresses.Range::parse).toList();
    }

    /** Canonical client address; never {@code null}. */
    public String resolve(HttpServletRequest request) {
        String rawPeer = request.getRemoteAddr();
        String peer = IpAddresses.normalize(rawPeer);
        if (peer == null) return rawPeer == null || rawPeer.isBlank() ? "unknown" : rawPeer;
        if (!isTrustedProxy(peer)) return peer;

        List<String> hops = forwardedHops(request);
        if (!hops.isEmpty()) {
            String leftmostTrusted = null;
            for (int i = hops.size() - 1; i >= 0; i--) {
                String hop = IpAddresses.normalize(hops.get(i));
                if (hop == null) return peer; // the last proxy did not write a valid hop: nothing here is verifiable
                if (!isTrustedProxy(hop)) return hop;
                leftmostTrusted = hop;
            }
            return leftmostTrusted; // every hop is internal (e.g. a request from the host itself)
        }

        String realIp = IpAddresses.normalize(request.getHeader("X-Real-IP"));
        return realIp != null ? realIp : peer;
    }

    /** Subject for per-IP quotas (IPv6 clients are grouped by /64). */
    public String quotaSubject(HttpServletRequest request) {
        return IpAddresses.quotaSubject(resolve(request));
    }

    public boolean isTrustedProxy(String address) {
        byte[] parsed = IpAddresses.parse(address);
        if (parsed == null) return false;
        for (IpAddresses.Range range : trustedProxies) {
            if (range.contains(parsed)) return true;
        }
        return false;
    }

    List<IpAddresses.Range> trustedProxies() { return Collections.unmodifiableList(trustedProxies); }

    private static List<String> forwardedHops(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders("X-Forwarded-For");
        if (headers == null) return List.of();
        List<String> hops = new ArrayList<>();
        while (headers.hasMoreElements()) {
            for (String part : headers.nextElement().split(",")) {
                if (!part.isBlank()) hops.add(part.trim());
            }
        }
        // Keep the hops closest to us; a client can prepend as many entries as it likes.
        return hops.size() > MAX_FORWARDED_HOPS ? hops.subList(hops.size() - MAX_FORWARDED_HOPS, hops.size()) : hops;
    }
}

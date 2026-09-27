package com.company.bds.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F13.1: only declared proxies may assert the client address. */
class ClientIpResolverTests {
    private final ClientIpResolver resolver = new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED_PROXIES);

    private static MockHttpServletRequest fromPeer(String peer) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings/search");
        request.setRemoteAddr(peer);
        return request;
    }

    @Test
    void untrustedPeerIsIdentifiedBySocketAddressWhateverHeadersItSends() {
        MockHttpServletRequest request = fromPeer("198.51.100.7");
        request.addHeader("X-Real-IP", "203.0.113.9");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void nginxInTheComposeNetworkSuppliesTheClientAddress() {
        MockHttpServletRequest request = fromPeer("172.18.0.5");
        request.addHeader("X-Real-IP", "203.0.113.9");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(withRealIp(fromPeer("127.0.0.1"), "203.0.113.9"))).isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(withRealIp(fromPeer("192.168.65.3"), "203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    @Test
    void forwardedForIsReadRightToLeftSoPrependedEntriesAreIgnored() {
        MockHttpServletRequest request = fromPeer("172.18.0.5");
        request.addHeader("X-Forwarded-For", "198.51.100.200, 203.0.113.9, 172.18.0.3");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void malformedForwardingValuesFallBackToThePeer() {
        assertThat(resolver.resolve(withRealIp(fromPeer("172.18.0.5"), "evil.example.com"))).isEqualTo("172.18.0.5");
        assertThat(resolver.resolve(withRealIp(fromPeer("172.18.0.5"), "203.0.113.9, 198.51.100.1"))).isEqualTo("172.18.0.5");

        MockHttpServletRequest garbageChain = fromPeer("172.18.0.5");
        garbageChain.addHeader("X-Forwarded-For", "203.0.113.9, not-an-ip");
        assertThat(resolver.resolve(garbageChain)).isEqualTo("172.18.0.5");
    }

    @Test
    void onlyIpLiteralsAreAcceptedAndNothingIsResolvedThroughDns() {
        // "localhost" would resolve to 127.0.0.1 if the value ever reached a name lookup.
        for (String value : List.of("localhost", "example.com", "1.2.3.999", "1.2.3", "1.2.3.4.5", ".::1", "::g",
                "1.2.3.4:80", "", " ", "0x7f.0.0.1")) {
            assertThat(IpAddresses.parse(value)).as(value).isNull();
        }
        assertThat(IpAddresses.normalize(" 203.0.113.9 ")).isEqualTo("203.0.113.9");
        assertThat(IpAddresses.normalize("[2001:db8::1]")).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void ipv4MappedIpv6CollapsesToIpv4() {
        assertThat(IpAddresses.normalize("::ffff:203.0.113.9")).isEqualTo("203.0.113.9");
    }

    @Test
    void ipv6ClientsShareOneQuotaPerSlash64() {
        String a = IpAddresses.quotaSubject(IpAddresses.normalize("2001:db8:1:2:aaaa::1"));
        String b = IpAddresses.quotaSubject(IpAddresses.normalize("2001:db8:1:2:bbbb::2"));
        String otherNetwork = IpAddresses.quotaSubject(IpAddresses.normalize("2001:db8:1:3::1"));

        assertThat(a).isEqualTo(b).endsWith("/64");
        assertThat(otherNetwork).isNotEqualTo(a);
        assertThat(IpAddresses.quotaSubject("203.0.113.9")).isEqualTo("203.0.113.9");
    }

    @Test
    void configuredProxyListReplacesTheDefaultsAndBadEntriesFailAtStartup() {
        ClientIpResolver onlyTenNet = new ClientIpResolver("10.0.0.0/8, ::1/128");

        assertThat(onlyTenNet.resolve(withRealIp(fromPeer("172.18.0.5"), "203.0.113.9"))).isEqualTo("172.18.0.5");
        assertThat(onlyTenNet.resolve(withRealIp(fromPeer("10.1.2.3"), "203.0.113.9"))).isEqualTo("203.0.113.9");
        assertThatThrownBy(() -> new ClientIpResolver("172.16.0.0/33")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientIpResolver("proxy.internal")).isInstanceOf(IllegalArgumentException.class);
    }

    private static MockHttpServletRequest withRealIp(MockHttpServletRequest request, String realIp) {
        request.addHeader("X-Real-IP", realIp);
        return request;
    }
}

package com.company.bds.shared.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Strict IP-literal parsing for values that arrive in request headers.
 *
 * <p>{@link InetAddress#getByName(String)} falls back to a DNS lookup for anything that is not a well-formed
 * literal (for example {@code 1.2.3.999}), which would let a client make the server resolve names it chooses.
 * Everything here parses IPv4 by hand and only hands strings that can only be IPv6 literals to the JDK.</p>
 */
final class IpAddresses {
    private static final int MAX_LITERAL_LENGTH = 64;

    private IpAddresses() {}

    /** Returns the 4- or 16-byte address, or {@code null} when {@code text} is not a single IP literal. */
    static byte[] parse(String text) {
        if (text == null) return null;
        String value = text.trim();
        if (value.isEmpty() || value.length() > MAX_LITERAL_LENGTH) return null;
        if (value.startsWith("[") && value.endsWith("]")) value = value.substring(1, value.length() - 1);
        if (value.indexOf(':') < 0) return parseIpv4(value);
        return parseIpv6(value);
    }

    /** Canonical text form (IPv4-mapped IPv6 collapses to IPv4), or {@code null} for non-literals. */
    static String normalize(String text) {
        byte[] address = parse(text);
        return address == null ? null : format(address);
    }

    static String format(byte[] address) {
        try {
            return InetAddress.getByAddress(address).getHostAddress();
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Độ dài địa chỉ IP không hợp lệ", ex);
        }
    }

    /**
     * Subject used for per-IP quotas: the address itself for IPv4, the /64 network for IPv6 because a single IPv6
     * subscriber normally owns a whole /64 and can rotate addresses inside it freely.
     */
    static String quotaSubject(String canonicalAddress) {
        byte[] address = parse(canonicalAddress);
        if (address == null) return canonicalAddress;
        if (address.length == 4) return format(address);
        byte[] network = Arrays.copyOf(address, 16);
        Arrays.fill(network, 8, 16, (byte) 0);
        return format(network) + "/64";
    }

    private static byte[] parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] address = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) return null;
            int octet = 0;
            for (int j = 0; j < part.length(); j++) {
                char c = part.charAt(j);
                if (c < '0' || c > '9') return null;
                octet = octet * 10 + (c - '0');
            }
            if (octet > 255) return null;
            address[i] = (byte) octet;
        }
        return address;
    }

    private static byte[] parseIpv6(String value) {
        int zone = value.indexOf('%');
        String literal = zone >= 0 ? value.substring(0, zone) : value;
        if (literal.isEmpty()) return null;
        // The JDK only treats a string as a literal when it starts with a hex digit or ':'; anything else is resolved.
        if (!isHex(literal.charAt(0)) && literal.charAt(0) != ':') return null;
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            if (!isHex(c) && c != ':' && c != '.') return null;
        }
        try {
            // Starts like a literal and contains ':', so the JDK parses it as IPv6 or rejects it; it never looks it up.
            return InetAddress.getByName(literal).getAddress();
        } catch (UnknownHostException | SecurityException ex) {
            return null;
        }
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /** CIDR block such as {@code 172.16.0.0/12} or {@code ::1/128}. */
    record Range(byte[] network, int prefixLength) {
        static Range parse(String cidr) {
            String value = cidr.trim();
            int slash = value.indexOf('/');
            byte[] address = IpAddresses.parse(slash < 0 ? value : value.substring(0, slash));
            if (address == null) throw new IllegalArgumentException("Dải proxy tin cậy không hợp lệ: " + cidr);
            int bits = address.length * 8;
            int prefix = bits;
            if (slash >= 0) {
                try {
                    prefix = Integer.parseInt(value.substring(slash + 1));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Độ dài prefix không hợp lệ: " + cidr, ex);
                }
            }
            if (prefix < 0 || prefix > bits) throw new IllegalArgumentException("Độ dài prefix không hợp lệ: " + cidr);
            return new Range(address, prefix);
        }

        boolean contains(byte[] address) {
            if (address == null || address.length != network.length) return false;
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) return false;
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) return true;
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }

        @Override public String toString() { return format(network) + "/" + prefixLength; }
        @Override public boolean equals(Object other) {
            return other instanceof Range range && range.prefixLength == prefixLength && Arrays.equals(range.network, network);
        }
        @Override public int hashCode() { return 31 * Arrays.hashCode(network) + prefixLength; }
    }
}

package com.company.bds.analytics.domain;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * CIDR list of internal networks (office, VPN) whose traffic is marked internal ({@code app.analytics.internal-networks}).
 * Only literal IPv4/IPv6 addresses are accepted, so parsing never triggers a DNS lookup.
 */
public final class InternalNetworks {
    private static final Pattern LITERAL = Pattern.compile("(\\d{1,3}\\.){3}\\d{1,3}|[0-9A-Fa-f.]*:[0-9A-Fa-f:.]*");

    private record Range(byte[] network, int prefix) {
        boolean contains(byte[] address) {
            if (address.length != network.length) return false;
            int full = prefix / 8;
            for (int i = 0; i < full; i++) if (address[i] != network[i]) return false;
            int rest = prefix % 8;
            if (rest == 0) return true;
            int mask = (0xFF << (8 - rest)) & 0xFF;
            return (address[full] & mask) == (network[full] & mask);
        }
    }

    private final List<Range> ranges;

    private InternalNetworks(List<Range> ranges) {
        this.ranges = ranges;
    }

    /** Parses {@code "10.0.0.0/8, 2001:db8::/32, 203.0.113.7"}; an invalid entry throws (the app fails at startup). */
    public static InternalNetworks parse(String csv) {
        List<Range> ranges = new ArrayList<>();
        if (csv != null) {
            for (String raw : csv.split(",")) {
                String entry = raw.trim();
                if (entry.isEmpty()) continue;
                int slash = entry.indexOf('/');
                byte[] network = literal(slash < 0 ? entry : entry.substring(0, slash));
                if (network == null) throw new IllegalArgumentException("Invalid internal network: " + entry);
                int prefix;
                try {
                    prefix = slash < 0 ? network.length * 8 : Integer.parseInt(entry.substring(slash + 1));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid internal network: " + entry);
                }
                if (prefix < 0 || prefix > network.length * 8) throw new IllegalArgumentException("Invalid internal network: " + entry);
                ranges.add(new Range(network, prefix));
            }
        }
        return new InternalNetworks(List.copyOf(ranges));
    }

    public boolean contains(String address) {
        if (ranges.isEmpty() || address == null) return false;
        byte[] parsed = literal(address);
        if (parsed == null) return false;
        for (Range range : ranges) if (range.contains(parsed)) return true;
        return false;
    }

    private static byte[] literal(String value) {
        if (value == null || value.isEmpty() || !LITERAL.matcher(value).matches()) return null;
        try {
            return InetAddress.getByName(value).getAddress(); // literal only (checked above): no DNS lookup
        } catch (UnknownHostException ex) {
            return null;
        }
    }
}

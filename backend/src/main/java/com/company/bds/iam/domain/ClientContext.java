package com.company.bds.iam.domain;

import java.util.Locale;

/**
 * What a session or security event may remember about the client: a coarse browser/OS label (never the raw
 * User-Agent) and a network prefix (IPv4 /24, IPv6 /48), never a full address — enough for a person to recognise
 * "Chrome trên Windows, 203.0.113.x", not enough to track them.
 */
public record ClientContext(String ipHint, String deviceLabel) {
    public static final ClientContext UNKNOWN = new ClientContext(null, null);

    public static ClientContext of(String clientIp, String userAgent) {
        return new ClientContext(ipHint(clientIp), deviceLabel(userAgent));
    }

    static String ipHint(String ip) {
        if (ip == null || ip.isBlank()) return null;
        String value = ip.trim();
        if (value.startsWith("::ffff:") && value.indexOf('.') > 0) value = value.substring(7);
        if (value.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return value.substring(0, value.lastIndexOf('.')) + ".x";
        }
        if (value.contains(":")) {
            StringBuilder prefix = new StringBuilder();
            int kept = 0;
            for (String group : value.split(":", -1)) {
                if (group.isEmpty() || kept == 3) break;
                if (!group.matches("[0-9a-fA-F]{1,4}")) return null;
                if (kept > 0) prefix.append(':');
                prefix.append(group.toLowerCase(Locale.ROOT));
                kept++;
            }
            return kept == 0 ? null : prefix + "::/48";
        }
        return null;
    }

    static String deviceLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return null;
        String ua = userAgent.toLowerCase(Locale.ROOT);
        String browser = ua.contains("edg/") ? "Edge"
                : ua.contains("opr/") || ua.contains("opera") ? "Opera"
                : ua.contains("coc_coc") || ua.contains("coccoc") ? "Cốc Cốc"
                : ua.contains("samsungbrowser") ? "Samsung Internet"
                : ua.contains("firefox/") || ua.contains("fxios") ? "Firefox"
                : ua.contains("chrome/") || ua.contains("crios") ? "Chrome"
                : ua.contains("safari/") ? "Safari"
                : "Trình duyệt khác";
        String os = ua.contains("iphone") || ua.contains("ipad") ? "iOS"
                : ua.contains("android") ? "Android"
                : ua.contains("windows") ? "Windows"
                : ua.contains("mac os x") || ua.contains("macintosh") ? "macOS"
                : ua.contains("cros") ? "ChromeOS"
                : ua.contains("linux") ? "Linux"
                : "hệ điều hành khác";
        return browser + " trên " + os;
    }
}

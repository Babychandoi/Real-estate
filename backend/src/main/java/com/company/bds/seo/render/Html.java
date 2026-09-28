package com.company.bds.seo.render;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Escaping and formatting for server-rendered HTML (every dynamic value goes through {@link #esc}). */
public final class Html {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(VN);

    private Html() {}

    /** Text and attribute escaping (quotes included), so a value can never leave its element or attribute. */
    public static String esc(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> {
                    if (c < 0x20 && c != '\n' && c != '\t') continue;
                    out.append(c);
                }
            }
        }
        return out.toString();
    }

    /** Vietnamese money text like the SPA ({@code money.ts}): "3,5 tỷ", "850 triệu", "12 triệu/tháng". */
    public static String money(long vnd, String period) {
        String text;
        if (vnd >= 1_000_000_000L) text = number(vnd / 1_000_000_000d, 2) + " tỷ";
        else if (vnd >= 1_000_000L) text = number(vnd / 1_000_000d, 1) + " triệu";
        else text = number(vnd, 0) + " đ";
        return "MONTH".equals(period) ? text + "/tháng" : text;
    }

    public static String number(double value, int maxFraction) {
        DecimalFormat format = new DecimalFormat("#,##0." + "#".repeat(Math.max(1, maxFraction)), DecimalFormatSymbols.getInstance(Locale.forLanguageTag("vi-VN")));
        if (maxFraction == 0) format.setMaximumFractionDigits(0);
        return format.format(value);
    }

    public static String date(Instant value) {
        return value == null ? "" : DATE.format(value);
    }

    public static String propertyType(String type) {
        if (type == null) return "Bất động sản";
        return switch (type) {
            case "APARTMENT" -> "Căn hộ";
            case "HOUSE" -> "Nhà riêng";
            case "VILLA" -> "Biệt thự";
            case "TOWNHOUSE" -> "Nhà phố";
            case "LAND" -> "Đất";
            default -> "Bất động sản";
        };
    }

    public static String purpose(String purpose) {
        return "RENT".equals(purpose) ? "Cho thuê" : "Bán";
    }

    /** Cuts plain text at a word boundary. */
    public static String excerpt(String text, int max) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.length() <= max) return clean;
        int cut = clean.lastIndexOf(' ', max - 1);
        return clean.substring(0, cut > max / 2 ? cut : max - 1) + "…";
    }
}

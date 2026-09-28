package com.company.bds.engagement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Compact VND amounts for alert texts, the same wording as the web ({@code money.ts}): "3,95 tỷ", "14,5 triệu/tháng". */
public final class VndFormat {
    private static final long BILLION = 1_000_000_000L;
    private static final long MILLION = 1_000_000L;

    private VndFormat() {}

    public static String compact(long amount, String purpose) {
        String suffix = "RENT".equals(purpose) ? "/tháng" : "";
        if (amount >= BILLION) return decimal(amount, BILLION) + " tỷ" + suffix;
        if (amount >= MILLION) return decimal(amount, MILLION) + " triệu" + suffix;
        return String.format("%,d", amount).replace(',', '.') + " đ" + suffix;
    }

    private static String decimal(long amount, long unit) {
        BigDecimal value = BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(unit), 2, RoundingMode.HALF_UP).stripTrailingZeros();
        String text = value.scale() < 0 ? value.setScale(0).toPlainString() : value.toPlainString();
        return text.replace('.', ',');
    }
}

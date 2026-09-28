package com.company.bds.search.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** WGS84 bounding box {@code minLng,minLat,maxLng,maxLat}, rounded to 5 decimals (contract §7). */
public record BoundingBox(double minLng, double minLat, double maxLng, double maxLat) {
    public static final double MAX_SPAN_DEGREES = 3.0;

    public double lngSpan() { return maxLng - minLng; }

    public double latSpan() { return maxLat - minLat; }

    public boolean contains(Double lat, Double lng) {
        return lat != null && lng != null && lng >= minLng && lng <= maxLng && lat >= minLat && lat <= maxLat;
    }

    /** Canonical text form, the one used in URLs and in the filter hash. */
    public String canonical() {
        return format(minLng) + "," + format(minLat) + "," + format(maxLng) + "," + format(maxLat);
    }

    public static double round5(double value) {
        return BigDecimal.valueOf(value).setScale(5, RoundingMode.HALF_UP).doubleValue();
    }

    static String format(double value) {
        BigDecimal decimal = BigDecimal.valueOf(value).setScale(5, RoundingMode.HALF_UP).stripTrailingZeros();
        return decimal.scale() < 0 ? decimal.setScale(0).toPlainString() : decimal.toPlainString();
    }
}

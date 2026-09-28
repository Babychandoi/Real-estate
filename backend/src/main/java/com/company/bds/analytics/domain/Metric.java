package com.company.bds.analytics.domain;

import java.util.Objects;

/**
 * One dashboard number. {@code NOT_MEASURED} (value {@code null}) is different from a measured {@code 0}: the pipeline
 * that would produce the number is off, the filter does not apply to it, or there is no sample to compute a rate from.
 * {@code reason} says which, in Vietnamese, for the UI.
 *
 * @param numerator   for rates: the counted part (null for plain counts/durations)
 * @param denominator for rates: the base
 */
public record Metric(Status status, Double value, String unit, String reason, Long numerator, Long denominator) {
    public enum Status { MEASURED, NOT_MEASURED }

    public Metric {
        Objects.requireNonNull(status);
        Objects.requireNonNull(unit);
        if (status == Status.MEASURED && value == null) throw new IllegalArgumentException("a measured metric has a value");
        if (status == Status.NOT_MEASURED && (value != null || reason == null)) {
            throw new IllegalArgumentException("a metric that is not measured has no value and says why");
        }
    }

    public static Metric count(long value) {
        return new Metric(Status.MEASURED, (double) value, "count", null, null, null);
    }

    public static Metric value(double value, String unit) {
        double rounded = "score".equals(unit) ? Math.round(value * 1000.0) / 1000.0 : round(value);
        return new Metric(Status.MEASURED, rounded, unit, null, null, null);
    }

    /** A percentage; a zero base is not a 0 % rate, so it is reported as not measured. */
    public static Metric percent(long part, long base) {
        if (base <= 0) return new Metric(Status.NOT_MEASURED, null, "percent", "Chưa có mẫu để tính tỷ lệ.", part, base);
        return new Metric(Status.MEASURED, round(100.0 * part / base), "percent", null, part, base);
    }

    public static Metric notMeasured(String unit, String reason) {
        return new Metric(Status.NOT_MEASURED, null, unit, reason, null, null);
    }

    public boolean measured() { return status == Status.MEASURED; }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

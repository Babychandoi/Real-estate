package com.company.bds.shared.security.ratelimit;

import java.time.Duration;

/** At most {@code limit} requests per {@code window} for one value of {@code dimension}. */
public record RateLimitRule(RateLimitDimension dimension, int limit, Duration window) {
    public RateLimitRule {
        if (dimension == null) throw new IllegalArgumentException("Thiếu chiều giới hạn");
        if (limit < 1) throw new IllegalArgumentException("Giới hạn phải lớn hơn 0");
        if (window == null || window.toMillis() < 1000) throw new IllegalArgumentException("Cửa sổ giới hạn tối thiểu 1 giây");
    }

    public long windowMillis() { return window.toMillis(); }
}

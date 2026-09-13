package com.company.bds.media;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.media.storage-enabled", havingValue = "true")
public class MinioHealthIndicator implements HealthIndicator {
    private final MediaStorageService storage;
    public MinioHealthIndicator(MediaStorageService storage) { this.storage = storage; }
    @Override public Health health() {
        return storage.bucketAvailable() ? Health.up().build() : Health.down().withDetail("reason", "bucket unavailable").build();
    }
}

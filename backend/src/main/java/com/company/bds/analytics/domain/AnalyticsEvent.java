package com.company.bds.analytics.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One stored analytics fact ({@code analytics_events}). JSON columns are carried as already validated JSON text.
 * Without analytics consent {@code anonymousId}, {@code sessionId}, {@code userId} and {@code utmJson} are null.
 */
public record AnalyticsEvent(UUID eventId, String name, int schemaVersion, Instant occurredAt, String anonymousId,
                             String sessionId, UUID userId, UUID listingId, boolean internal, boolean bot, String origin,
                             String device, String areaCode, String pagePath, String propertiesJson, String utmJson) {
    public static final String ORIGIN_WEB = "web";
    public static final String ORIGIN_SERVER = "server";

    public AnalyticsEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(origin);
        propertiesJson = propertiesJson == null ? "{}" : propertiesJson;
    }

    public AnalyticsEvent withFlags(boolean internalFlag, boolean botFlag) {
        return new AnalyticsEvent(eventId, name, schemaVersion, occurredAt, anonymousId, sessionId, userId, listingId, internalFlag,
                botFlag, origin, device, areaCode, pagePath, propertiesJson, utmJson);
    }
}

package com.company.bds.analytics.application;

import com.company.bds.analytics.application.port.out.AnalyticsEventRepository;
import com.company.bds.analytics.domain.AnalyticsEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records server-side business facts (contract §5) in the caller's transaction, exactly once per fact:
 * {@code event_id = UUID.nameUUIDFromBytes("server:" + name + ":" + deterministicKey)} and duplicates are ignored, so a
 * retried or replayed request never counts twice and a rolled-back transaction records nothing.
 */
@Service
public class AnalyticsRecorder {
    private final AnalyticsEventRepository store;
    private final ObjectMapper json;
    private final Clock clock;

    public AnalyticsRecorder(AnalyticsEventRepository store, ObjectMapper json, Clock clock) {
        this.store = store;
        this.json = json;
        this.clock = clock;
    }

    /**
     * @param deterministicKey identifies the business fact (e.g. the lead id, or listing id + revision number)
     * @param userId           the user the fact is about/performed by; staff users mark the event internal
     * @return whether the event was new
     * @throws IllegalArgumentException for an unknown/web-only event, a wrong version or properties outside the catalog
     */
    public boolean recordServer(String name, int version, String deterministicKey, @Nullable UUID userId, @Nullable UUID listingId,
                                Map<String, ?> properties) {
        EventCatalog.EventDefinition definition = EventCatalog.find(name)
                .filter(event -> event.origin() == EventCatalog.Origin.SERVER)
                .orElseThrow(() -> new IllegalArgumentException("Unknown server analytics event " + name));
        if (definition.version() != version) {
            throw new IllegalArgumentException("Analytics event " + name + " is at version " + definition.version() + ", not " + version);
        }
        if (deterministicKey == null || deterministicKey.isBlank()) throw new IllegalArgumentException("deterministicKey is required");
        if (definition.requiresListing() && listingId == null) throw new IllegalArgumentException("Analytics event " + name + " needs a listing id");
        JsonNode props = json.valueToTree(properties == null ? Map.of() : properties);
        List<String> problems = EventValidation.propertyProblems(definition, props);
        if (!problems.isEmpty()) throw new IllegalArgumentException("Invalid properties for " + name + ": " + String.join("; ", problems));
        return store.insertServerEvent(new AnalyticsEvent(serverEventId(name, deterministicKey), name, version, clock.instant(),
                null, null, userId, listingId, false, false, AnalyticsEvent.ORIGIN_SERVER, null, null, null, props.toString(), null));
    }

    public static UUID serverEventId(String name, String deterministicKey) {
        return UUID.nameUUIDFromBytes(("server:" + name + ":" + deterministicKey).getBytes(StandardCharsets.UTF_8));
    }
}

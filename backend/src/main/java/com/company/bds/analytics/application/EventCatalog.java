package com.company.bds.analytics.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Event catalog v1 (contract §5). The listed properties are the only allowed keys; adding an event means a new row and
 * version, never reusing a name with different semantics.
 */
public final class EventCatalog {

    public enum Origin { WEB, SERVER }

    /** How one property value is validated; {@code required} keys must be present, {@code nullable} ones may be null. */
    public record PropertyRule(String name, boolean required, boolean nullable, String expectation, Predicate<JsonNode> accepts) {}

    public record EventDefinition(String name, int version, Origin origin, boolean requiresListing, Map<String, PropertyRule> properties) {
        public boolean allows(String property) { return properties.containsKey(property); }
    }

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{32}");
    /** Administrative codes are digits only, so the field cannot carry names or phone numbers. */
    private static final Pattern DISTRICT = Pattern.compile("[0-9]{1,5}");
    private static final Pattern CONTEXT = Pattern.compile("[a-z][a-z0-9_-]{0,39}");
    private static final Pattern ROUTE = Pattern.compile("/[A-Za-z0-9/:_.-]{0,99}");
    private static final Pattern FREQUENCY = Pattern.compile("[A-Z_]{1,20}");

    private static final Map<String, EventDefinition> EVENTS = new LinkedHashMap<>();

    static {
        web("search_performed", false,
                required("filterHash", "32 hex characters", text(HASH)),
                required("purpose", "SALE or RENT", oneOf("SALE", "RENT")),
                nullable("resultCount", "an integer >= 0 or null", nonNegativeInteger()),
                optional("zeroResult", "a boolean", JsonNode::isBoolean),
                optional("engine", "search or database", oneOf("search", "database")),
                optional("hasBbox", "a boolean", JsonNode::isBoolean),
                optional("hasKeyword", "a boolean", JsonNode::isBoolean));
        web("search_results_viewed", false,
                required("filterHash", "32 hex characters", text(HASH)),
                required("listingIds", "an array of at most 48 listing UUIDs", uuidArray(48)),
                optional("offset", "an integer >= 0", nonNegativeInteger()));
        web("listing_detail_viewed", true,
                optional("purpose", "SALE or RENT", oneOf("SALE", "RENT")),
                optional("propertyType", "a property type", oneOf("APARTMENT", "HOUSE", "VILLA", "TOWNHOUSE", "LAND")),
                optional("district", "a district code", text(DISTRICT)));
        server("listing_favorited", true);
        server("listing_unfavorited", true);
        web("compare_opened", false,
                required("listingIds", "an array of at most 48 listing UUIDs", uuidArray(48)));
        server("saved_search_created", false,
                required("filterHash", "32 hex characters", text(HASH)),
                optional("frequency", "an upper-case frequency code", text(FREQUENCY)));
        web("lead_form_opened", true,
                optional("requestType", "VIEWING or CONSULTATION", oneOf("VIEWING", "CONSULTATION")));
        web("kyc_required_shown", false,
                required("context", "a lower-case context code", text(CONTEXT)));
        // F17.4 (W6): the lead API refused the request because the requester's identity is not verified. Recorded by the
        // server (no consent needed, no session), once per requester, listing and day.
        server("lead_kyc_blocked", true,
                required("context", "a lower-case context code", text(CONTEXT)));
        server("lead_submitted", true,
                required("leadId", "a UUID", uuid()),
                required("requestType", "VIEWING or CONSULTATION", oneOf("VIEWING", "CONSULTATION")));
        server("lead_first_response", true,
                required("leadId", "a UUID", uuid()),
                required("minutes", "an integer >= 0", nonNegativeInteger()));
        server("lead_qualified", true,
                required("leadId", "a UUID", uuid()),
                required("qualification", "QUALIFIED or UNQUALIFIED", oneOf("QUALIFIED", "UNQUALIFIED")));
        for (String appointment : List.of("appointment_proposed", "appointment_confirmed", "appointment_completed",
                "appointment_no_show", "appointment_cancelled")) {
            server(appointment, true, required("appointmentId", "a UUID", uuid()), required("leadId", "a UUID", uuid()));
        }
        server("listing_published", true,
                required("revisionNumber", "an integer >= 1", node -> node.canConvertToInt() && node.isIntegralNumber() && node.asInt() >= 1));
        web("web_vital", false,
                required("metric", "LCP, INP, CLS or TTFB", oneOf("LCP", "INP", "CLS", "TTFB")),
                required("value", "a number >= 0", node -> node.isNumber() && Double.isFinite(node.asDouble()) && node.asDouble() >= 0),
                optional("rating", "good, needs-improvement or poor", oneOf("good", "needs-improvement", "poor")),
                optional("route", "a route pattern such as /listings/:slug", text(ROUTE)));
    }

    private EventCatalog() {}

    public static Optional<EventDefinition> find(String name) { return Optional.ofNullable(EVENTS.get(name)); }

    public static Collection<EventDefinition> all() { return EVENTS.values(); }

    // ------------------------------------------------------------------ definition helpers

    private static void web(String name, boolean requiresListing, PropertyRule... rules) { define(name, Origin.WEB, requiresListing, rules); }

    private static void server(String name, boolean requiresListing, PropertyRule... rules) { define(name, Origin.SERVER, requiresListing, rules); }

    private static void define(String name, Origin origin, boolean requiresListing, PropertyRule... rules) {
        Map<String, PropertyRule> properties = new LinkedHashMap<>();
        for (PropertyRule rule : rules) properties.put(rule.name(), rule);
        EVENTS.put(name, new EventDefinition(name, 1, origin, requiresListing, Map.copyOf(properties)));
    }

    private static PropertyRule required(String name, String expectation, Predicate<JsonNode> accepts) {
        return new PropertyRule(name, true, false, expectation, accepts);
    }

    private static PropertyRule optional(String name, String expectation, Predicate<JsonNode> accepts) {
        return new PropertyRule(name, false, false, expectation, accepts);
    }

    private static PropertyRule nullable(String name, String expectation, Predicate<JsonNode> accepts) {
        return new PropertyRule(name, false, true, expectation, accepts);
    }

    private static Predicate<JsonNode> oneOf(String... values) {
        Set<String> allowed = Set.of(values);
        return node -> node.isTextual() && allowed.contains(node.asText());
    }

    private static Predicate<JsonNode> text(Pattern pattern) {
        return node -> node.isTextual() && pattern.matcher(node.asText()).matches();
    }

    private static Predicate<JsonNode> nonNegativeInteger() {
        return node -> node.isIntegralNumber() && node.canConvertToLong() && node.asLong() >= 0;
    }

    private static Predicate<JsonNode> uuid() {
        return node -> node.isTextual() && isUuid(node.asText());
    }

    private static Predicate<JsonNode> uuidArray(int max) {
        return node -> {
            if (!node.isArray() || node.size() > max) return false;
            for (JsonNode item : node) if (!item.isTextual() || !isUuid(item.asText())) return false;
            return true;
        };
    }

    static boolean isUuid(String value) {
        if (value.length() != 36) return false;
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}

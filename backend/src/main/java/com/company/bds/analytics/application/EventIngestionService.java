package com.company.bds.analytics.application;

import com.company.bds.analytics.domain.AnalyticsEvent;
import com.company.bds.analytics.domain.BotDetector;
import com.company.bds.analytics.infrastructure.AnalyticsEventStore;
import com.company.bds.shared.error.ProblemDetails.ValidationErrorItem;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Web event ingestion ({@code POST /api/v1/events}, contract §5). A batch is validated as a whole against catalog v1 and
 * rejected with every problem listed, or stored in one transaction. Consent, identity and bot/internal flags are decided
 * here, never taken from the body.
 */
@Service
public class EventIngestionService {
    public static final int MAX_EVENTS = 50;
    static final Duration MAX_AGE = Duration.ofDays(7);
    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final int MAX_ERRORS = 25;
    private static final Set<String> BATCH_FIELDS = Set.of("consent", "events");
    private static final Set<String> EVENT_FIELDS = Set.of("eventId", "name", "v", "occurredAt", "anonymousId", "sessionId",
            "listingId", "properties", "page", "utm", "device");
    private static final Set<String> PAGE_FIELDS = Set.of("path", "referrer");
    private static final Set<String> UTM_FIELDS = Set.of("source", "medium", "campaign", "term", "content");
    private static final Set<String> DEVICES = Set.of("mobile", "tablet", "desktop");
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final Pattern PAGE_PATH = Pattern.compile("/[A-Za-z0-9/_.~%:@!$&'()*+,;=-]{0,199}");

    /** Who sent the batch, from the bearer token and request headers only. */
    public record Viewer(@Nullable UUID userId, boolean staff, @Nullable String userAgent) {}

    public record IngestionResult(int accepted, int duplicates) {}

    private final AnalyticsEventStore store;
    private final Clock clock;

    public EventIngestionService(AnalyticsEventStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public IngestionResult ingest(JsonNode batch, Viewer viewer) {
        Errors errors = new Errors();
        if (batch == null || !batch.isObject()) {
            errors.add("body", "INVALID_BODY", "Nội dung phải là object JSON {consent, events}.");
            throw errors.exception();
        }
        batch.fieldNames().forEachRemaining(field -> {
            if (!BATCH_FIELDS.contains(field)) errors.add(field, "UNKNOWN_FIELD", "Trường không được hỗ trợ.");
        });
        boolean consent = consent(batch.get("consent"), errors);
        JsonNode events = batch.get("events");
        if (events == null || !events.isArray() || events.isEmpty() || events.size() > MAX_EVENTS) {
            errors.add("events", "INVALID_BATCH_SIZE", "Cần từ 1 đến " + MAX_EVENTS + " sự kiện trong một lần gửi.");
            throw errors.exception();
        }
        Instant now = clock.instant();
        boolean bot = BotDetector.isBot(viewer.userAgent());
        Map<UUID, AnalyticsEvent> unique = new LinkedHashMap<>();
        for (int i = 0; i < events.size(); i++) {
            AnalyticsEvent event = parse(events.get(i), "events[" + i + "]", consent, viewer, bot, now, errors);
            if (event != null) unique.putIfAbsent(event.eventId(), event);
        }
        if (errors.any()) throw errors.exception();
        int inserted = store.insertWebEvents(List.copyOf(unique.values()));
        return new IngestionResult(inserted, events.size() - inserted);
    }

    private static boolean consent(JsonNode value, Errors errors) {
        if (value == null || value.isNull()) return false; // no answer means no analytics consent
        if (value.isTextual() && "granted".equals(value.asText())) return true;
        if (value.isTextual() && "denied".equals(value.asText())) return false;
        errors.add("consent", "INVALID_CONSENT", "consent phải là \"granted\" hoặc \"denied\".");
        return false;
    }

    @Nullable
    private static AnalyticsEvent parse(JsonNode node, String at, boolean consent, Viewer viewer, boolean bot, Instant now, Errors errors) {
        if (node == null || !node.isObject()) {
            errors.add(at, "INVALID_EVENT", "Sự kiện phải là object JSON.");
            return null;
        }
        int before = errors.count();
        node.fieldNames().forEachRemaining(field -> {
            if (!EVENT_FIELDS.contains(field)) errors.add(at + "." + field, "UNKNOWN_FIELD", "Trường không có trong hợp đồng sự kiện.");
        });
        UUID eventId = webEventId(node.get("eventId"), at, errors);
        String name = node.path("name").asText(null);
        EventCatalog.EventDefinition definition = name == null ? null : EventCatalog.find(name).orElse(null);
        if (definition == null) {
            errors.add(at + ".name", "UNKNOWN_EVENT", "Tên sự kiện không có trong danh mục.");
            return null;
        }
        if (definition.origin() != EventCatalog.Origin.WEB) {
            errors.add(at + ".name", "SERVER_ONLY_EVENT", "Sự kiện " + name + " chỉ được ghi nhận từ máy chủ.");
            return null;
        }
        JsonNode version = node.get("v");
        if (version == null || !version.isIntegralNumber() || version.asInt() != definition.version()) {
            errors.add(at + ".v", "UNSUPPORTED_VERSION", "Sự kiện " + name + " dùng phiên bản " + definition.version() + ".");
        }
        Instant occurredAt = occurredAt(node.get("occurredAt"), now, at, errors);
        String anonymousId = clientId(node.get("anonymousId"), at + ".anonymousId", errors);
        String sessionId = clientId(node.get("sessionId"), at + ".sessionId", errors);
        UUID listingId = optionalUuid(node.get("listingId"), at + ".listingId", errors);
        if (definition.requiresListing() && listingId == null && !node.hasNonNull("listingId")) {
            errors.add(at + ".listingId", "LISTING_REQUIRED", "Sự kiện " + name + " cần listingId.");
        }
        JsonNode properties = node.hasNonNull("properties") ? node.get("properties") : null;
        for (String problem : EventValidation.propertyProblems(definition, properties)) {
            errors.add(at + ".properties", "INVALID_PROPERTY", problem);
        }
        String pagePath = pagePath(node.get("page"), at + ".page", errors);
        String utm = utm(node.get("utm"), at + ".utm", errors);
        String device = device(node.get("device"), at + ".device", errors);
        if (errors.count() > before || eventId == null || occurredAt == null) return null;
        String areaCode = properties != null && properties.hasNonNull("district") ? properties.get("district").asText() : null;
        return new AnalyticsEvent(eventId, name, definition.version(), occurredAt,
                consent ? anonymousId : null, consent ? sessionId : null, consent ? viewer.userId() : null, listingId,
                viewer.staff(), bot, AnalyticsEvent.ORIGIN_WEB, device, areaCode, pagePath,
                properties == null ? "{}" : properties.toString(), consent ? utm : null);
    }

    /** Client ids must be random UUIDv4: server events use name-based ids, so a client can never pre-empt one. */
    @Nullable
    private static UUID webEventId(JsonNode value, String at, Errors errors) {
        UUID id = value != null && value.isTextual() && EventCatalog.isUuid(value.asText()) ? UUID.fromString(value.asText()) : null;
        if (id == null || id.version() != 4 || id.variant() != 2) {
            errors.add(at + ".eventId", "INVALID_EVENT_ID", "eventId phải là UUID phiên bản 4.");
            return null;
        }
        return id;
    }

    @Nullable
    private static Instant occurredAt(JsonNode value, Instant now, String at, Errors errors) {
        Instant instant = null;
        if (value != null && value.isTextual()) {
            try {
                instant = OffsetDateTime.parse(value.asText()).toInstant();
            } catch (DateTimeParseException ignored) {
                // reported below
            }
        }
        if (instant == null || instant.isBefore(now.minus(MAX_AGE)) || instant.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            errors.add(at + ".occurredAt", "INVALID_OCCURRED_AT", "occurredAt phải là thời điểm ISO-8601 trong 7 ngày gần nhất.");
            return null;
        }
        return instant;
    }

    @Nullable
    private static String clientId(JsonNode value, String at, Errors errors) {
        if (value == null || value.isNull()) return null;
        if (value.isTextual() && CLIENT_ID.matcher(value.asText()).matches()) return value.asText();
        errors.add(at, "INVALID_CLIENT_ID", "Mã định danh phải gồm 8–64 ký tự chữ, số, '-' hoặc '_'.");
        return null;
    }

    @Nullable
    private static UUID optionalUuid(JsonNode value, String at, Errors errors) {
        if (value == null || value.isNull()) return null;
        if (value.isTextual() && EventCatalog.isUuid(value.asText())) return UUID.fromString(value.asText());
        errors.add(at, "INVALID_LISTING_ID", "listingId phải là UUID.");
        return null;
    }

    /** Keeps the path only: query strings and fragments can carry tokens (reset links) or search text. */
    @Nullable
    private static String pagePath(JsonNode value, String at, Errors errors) {
        if (value == null || value.isNull()) return null;
        JsonNode path = value;
        if (value.isObject()) {
            value.fieldNames().forEachRemaining(field -> {
                if (!PAGE_FIELDS.contains(field)) errors.add(at + "." + field, "UNKNOWN_FIELD", "Trường không được hỗ trợ.");
            });
            path = value.get("path");
        }
        if (path != null && path.isTextual()) {
            String raw = path.asText();
            int cut = raw.length();
            for (char separator : new char[] {'?', '#'}) {
                int index = raw.indexOf(separator);
                if (index >= 0) cut = Math.min(cut, index);
            }
            String stripped = raw.substring(0, cut);
            if (PAGE_PATH.matcher(stripped).matches()) return stripped;
        }
        errors.add(at, "INVALID_PAGE", "page phải là đường dẫn bắt đầu bằng '/' (tối đa 200 ký tự).");
        return null;
    }

    @Nullable
    private static String utm(JsonNode value, String at, Errors errors) {
        if (value == null || value.isNull()) return null;
        if (!value.isObject()) {
            errors.add(at, "INVALID_UTM", "utm phải là object.");
            return null;
        }
        boolean valid = true;
        for (Iterator<Map.Entry<String, JsonNode>> it = value.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            if (!UTM_FIELDS.contains(field.getKey()) || !field.getValue().isTextual() || field.getValue().asText().length() > 100) {
                errors.add(at + "." + field.getKey(), "INVALID_UTM", "Chỉ nhận source, medium, campaign, term, content (chuỗi ≤ 100 ký tự).");
                valid = false;
            }
        }
        return valid && !value.isEmpty() ? value.toString() : null;
    }

    @Nullable
    private static String device(JsonNode value, String at, Errors errors) {
        if (value == null || value.isNull()) return null;
        if (value.isTextual() && DEVICES.contains(value.asText())) return value.asText();
        errors.add(at, "INVALID_DEVICE", "device phải là mobile, tablet hoặc desktop.");
        return null;
    }

    private static final class Errors {
        private final List<ValidationErrorItem> items = new ArrayList<>();
        private int total;

        void add(String field, String code, String message) {
            total++;
            if (items.size() < MAX_ERRORS) items.add(new ValidationErrorItem(field, code, message));
        }

        int count() { return total; }

        boolean any() { return total > 0; }

        InvalidEventsException exception() { return new InvalidEventsException(items); }
    }
}

package com.company.bds.analytics.infrastructure.persistence;

import com.company.bds.analytics.application.port.out.AnalyticsEventRepository;
import com.company.bds.analytics.domain.AnalyticsEvent;
import com.company.bds.analytics.domain.DeviceFlag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@link AnalyticsEventRepository} on {@code analytics_events}: append-only, a repeated event id is ignored (dedupe). */
@Component
class AnalyticsEventPersistenceAdapter implements AnalyticsEventRepository {
    private static final String INSERT = """
            INSERT INTO analytics_events (event_id, name, schema_version, occurred_at, anonymous_id, session_id, user_id, listing_id,
                                          is_internal, is_bot, origin, device, area_code, page_path, properties, utm)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, %s, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
            ON CONFLICT (event_id) DO NOTHING
            """;
    /** Server events: staff activity is marked internal from the actor's stored role. */
    private static final String INSERT_SERVER = INSERT.formatted(
            "EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = CAST(? AS uuid) AND r.role IN ('ADMIN', 'MODERATOR'))");
    private static final String INSERT_WEB = INSERT.formatted("?");

    private final JdbcTemplate jdbc;

    AnalyticsEventPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int insertWebEvents(List<AnalyticsEvent> events) {
        if (events.isEmpty()) return 0;
        int[][] counts = jdbc.batchUpdate(INSERT_WEB, events, events.size(), (statement, event) -> {
            bindHead(statement, event);
            statement.setBoolean(9, event.internal());
            bindTail(statement, event, 10);
        });
        // ON CONFLICT DO NOTHING reports 0 for a duplicate id and 1 for a new row.
        return Arrays.stream(counts).flatMapToInt(Arrays::stream).map(count -> count > 0 ? 1 : 0).sum();
    }

    @Override
    public boolean insertServerEvent(AnalyticsEvent event) {
        return jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT_SERVER);
            bindHead(statement, event);
            statement.setObject(9, event.userId() == null ? null : event.userId().toString());
            bindTail(statement, event, 10);
            return statement;
        }) == 1;
    }

    @Override
    public Map<String, Set<DeviceFlag>> deviceFlags(Collection<String> anonymousIds) {
        if (anonymousIds.isEmpty()) return Map.of();
        Map<String, Set<DeviceFlag>> flags = new HashMap<>();
        jdbc.query("SELECT anonymous_id, kind FROM analytics_client_flags WHERE anonymous_id = ANY (?)",
                rs -> {
                    flags.computeIfAbsent(rs.getString(1), key -> EnumSet.noneOf(DeviceFlag.class)).add(DeviceFlag.valueOf(rs.getString(2)));
                },
                (Object) anonymousIds.toArray(String[]::new));
        return flags;
    }

    @Override
    public boolean flagDevice(String anonymousId, DeviceFlag flag, String reason) {
        int inserted = jdbc.update("""
                INSERT INTO analytics_client_flags (anonymous_id, kind, reason) VALUES (?, ?, ?)
                ON CONFLICT (anonymous_id, kind) DO NOTHING
                """, anonymousId, flag.name(), reason);
        if (inserted == 0) return false;
        String column = flag == DeviceFlag.INTERNAL ? "is_internal" : "is_bot";
        jdbc.update("UPDATE analytics_events SET " + column + " = TRUE WHERE anonymous_id = ? AND " + column + " = FALSE", anonymousId);
        return true;
    }

    private static void bindHead(PreparedStatement statement, AnalyticsEvent event) throws SQLException {
        statement.setObject(1, event.eventId());
        statement.setString(2, event.name());
        statement.setShort(3, (short) event.schemaVersion());
        statement.setTimestamp(4, Timestamp.from(event.occurredAt()));
        statement.setString(5, event.anonymousId());
        statement.setString(6, event.sessionId());
        statement.setObject(7, event.userId(), java.sql.Types.OTHER);
        statement.setObject(8, event.listingId(), java.sql.Types.OTHER);
    }

    private static void bindTail(PreparedStatement statement, AnalyticsEvent event, int first) throws SQLException {
        statement.setBoolean(first, event.bot());
        statement.setString(first + 1, event.origin());
        statement.setString(first + 2, event.device());
        statement.setString(first + 3, event.areaCode());
        statement.setString(first + 4, event.pagePath());
        statement.setString(first + 5, event.propertiesJson());
        statement.setString(first + 6, event.utmJson());
    }
}

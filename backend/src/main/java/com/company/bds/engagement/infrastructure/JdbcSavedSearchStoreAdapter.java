package com.company.bds.engagement.infrastructure;

import com.company.bds.engagement.application.port.SavedSearchStorePort;
import com.company.bds.engagement.domain.ListingChange;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

@Repository
public class JdbcSavedSearchStoreAdapter implements SavedSearchStorePort {
    private static final String COLUMNS = """
            id, user_id, name, params::text AS params, filter_hash, purpose, types, districts, price_min, price_max, frequency,
            alert_new, alert_price_drop, alert_back_on_market, paused, next_digest_at, last_digest_at, version, created_at,
            updated_at""";
    private static final TypeReference<TreeMap<String, String>> PARAMS = new TypeReference<>() {};
    private static final UUID MIN_UUID = new UUID(0L, 0L);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<SavedSearch> row;

    public JdbcSavedSearchStoreAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.row = (rs, n) -> new SavedSearch(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("name"), params(rs.getString("params")), rs.getString("filter_hash"), rs.getString("purpose"),
                strings(rs.getArray("types")), strings(rs.getArray("districts")), (Long) rs.getObject("price_min"),
                (Long) rs.getObject("price_max"), rs.getString("frequency"), rs.getBoolean("alert_new"),
                rs.getBoolean("alert_price_drop"), rs.getBoolean("alert_back_on_market"), rs.getBoolean("paused"),
                instant(rs, "next_digest_at"), instant(rs, "last_digest_at"), rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    @Override
    public void insert(SavedSearch s) {
        jdbc.update("""
                INSERT INTO saved_searches (id, user_id, name, params, filter_hash, purpose, types, districts, price_min,
                    price_max, frequency, alert_new, alert_price_drop, alert_back_on_market, paused, next_digest_at,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                s.id(), s.userId(), s.name(), write(s.params()), s.filterHash(), s.purpose(), SqlArrays.texts(s.types()),
                SqlArrays.texts(s.districts()), s.priceMin(), s.priceMax(), s.frequency(), s.alertNew(), s.alertPriceDrop(),
                s.alertBackOnMarket(), s.paused(), ts(s.nextDigestAt()), ts(s.createdAt()), ts(s.updatedAt()));
    }

    @Override
    public Optional<UUID> findIdByHash(UUID userId, String filterHash) {
        return jdbc.queryForList("SELECT id FROM saved_searches WHERE user_id = ? AND filter_hash = ?", UUID.class,
                userId, filterHash).stream().findFirst();
    }

    @Override
    public int count(UUID userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM saved_searches WHERE user_id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }

    @Override
    public List<SavedSearch> list(UUID userId, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM saved_searches WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?",
                row, userId, limit);
    }

    @Override
    public Optional<SavedSearch> find(UUID userId, UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM saved_searches WHERE user_id = ? AND id = ?", row, userId, id)
                .stream().findFirst();
    }

    @Override
    public Map<UUID, Integer> pendingCounts(UUID userId) {
        Map<UUID, Integer> out = new HashMap<>();
        jdbc.query("""
                SELECT m.saved_search_id, count(*) FROM saved_search_matches m
                  JOIN saved_searches s ON s.id = m.saved_search_id AND s.user_id = ?
                 WHERE m.delivered_at IS NULL GROUP BY m.saved_search_id""",
                rs -> { out.put(rs.getObject(1, UUID.class), rs.getInt(2)); }, userId);
        return out;
    }

    @Override
    public boolean update(SavedSearch s, long expectedVersion) {
        return jdbc.update("""
                UPDATE saved_searches SET name = ?, frequency = ?, alert_new = ?, alert_price_drop = ?,
                       alert_back_on_market = ?, paused = ?, next_digest_at = ?, version = version + 1, updated_at = ?
                 WHERE id = ? AND user_id = ? AND version = ?""",
                s.name(), s.frequency(), s.alertNew(), s.alertPriceDrop(), s.alertBackOnMarket(), s.paused(),
                ts(s.nextDigestAt()), ts(s.updatedAt()), s.id(), s.userId(), expectedVersion) > 0;
    }

    @Override
    public boolean delete(UUID userId, UUID id) {
        return jdbc.update("DELETE FROM saved_searches WHERE id = ? AND user_id = ?", id, userId) > 0;
    }

    @Override
    public boolean stopAlerts(UUID userId, UUID id) {
        return jdbc.update("""
                UPDATE saved_searches SET frequency = 'OFF', next_digest_at = NULL, version = version + 1, updated_at = now()
                 WHERE id = ? AND user_id = ?""", id, userId) > 0;
    }

    @Override
    public List<Candidate> candidates(ListingChange.Kind kind, String purpose, String propertyType, String districtCode,
                                      long priceVnd, UUID listingOwnerId, Instant createdBefore, @Nullable UUID afterId,
                                      int limit) {
        String flag = switch (kind) {
            case NEW -> "alert_new";
            case PRICE_DROP -> "alert_price_drop";
            case BACK_ON_MARKET -> "alert_back_on_market";
        };
        return jdbc.query("""
                SELECT s.id, s.user_id, s.params::text FROM saved_searches s
                  JOIN users u ON u.id = s.user_id AND u.status = 'ACTIVE'
                 WHERE s.frequency <> 'OFF' AND NOT s.paused AND s.%s
                   AND s.purpose = ? AND s.user_id <> ? AND s.created_at < ?
                   AND (cardinality(s.types) = 0 OR ? = ANY(s.types))
                   AND (cardinality(s.districts) = 0 OR ? = ANY(s.districts))
                   AND (s.price_min IS NULL OR s.price_min <= ?) AND (s.price_max IS NULL OR s.price_max >= ?)
                   AND s.id > ?
                 ORDER BY s.id LIMIT ?""".formatted(flag),
                (rs, n) -> new Candidate(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), params(rs.getString(3))),
                purpose, listingOwnerId, ts(createdBefore), propertyType, districtCode, priceVnd, priceVnd,
                afterId == null ? MIN_UUID : afterId, limit);
    }

    @Override
    public boolean insertMatch(UUID searchId, UUID listingId, ListingChange.Kind kind, String factKey, long priceVnd,
                               @Nullable Long previousPriceVnd, Instant now) {
        return jdbc.update("""
                INSERT INTO saved_search_matches (saved_search_id, listing_id, kind, fact_key, price_vnd, previous_price_vnd, matched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (saved_search_id, listing_id, kind, fact_key) DO NOTHING""",
                searchId, listingId, kind.name(), factKey, priceVnd, previousPriceVnd, ts(now)) > 0;
    }

    @Override
    public Optional<ListingChange.Seen> lockState(UUID listingId) {
        return jdbc.query("SELECT visible, purpose, price_vnd, hidden_at FROM engage_listing_state WHERE listing_id = ? FOR UPDATE",
                (rs, n) -> new ListingChange.Seen(rs.getBoolean(1), rs.getString(2), (Long) rs.getObject(3), instant(rs, "hidden_at")),
                listingId).stream().findFirst();
    }

    @Override
    public boolean listingExists(UUID listingId) {
        return !jdbc.queryForList("SELECT 1 FROM listings WHERE id = ?", Integer.class, listingId).isEmpty();
    }

    @Override
    public void saveState(UUID listingId, boolean visible, @Nullable String purpose, @Nullable Long priceVnd,
                          @Nullable Instant hiddenAt, Instant now) {
        jdbc.update("""
                INSERT INTO engage_listing_state (listing_id, visible, purpose, price_vnd, first_public_at, hidden_at, updated_at)
                VALUES (?, ?, ?, ?, CASE WHEN ? THEN ?::timestamptz END, ?, ?)
                ON CONFLICT (listing_id) DO UPDATE SET visible = EXCLUDED.visible,
                    purpose = coalesce(EXCLUDED.purpose, engage_listing_state.purpose),
                    price_vnd = coalesce(EXCLUDED.price_vnd, engage_listing_state.price_vnd),
                    first_public_at = coalesce(engage_listing_state.first_public_at, EXCLUDED.first_public_at),
                    hidden_at = EXCLUDED.hidden_at, updated_at = EXCLUDED.updated_at""",
                listingId, visible, purpose, priceVnd, visible, ts(now), ts(hiddenAt), ts(now));
    }

    @Override
    public List<UUID> dueSearches(Instant now, int limit) {
        return jdbc.queryForList("""
                SELECT s.id FROM saved_searches s
                 WHERE s.frequency <> 'OFF' AND NOT s.paused AND (s.next_digest_at IS NULL OR s.next_digest_at <= ?)
                   AND EXISTS (SELECT 1 FROM saved_search_matches m WHERE m.saved_search_id = s.id AND m.delivered_at IS NULL)
                 ORDER BY s.next_digest_at NULLS FIRST, s.id LIMIT ?""", UUID.class, ts(now), limit);
    }

    @Override
    public Optional<SavedSearch> lockDue(UUID id, Instant now) {
        return jdbc.query("SELECT " + COLUMNS + " " + """
                 FROM saved_searches WHERE id = ? AND frequency <> 'OFF' AND NOT paused
                   AND (next_digest_at IS NULL OR next_digest_at <= ?) FOR UPDATE SKIP LOCKED""", row, id, ts(now))
                .stream().findFirst();
    }

    @Override
    public List<Match> takePending(UUID searchId, Instant now) {
        return jdbc.query("""
                UPDATE saved_search_matches SET delivered_at = ? WHERE saved_search_id = ? AND delivered_at IS NULL
                RETURNING id, listing_id, kind, price_vnd, previous_price_vnd, matched_at""",
                (rs, n) -> new Match(rs.getLong(1), rs.getObject(2, UUID.class), ListingChange.Kind.valueOf(rs.getString(3)),
                        rs.getLong(4), (Long) rs.getObject(5), rs.getTimestamp(6).toInstant()),
                ts(now), searchId).stream().sorted((a, b) -> Long.compare(a.id(), b.id())).toList();
    }

    @Override
    public void markDigested(UUID searchId, Instant now, @Nullable Instant nextDigestAt) {
        jdbc.update("UPDATE saved_searches SET last_digest_at = ?, next_digest_at = ? WHERE id = ?",
                ts(now), ts(nextDigestAt), searchId);
    }

    @Override
    public int purgeMatches(Instant deliveredBefore, Instant pendingBefore) {
        return jdbc.update("""
                DELETE FROM saved_search_matches
                 WHERE (delivered_at IS NOT NULL AND delivered_at < ?) OR (delivered_at IS NULL AND matched_at < ?)""",
                ts(deliveredBefore), ts(pendingBefore));
    }

    private Map<String, String> params(String text) {
        try {
            return json.readValue(text, PARAMS);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unreadable saved search params", ex);
        }
    }

    private String write(Map<String, String> params) {
        try {
            return json.writeValueAsString(new TreeMap<>(params));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) return List.of();
        return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}

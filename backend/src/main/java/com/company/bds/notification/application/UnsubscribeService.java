package com.company.bds.notification.application;

import com.company.bds.notification.application.port.SavedSearchAlertsPort;
import com.company.bds.notification.domain.NotificationCategory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One-click unsubscribe links for alert e-mails (RFC 8058). A link carries a random 256-bit token; only its SHA-256 is
 * stored, bound to one user and one scope (a saved search, or the e-mail channel of a category). Using it needs no
 * sign-in and is idempotent; it can only ever switch e-mail off, never read or change anything else. Tokens expire
 * after {@link #VALIDITY}.
 */
@Service
public class UnsubscribeService {
    public static final Duration VALIDITY = Duration.ofDays(400);
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final NotificationPreferenceService preferences;
    private final ObjectProvider<SavedSearchAlertsPort> savedSearches;
    private final Clock clock;

    public UnsubscribeService(JdbcTemplate jdbc, NotificationPreferenceService preferences,
                              ObjectProvider<SavedSearchAlertsPort> savedSearches, Clock clock) {
        this.jdbc = jdbc;
        this.preferences = preferences;
        this.savedSearches = savedSearches;
        this.clock = clock;
    }

    /** Issues a token for a saved search's alerts (caller transaction). */
    public String issueForSavedSearch(UUID userId, UUID savedSearchId) {
        return issue(userId, "SAVED_SEARCH", savedSearchId, null);
    }

    /** Issues a token that turns e-mail off for a category (caller transaction). */
    public String issueForCategory(UUID userId, NotificationCategory category) {
        return issue(userId, "CATEGORY", null, category.name());
    }

    @Transactional(readOnly = true)
    public Optional<Target> describe(String token) {
        return find(token).map(row -> new Target(row.scope, row.category, row.savedSearchId == null ? null
                : savedSearches.getObject().name(row.userId, row.savedSearchId).orElse(null), row.usedAt != null));
    }

    /** Applies the token; empty when it is unknown or expired. Applying it again changes nothing. */
    @Transactional
    public Optional<Target> apply(String token) {
        Optional<Row> found = find(token);
        if (found.isEmpty()) return Optional.empty();
        Row row = found.get();
        String name = null;
        if ("SAVED_SEARCH".equals(row.scope)) {
            SavedSearchAlertsPort port = savedSearches.getObject();
            name = port.name(row.userId, row.savedSearchId).orElse(null);
            port.stopAlerts(row.userId, row.savedSearchId);
        } else {
            preferences.disableEmail(row.userId, NotificationCategory.valueOf(row.category));
        }
        jdbc.update("UPDATE notification_unsubscribe_tokens SET used_at = coalesce(used_at, ?) WHERE token_hash = ?",
                Timestamp.from(clock.instant()), hash(token));
        return Optional.of(new Target(row.scope, row.category, name, true));
    }

    private String issue(UUID userId, String scope, @Nullable UUID savedSearchId, @Nullable String category) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO notification_unsubscribe_tokens (token_hash, user_id, scope, saved_search_id, category, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", hash(token), userId, scope, savedSearchId, category,
                Timestamp.from(now), Timestamp.from(now.plus(VALIDITY)));
        return token;
    }

    private Optional<Row> find(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) return Optional.empty();
        return jdbc.query("""
                SELECT user_id, scope, saved_search_id, category, used_at FROM notification_unsubscribe_tokens
                 WHERE token_hash = ? AND expires_at > ?""",
                (rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4),
                        rs.getTimestamp(5)), hash(token), Timestamp.from(clock.instant())).stream().findFirst();
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** What a link unsubscribes from: {@code scope} SAVED_SEARCH (with the search name) or CATEGORY. */
    public record Target(String scope, String category, String savedSearchName, boolean applied) {}

    private record Row(UUID userId, String scope, UUID savedSearchId, String category, Timestamp usedAt) {}
}

package com.company.bds.search.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code search_index_state}: the concrete indices behind an alias and their role (V035). */
@Repository
public class SearchIndexStateRepository {
    public enum Role { ACTIVE, BUILDING, PREVIOUS, RETIRED }

    public record IndexState(String indexName, String alias, Role role, int mappingVersion, UUID backfillCursor,
                             long backfilledRows, Instant backfillCompletedAt, Instant activatedAt, Instant retiredAt,
                             Instant createdAt) {}

    private static final RowMapper<IndexState> ROW = (rs, n) -> new IndexState(rs.getString("index_name"),
            rs.getString("alias_name"), Role.valueOf(rs.getString("role")), rs.getInt("mapping_version"),
            rs.getObject("backfill_cursor", UUID.class), rs.getLong("backfilled_rows"),
            toInstant(rs.getTimestamp("backfill_completed_at")), toInstant(rs.getTimestamp("activated_at")),
            toInstant(rs.getTimestamp("retired_at")), toInstant(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbc;

    public SearchIndexStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<IndexState> all(String alias) {
        return jdbc.query("SELECT * FROM search_index_state WHERE alias_name = ? ORDER BY created_at DESC, index_name DESC", ROW, alias);
    }

    /** Indices the search-index job writes: ACTIVE, BUILDING (dual-write) and PREVIOUS (rollback target). */
    public List<IndexState> writeTargets(String alias) {
        return jdbc.query("SELECT * FROM search_index_state WHERE alias_name = ? AND role IN ('ACTIVE','BUILDING','PREVIOUS')", ROW, alias);
    }

    public Optional<IndexState> withRole(String alias, Role role) {
        return jdbc.query("SELECT * FROM search_index_state WHERE alias_name = ? AND role = ? ORDER BY created_at DESC LIMIT 1",
                ROW, alias, role.name()).stream().findFirst();
    }

    public Optional<IndexState> find(String indexName) {
        return jdbc.query("SELECT * FROM search_index_state WHERE index_name = ?", ROW, indexName).stream().findFirst();
    }

    public void insert(String indexName, String alias, Role role, int mappingVersion) {
        jdbc.update("""
                INSERT INTO search_index_state(index_name, alias_name, role, mapping_version, activated_at)
                VALUES (?, ?, ?, ?, CASE WHEN ? = 'ACTIVE' THEN now() END)
                """, indexName, alias, role.name(), mappingVersion, role.name());
    }

    public void setRole(String indexName, Role role) {
        jdbc.update("""
                UPDATE search_index_state SET role = ?, updated_at = now(),
                       activated_at = CASE WHEN ? = 'ACTIVE' THEN now() ELSE activated_at END,
                       retired_at = CASE WHEN ? IN ('PREVIOUS','RETIRED') THEN now() ELSE retired_at END
                WHERE index_name = ?""", role.name(), role.name(), role.name(), indexName);
    }

    public void recordBackfill(String indexName, UUID cursor, long rows, boolean completed) {
        jdbc.update("""
                UPDATE search_index_state SET backfill_cursor = ?, backfilled_rows = backfilled_rows + ?, updated_at = now(),
                       backfill_completed_at = CASE WHEN ? THEN now() ELSE backfill_completed_at END
                WHERE index_name = ?""", cursor, rows, completed, indexName);
    }

    public void delete(String indexName) {
        jdbc.update("DELETE FROM search_index_state WHERE index_name = ?", indexName);
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}

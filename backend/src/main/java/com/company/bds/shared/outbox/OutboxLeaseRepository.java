package com.company.bds.shared.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class OutboxLeaseRepository {
    private final JdbcTemplate jdbc;

    public OutboxLeaseRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public List<ClaimedEvent> claim(String workerId, int batchSize) {
        UUID lease = UUID.randomUUID();
        return jdbc.query("""
                UPDATE outbox_events SET locked_at=CURRENT_TIMESTAMP,locked_by=?,lease_token=?
                WHERE id IN (
                    SELECT id FROM outbox_events
                    WHERE processed_at IS NULL AND dead_lettered_at IS NULL AND available_at<=CURRENT_TIMESTAMP
                      AND (locked_at IS NULL OR locked_at<CURRENT_TIMESTAMP-INTERVAL '5 minutes')
                    ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT ?
                )
                RETURNING id,event_type,payload::text,lease_token
                """, (rs, row) -> new ClaimedEvent(rs.getObject("id", UUID.class), rs.getString("event_type"),
                rs.getString("payload"), rs.getObject("lease_token", UUID.class)), workerId, lease, batchSize);
    }

    @Transactional
    public boolean complete(UUID id, UUID lease) {
        return jdbc.update("""
                UPDATE outbox_events SET processed_at=CURRENT_TIMESTAMP,locked_at=NULL,locked_by=NULL,lease_token=NULL,last_error=NULL
                WHERE id=? AND lease_token=? AND processed_at IS NULL
                """, id, lease) == 1;
    }

    @Transactional
    public void fail(UUID id, UUID lease, String error) {
        jdbc.update("""
                UPDATE outbox_events SET retry_count=retry_count+1,
                    available_at=CURRENT_TIMESTAMP + LEAST(INTERVAL '1 hour', INTERVAL '1 minute' * POWER(2, LEAST(retry_count, 6))),
                    dead_lettered_at=CASE WHEN retry_count+1>=10 THEN CURRENT_TIMESTAMP ELSE dead_lettered_at END,
                    locked_at=NULL,locked_by=NULL,lease_token=NULL,last_error=?
                WHERE id=? AND lease_token=? AND processed_at IS NULL
                """, error.substring(0, Math.min(1000, error.length())), id, lease);
    }

    public record ClaimedEvent(UUID id, String eventType, String payload, UUID leaseToken) {}
}

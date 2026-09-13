package com.company.bds.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true")
public class OutboxEventWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OutboxEventWriter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void append(String aggregateType, UUID aggregateId, String eventType, Map<String, ?> payload) {
        try {
            jdbc.update("""
                    INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,payload)
                    VALUES (?,?,?,?,CAST(? AS jsonb))
                    """, UUID.randomUUID(), aggregateType, aggregateId, eventType, mapper.writeValueAsString(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể ghi sự kiện outbox", ex);
        }
    }
}


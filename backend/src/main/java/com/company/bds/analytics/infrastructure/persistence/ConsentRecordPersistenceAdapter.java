package com.company.bds.analytics.infrastructure.persistence;

import com.company.bds.analytics.application.port.out.ConsentRecordRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;

@Component
class ConsentRecordPersistenceAdapter implements ConsentRecordRepository {
    private final JdbcTemplate jdbc;

    ConsentRecordPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ConsentRecord record) {
        jdbc.update("""
                INSERT INTO analytics_consent_records (id, consent_id, purpose, choice, policy_version, source, user_id, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, ps -> {
            ps.setObject(1, record.id());
            ps.setString(2, record.consentId());
            ps.setString(3, record.purpose());
            ps.setString(4, record.choice());
            ps.setString(5, record.policyVersion());
            ps.setString(6, record.source());
            ps.setObject(7, record.userId(), Types.OTHER);
            ps.setTimestamp(8, Timestamp.from(record.recordedAt()));
        });
    }
}

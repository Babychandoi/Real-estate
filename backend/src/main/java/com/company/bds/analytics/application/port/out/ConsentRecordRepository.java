package com.company.bds.analytics.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Append-only store of consent decisions ({@code analytics_consent_records}). */
public interface ConsentRecordRepository {

    record ConsentRecord(UUID id, String consentId, String purpose, String choice, String policyVersion, String source,
                         UUID userId, Instant recordedAt) {}

    void insert(ConsentRecord record);
}

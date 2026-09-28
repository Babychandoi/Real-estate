-- S4-ADMIN: violation report queue (claims, SLA, event history) and room for the encrypted reporter phone.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- The encrypted value ("v1:<mask>:<nonce>:<ciphertext>") does not fit VARCHAR(50). Existing plaintext values are
-- encrypted in place by the application at startup (ReporterPhoneEncryptionMigrator): the key lives in the secret
-- store, never in SQL. Until then the API only ever shows a mask.
ALTER TABLE listing_reports ALTER COLUMN reporter_phone TYPE TEXT;

ALTER TABLE listing_reports
    ADD COLUMN IF NOT EXISTS claimed_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS claimed_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resolved_by   UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_at    TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE report_events (
    id         UUID PRIMARY KEY,
    report_id  UUID NOT NULL REFERENCES listing_reports (id) ON DELETE CASCADE,
    type       VARCHAR(30) NOT NULL,
    actor_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    note       VARCHAR(1000),
    data       JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_report_events_type CHECK (type IN
        ('SUBMITTED', 'CLAIMED', 'RELEASED', 'EMERGENCY_HIDDEN', 'RESOLVED', 'DISMISSED', 'APPEALED',
         'OWNER_RESPONSE', 'AUTO_PAUSED', 'NOTE'))
);
CREATE INDEX idx_report_events_report ON report_events (report_id, created_at, id);

-- Open cases, oldest first (the SLA order within a severity).
CREATE INDEX IF NOT EXISTS idx_listing_reports_open ON listing_reports (created_at, id)
    WHERE status IN ('PENDING', 'WAITING_REPLY', 'APPEALED');

-- Every new case starts its history, whichever write path creates it (public form, import, tests).
CREATE OR REPLACE FUNCTION bds_report_submitted_event() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
    INSERT INTO report_events (id, report_id, type, data, created_at)
    VALUES (uuid_generate_v4(), NEW.id, 'SUBMITTED',
            jsonb_build_object('severity', NEW.severity, 'category', NEW.category), NEW.created_at);
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_listing_reports_submitted AFTER INSERT ON listing_reports
    FOR EACH ROW EXECUTE FUNCTION bds_report_submitted_event();

-- History starts with the submission of every existing case (the only fact the old schema recorded with a time).
INSERT INTO report_events (id, report_id, type, created_at)
SELECT uuid_generate_v4(), r.id, 'SUBMITTED', r.created_at
FROM listing_reports r
WHERE NOT EXISTS (SELECT 1 FROM report_events e WHERE e.report_id = r.id AND e.type = 'SUBMITTED');

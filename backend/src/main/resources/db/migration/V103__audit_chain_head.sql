-- W6: the request audit trail (audit_events) is a hash chain, but every request read "the latest hash" and inserted
-- its event without a lock. Concurrent requests read the same predecessor, so the chain forked, and identical concurrent
-- requests produced the same event_hash: the second insert failed on its UNIQUE constraint and the record was lost
-- (audit_write_failed). Requests now store the event unlinked (a plain INSERT); one linker at a time (the head row lock
-- below) gives unchained events the next chain_seq, previous_hash and event_hash in arrival order (AuditTrail).
--
-- Lock profile (W6 review rounds 1-2): this migration only changes metadata. Flyway runs it in one transaction and
-- ADD COLUMN takes ACCESS EXCLUSIVE until the commit, so nothing slow may follow it here: the CHECK is added NOT VALID
-- (validated in V104 under SHARE UPDATE EXCLUSIVE) and the indexes are built in V106, after this lock is released.
-- Measured numbers: docs/operations/ALERT_RUNBOOK.md#bdsauditchainstalled.
SET LOCAL lock_timeout = '5s';

ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS chain_seq BIGINT;
-- Stored but not linked yet: no hash. Rows from before V103 keep their hash and a NULL chain_seq.
ALTER TABLE audit_events ALTER COLUMN event_hash DROP NOT NULL;
ALTER TABLE audit_events ADD CONSTRAINT chk_audit_events_linked_has_hash
    CHECK (chain_seq IS NULL OR (event_hash IS NOT NULL AND previous_hash IS NOT NULL)) NOT VALID;
-- When the row reached the database, by the database clock. NULL for every row from before V103 (the column is added
-- without a default, so existing rows are not rewritten and stay NULL); every row inserted afterwards gets now() — also
-- rows inserted by the previous image during a rolling deploy, which does not know the column. "stored_at IS NOT NULL
-- AND chain_seq IS NULL" is therefore exactly the set the linker must chain, independent of any application clock.
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS stored_at TIMESTAMPTZ;
ALTER TABLE audit_events ALTER COLUMN stored_at SET DEFAULT now();

CREATE TABLE IF NOT EXISTS audit_chain_head (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    last_seq BIGINT NOT NULL CHECK (last_seq >= 0),
    last_hash VARCHAR(64) NOT NULL,
    -- The predecessor of chain_seq 1: the latest pre-V103 event (by time), so the old history stays linked to the new.
    genesis_hash VARCHAR(64) NOT NULL,
    -- When the chain started (informational; the linker selects by stored_at, not by this instant).
    cutover_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO audit_chain_head(singleton_id, last_seq, last_hash, genesis_hash, cutover_at)
SELECT 1, 0, latest, latest, now()
FROM (SELECT COALESCE((SELECT TRIM(event_hash) FROM audit_events ORDER BY occurred_at DESC, id DESC LIMIT 1), 'GENESIS') AS latest) AS l
ON CONFLICT (singleton_id) DO NOTHING;

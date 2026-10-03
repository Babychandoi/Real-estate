-- W6: the request audit trail (audit_events) is a hash chain, but every request read "the latest hash" and inserted
-- its event without a lock. Concurrent requests read the same predecessor, so the chain forked, and identical concurrent
-- requests produced the same event_hash: the second insert failed on its UNIQUE constraint and the record was lost
-- (audit_write_failed). Requests now store the event unlinked (a plain INSERT); one linker at a time (the head row lock
-- below) gives unchained events the next chain_seq, previous_hash and event_hash in arrival order (AuditTrail).
--
-- Lock profile (W6 review): only metadata changes take ACCESS EXCLUSIVE here (ADD COLUMN without default, DROP NOT NULL,
-- CHECK ... NOT VALID). The CHECK is validated in V104 under SHARE UPDATE EXCLUSIVE (reads and writes continue). The
-- three indexes are plain CREATE INDEX (SHARE lock: reads continue, audit inserts wait for the build); measured build
-- time and the CONCURRENTLY alternative are in docs/operations/ALERT_RUNBOOK.md#bdsauditchainstalled.
SET LOCAL lock_timeout = '5s';

ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS chain_seq BIGINT;
-- Stored but not linked yet: no hash. Rows from before V103 keep their hash and a NULL chain_seq.
ALTER TABLE audit_events ALTER COLUMN event_hash DROP NOT NULL;
ALTER TABLE audit_events ADD CONSTRAINT chk_audit_events_linked_has_hash
    CHECK (chain_seq IS NULL OR (event_hash IS NOT NULL AND previous_hash IS NOT NULL)) NOT VALID;

-- On a large table, build these beforehand with CREATE [UNIQUE] INDEX CONCURRENTLY IF NOT EXISTS (same names and
-- definitions); the statements below then do nothing.
-- Two events can never claim the same position (a fork), whoever writes them.
CREATE UNIQUE INDEX IF NOT EXISTS uq_audit_events_chain_seq ON audit_events(chain_seq);
-- Linker input 1: events stored by AuditTrail.record (no hash yet).
CREATE INDEX IF NOT EXISTS idx_audit_events_unlinked ON audit_events(occurred_at, id) WHERE event_hash IS NULL;
-- Linker input 2: events stored after the cut-over by the previous image during a rolling deploy (hashed on their own,
-- no position). Pre-V103 rows are in this index too but lie before cutover_at, so the range scan never reads them.
CREATE INDEX IF NOT EXISTS idx_audit_events_unchained ON audit_events(occurred_at, id) WHERE chain_seq IS NULL;

CREATE TABLE IF NOT EXISTS audit_chain_head (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    last_seq BIGINT NOT NULL CHECK (last_seq >= 0),
    last_hash VARCHAR(64) NOT NULL,
    -- The predecessor of chain_seq 1: the latest pre-V103 event (by time), so the old history stays linked to the new.
    genesis_hash VARCHAR(64) NOT NULL,
    -- Events stored from this instant on must all be in the chain (also those written by the previous image).
    cutover_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO audit_chain_head(singleton_id, last_seq, last_hash, genesis_hash, cutover_at)
SELECT 1, 0, latest, latest, now()
FROM (SELECT COALESCE((SELECT TRIM(event_hash) FROM audit_events ORDER BY occurred_at DESC, id DESC LIMIT 1), 'GENESIS') AS latest) AS l
ON CONFLICT (singleton_id) DO NOTHING;

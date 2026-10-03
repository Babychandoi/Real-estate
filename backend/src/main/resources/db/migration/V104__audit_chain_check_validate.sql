-- W6 review: validate the V103 CHECK in its own migration. VALIDATE CONSTRAINT scans the table under SHARE UPDATE
-- EXCLUSIVE only, so reads and audit inserts continue while it runs (V103's ACCESS EXCLUSIVE lock was released at its commit).
SET LOCAL lock_timeout = '5s';
ALTER TABLE audit_events VALIDATE CONSTRAINT chk_audit_events_linked_has_hash;

-- Incremental verification (W6 review round 2): the last chain position an operator's verification run proved intact,
-- so a run continues from there instead of rescanning the whole chain on a request thread. Reset to 0 for a full re-check.
CREATE TABLE IF NOT EXISTS audit_chain_checkpoint (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    verified_seq BIGINT NOT NULL DEFAULT 0 CHECK (verified_seq >= 0),
    verified_hash VARCHAR(64),
    verified_at TIMESTAMPTZ,
    -- First position found broken by the last run (NULL = none); the checkpoint never moves past it.
    broken_seq BIGINT
);
INSERT INTO audit_chain_checkpoint(singleton_id, verified_seq) VALUES (1, 0) ON CONFLICT (singleton_id) DO NOTHING;

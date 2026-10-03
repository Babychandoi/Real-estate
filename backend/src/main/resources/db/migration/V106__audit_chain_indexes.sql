-- W6 review round 2: the audit chain indexes in their own migration. V103 (ADD COLUMN, ACCESS EXCLUSIVE) has committed
-- before this runs, so these builds hold only a SHARE lock: reads of audit_events continue, inserts wait for the build.
-- Measured per million rows in docs/operations/ALERT_RUNBOOK.md#bdsauditchainstalled. On a much larger table, build them
-- beforehand with CREATE [UNIQUE] INDEX CONCURRENTLY IF NOT EXISTS (same names and definitions): these statements then
-- do nothing.
SET LOCAL lock_timeout = '5s';

-- Two events can never claim the same position (a fork), whoever writes them.
CREATE UNIQUE INDEX IF NOT EXISTS uq_audit_events_chain_seq ON audit_events(chain_seq);
-- Linker input: rows stored since V103 (stored_at set by the database) that have no position yet. Tiny: rows leave it
-- within a second. Pre-V103 rows (stored_at NULL) are never in it.
CREATE INDEX IF NOT EXISTS idx_audit_events_unchained ON audit_events(stored_at, id) WHERE chain_seq IS NULL AND stored_at IS NOT NULL;

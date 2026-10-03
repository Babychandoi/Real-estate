-- W6 review: validate the V103 CHECK in its own migration. VALIDATE CONSTRAINT scans the table under SHARE UPDATE
-- EXCLUSIVE only, so reads and audit inserts continue while it runs (V103's ACCESS EXCLUSIVE lock was released at its commit).
SET LOCAL lock_timeout = '5s';
ALTER TABLE audit_events VALIDATE CONSTRAINT chk_audit_events_linked_has_hash;

-- S4-ADMIN Review 2 fixes.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '600s';

-- 1. Validate the property-asset FK added NOT VALID in V060 (SHARE UPDATE EXCLUSIVE: listings stay readable/writable).
ALTER TABLE listings VALIDATE CONSTRAINT fk_listings_property_asset;

-- 2. Severity changes of a report case are their own event type.
ALTER TABLE report_events DROP CONSTRAINT IF EXISTS chk_report_events_type;
ALTER TABLE report_events ADD CONSTRAINT chk_report_events_type CHECK (type IN
    ('SUBMITTED', 'CLAIMED', 'RELEASED', 'EMERGENCY_HIDDEN', 'RESOLVED', 'DISMISSED', 'APPEALED',
     'OWNER_RESPONSE', 'AUTO_PAUSED', 'ESCALATED', 'NOTE'));
UPDATE report_events SET type = 'ESCALATED' WHERE type = 'NOTE' AND data ->> 'severityTo' IS NOT NULL;

-- 3. At most one open order per user and plan (V059 skipped the index when duplicates existed).
--    Safe cleanup: an older duplicate still in CREATED (the buyer never reported a transfer) is cancelled with a history
--    entry, keeping the newest open order. Duplicates already TRANSFER_REPORTED / EXCEPTION may involve money and are
--    left for an administrator (runbook in docs/audit-2026-09-27/streams/s4-admin.md, "Review 2 fixes"); the index is
--    then created by re-running the block below by hand, and the service keeps serialising creation meanwhile.
WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY user_id, plan_code
                                  ORDER BY (status <> 'CREATED') DESC, created_at DESC, id DESC) AS rn
    FROM package_orders WHERE status IN ('CREATED', 'TRANSFER_REPORTED', 'EXCEPTION')
), cancelled AS (
    UPDATE package_orders o SET status = 'CANCELLED', reviewed_at = now(),
        review_note = 'Tự động hủy: yêu cầu trùng với một yêu cầu đang mở khác của cùng gói', updated_at = now(),
        version = o.version + 1
    FROM ranked r WHERE r.id = o.id AND r.rn > 1 AND o.status = 'CREATED'
    RETURNING o.id
)
INSERT INTO package_order_events (id, order_id, type, from_status, to_status, note, created_at)
SELECT uuid_generate_v4(), id, 'CANCELLED', 'CREATED', 'CANCELLED', 'Tự động hủy yêu cầu trùng (V061)', now() FROM cancelled;

DO $$
BEGIN
    IF to_regclass('uq_package_orders_open_per_plan') IS NOT NULL THEN
        RETURN;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM package_orders WHERE status IN ('CREATED', 'TRANSFER_REPORTED', 'EXCEPTION')
        GROUP BY user_id, plan_code HAVING count(*) > 1
    ) THEN
        CREATE UNIQUE INDEX uq_package_orders_open_per_plan ON package_orders (user_id, plan_code)
            WHERE status IN ('CREATED', 'TRANSFER_REPORTED', 'EXCEPTION');
    ELSE
        RAISE WARNING 'package_orders still has several reported/exception orders for one user and plan; resolve them, then create uq_package_orders_open_per_plan (runbook)';
    END IF;
END $$;

-- 4. Bind each staff KYC document grant to its own access-log row (reason). Earlier rows have no hash and simply stop
--    authorising reads (grants live minutes, so nothing valid is lost).
ALTER TABLE kyc_access_log ADD COLUMN IF NOT EXISTS grant_token_hash VARCHAR(64);

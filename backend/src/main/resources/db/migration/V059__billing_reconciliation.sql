-- S4-ADMIN (F18.2–F18.4): durable reconciliation states, received-payment details, exceptions and order history.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- The status CHECK was declared inline in V012 (system generated name): drop whichever check constrains status.
DO $$
DECLARE
    c record;
BEGIN
    FOR c IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'package_orders'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE package_orders DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE package_orders
    ADD CONSTRAINT chk_package_orders_status CHECK (status IN
        ('CREATED', 'TRANSFER_REPORTED', 'EXCEPTION', 'APPROVED', 'REJECTED', 'REFUNDED', 'CANCELLED'));

ALTER TABLE package_orders
    ADD COLUMN IF NOT EXISTS received_amount_vnd BIGINT,
    ADD COLUMN IF NOT EXISTS received_reference  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS exception_reason    VARCHAR(40),
    ADD COLUMN IF NOT EXISTS exception_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resolution          VARCHAR(30),
    ADD COLUMN IF NOT EXISTS updated_at          TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE package_orders
    ADD CONSTRAINT chk_package_orders_received_amount CHECK (received_amount_vnd IS NULL OR received_amount_vnd >= 0),
    ADD CONSTRAINT chk_package_orders_resolution CHECK (resolution IS NULL OR resolution IN
        ('MATCHED', 'APPROVED_WITH_NOTE', 'REJECTED', 'REFUNDED_OFFLINE'));

CREATE TABLE package_order_events (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL REFERENCES package_orders (id) ON DELETE CASCADE,
    type        VARCHAR(30) NOT NULL,
    from_status VARCHAR(30),
    to_status   VARCHAR(30),
    actor_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    note        VARCHAR(500),
    data        JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_package_order_events_order ON package_order_events (order_id, created_at, id);

-- Reconciliation queue by status, oldest report first.
CREATE INDEX IF NOT EXISTS idx_package_orders_status_reported ON package_orders (status, user_reported_at, id);

-- History for existing orders from the timestamps the old schema kept.
INSERT INTO package_order_events (id, order_id, type, to_status, actor_id, created_at)
SELECT uuid_generate_v4(), o.id, 'CREATED', 'CREATED', o.user_id, o.created_at FROM package_orders o;
INSERT INTO package_order_events (id, order_id, type, from_status, to_status, actor_id, created_at)
SELECT uuid_generate_v4(), o.id, 'TRANSFER_REPORTED', 'CREATED', 'TRANSFER_REPORTED', o.user_id, o.user_reported_at
FROM package_orders o WHERE o.user_reported_at IS NOT NULL;
INSERT INTO package_order_events (id, order_id, type, to_status, actor_id, note, created_at)
SELECT uuid_generate_v4(), o.id, o.status, o.status, o.reviewed_by, o.review_note, o.reviewed_at
FROM package_orders o WHERE o.status IN ('APPROVED', 'REJECTED', 'CANCELLED') AND o.reviewed_at IS NOT NULL;

-- At most one open order per user and plan. Created only when existing rows comply (they cannot be inspected before the
-- deploy); the service serialises order creation per user+plan with an advisory lock either way.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM package_orders WHERE status IN ('CREATED', 'TRANSFER_REPORTED', 'EXCEPTION')
        GROUP BY user_id, plan_code HAVING count(*) > 1
    ) THEN
        CREATE UNIQUE INDEX uq_package_orders_open_per_plan ON package_orders (user_id, plan_code)
            WHERE status IN ('CREATED', 'TRANSFER_REPORTED', 'EXCEPTION');
    ELSE
        RAISE WARNING 'package_orders has several open orders for one user and plan; uq_package_orders_open_per_plan not created';
    END IF;
END $$;

-- W6 review: an Idempotency-Key replay of an admin billing review answers what the keyed request produced (the order as
-- it was then), not the order's current state. The response body is kept with the key until the key expires.
SET LOCAL lock_timeout = '5s';
ALTER TABLE api_idempotency_keys ADD COLUMN IF NOT EXISTS response_snapshot TEXT;

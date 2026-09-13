ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS lease_token UUID;
CREATE INDEX IF NOT EXISTS idx_outbox_claim
    ON outbox_events(available_at, created_at) WHERE processed_at IS NULL;


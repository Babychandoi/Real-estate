ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS dead_lettered_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_outbox_dead_letter
    ON outbox_events(dead_lettered_at, created_at)
    WHERE dead_lettered_at IS NOT NULL;

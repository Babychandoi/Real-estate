-- S3a-SUPPLY: audit of self-service role upgrades (USER -> OWNER, P-09).
CREATE TABLE IF NOT EXISTS user_role_changes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    from_role VARCHAR(20) NOT NULL,
    to_role VARCHAR(20) NOT NULL,
    reason VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_user_role_changes_user ON user_role_changes (user_id, created_at DESC);

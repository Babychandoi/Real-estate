ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;
UPDATE users SET email_verified_at = COALESCE(email_verified_at, created_at) WHERE email IS NOT NULL;

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens(user_id, created_at DESC);

-- Public pins are approximate and intentionally do not reveal an exact property address.
UPDATE listing_revisions SET public_latitude=21.0118, public_longitude=105.7784 WHERE id='72000000-0000-0000-0000-000000000001';
UPDATE listing_revisions SET public_latitude=21.0367, public_longitude=105.7921 WHERE id='72000000-0000-0000-0000-000000000002';
UPDATE listing_revisions SET public_latitude=21.0152, public_longitude=105.8248 WHERE id='72000000-0000-0000-0000-000000000003';
UPDATE listing_revisions SET public_latitude=20.9976, public_longitude=105.8073 WHERE id='72000000-0000-0000-0000-000000000004';
UPDATE listing_revisions SET public_latitude=21.0289, public_longitude=105.7516 WHERE id='72000000-0000-0000-0000-000000000005';
UPDATE listing_revisions SET public_latitude=20.9808, public_longitude=105.7907 WHERE id='72000000-0000-0000-0000-000000000006';

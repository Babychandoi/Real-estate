-- S5-SEC phase B (F20.2, UI-17): what a person needs to recognise and revoke their own sessions, and the idle timeout of
-- staff sessions (ADR 0001). Only coarse metadata is kept: a browser/OS label (never the raw User-Agent) and a network
-- prefix (IPv4 /24, IPv6 /48), never a full client address.
ALTER TABLE auth_sessions
    ADD COLUMN last_seen_at         TIMESTAMPTZ,
    ADD COLUMN idle_timeout_seconds INTEGER,
    ADD COLUMN device_label         VARCHAR(80),
    ADD COLUMN ip_hint              VARCHAR(64),
    ADD COLUMN mfa_verified_at      TIMESTAMPTZ,
    ADD COLUMN revoked_reason       VARCHAR(30),
    ADD CONSTRAINT chk_auth_sessions_idle CHECK (idle_timeout_seconds IS NULL OR idle_timeout_seconds > 0);

UPDATE auth_sessions SET last_seen_at = created_at WHERE last_seen_at IS NULL;
ALTER TABLE auth_sessions ALTER COLUMN last_seen_at SET DEFAULT CURRENT_TIMESTAMP, ALTER COLUMN last_seen_at SET NOT NULL;

-- Session list of one account and "revoke every session of this user" (password change, role change, lock, MFA reset).
CREATE INDEX idx_auth_sessions_user_open ON auth_sessions (user_id, created_at DESC) WHERE revoked_at IS NULL;

ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;

-- S5-SEC phase B (F20.3, UI-17): TOTP second factor for staff, single-use recovery codes, short-lived login challenges and
-- an account security event log. Secrets are sealed with AES-GCM by the application (PiiProtectionService.seal, purpose
-- bound); codes and challenge tokens are stored as SHA-256 only.
CREATE TABLE user_mfa (
    user_id          UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    secret_sealed    VARCHAR(255) NOT NULL,
    confirmed_at     TIMESTAMPTZ NOT NULL,
    -- Highest accepted TOTP time step: a code is never accepted twice (RFC 6238 §5.2).
    last_used_step   BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_mfa_recovery_codes (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash   CHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at     TIMESTAMPTZ
);
CREATE INDEX idx_user_mfa_recovery_codes_user ON user_mfa_recovery_codes (user_id) WHERE used_at IS NULL;

-- Issued by the staff login after the password check; exchanged for a session by a TOTP/recovery code (VERIFY) or by
-- confirming a new authenticator (ENROLL). Never a bearer token.
CREATE TABLE mfa_challenges (
    id                     UUID PRIMARY KEY,
    user_id                UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash             CHAR(64) NOT NULL UNIQUE,
    purpose                VARCHAR(10) NOT NULL,
    pending_secret_sealed  VARCHAR(255),
    failed_attempts        INTEGER NOT NULL DEFAULT 0,
    expires_at             TIMESTAMPTZ NOT NULL,
    consumed_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mfa_challenges_purpose CHECK (purpose IN ('VERIFY', 'ENROLL'))
);
CREATE INDEX idx_mfa_challenges_user ON mfa_challenges (user_id, created_at DESC);
CREATE INDEX idx_mfa_challenges_expiry ON mfa_challenges (expires_at);

CREATE TABLE auth_security_events (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_type    VARCHAR(40) NOT NULL,
    ip_hint       VARCHAR(64),
    device_label  VARCHAR(80),
    actor_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_auth_security_events_user ON auth_security_events (user_id, created_at DESC, id DESC);

-- Admin actions on another account's second factor and sessions (reasoned, like role changes).
ALTER TABLE user_admin_actions DROP CONSTRAINT chk_user_admin_actions_action;
ALTER TABLE user_admin_actions ADD CONSTRAINT chk_user_admin_actions_action
    CHECK (action IN ('ROLE_CHANGE', 'LOCK', 'UNLOCK', 'MFA_RESET', 'SESSIONS_REVOKE'));

-- Staff sessions opened before this release were created without a second factor: end them, so every ADMIN/MODERATOR
-- signs in again through the MFA step (the first sign-in enrols an authenticator).
UPDATE auth_sessions s SET revoked_at = CURRENT_TIMESTAMP, revoked_reason = 'MFA_ROLLOUT'
WHERE s.revoked_at IS NULL
  AND EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = s.user_id AND r.role IN ('ADMIN', 'MODERATOR'));

-- S4-ADMIN: reasoned admin actions on listings and users, KYC document access log.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

CREATE TABLE listing_status_history (
    id          UUID PRIMARY KEY,
    listing_id  UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    action      VARCHAR(30) NOT NULL,
    reason      VARCHAR(1000) NOT NULL,
    actor_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_listing_status_history_action CHECK (action IN
        ('LOCK', 'UNLOCK', 'HIDE', 'UNHIDE', 'EMERGENCY_HIDE', 'REPORT_LOCK', 'REPORT_RESUME', 'AUTO_PAUSE')),
    CONSTRAINT chk_listing_status_history_reason CHECK (btrim(reason) <> '')
);
CREATE INDEX idx_listing_status_history_listing ON listing_status_history (listing_id, created_at DESC, id DESC);

CREATE TABLE user_admin_actions (
    id             UUID PRIMARY KEY,
    target_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    actor_id       UUID REFERENCES users (id) ON DELETE SET NULL,
    action         VARCHAR(20) NOT NULL,
    from_value     VARCHAR(40),
    to_value       VARCHAR(40),
    reason         VARCHAR(1000) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_user_admin_actions_action CHECK (action IN ('ROLE_CHANGE', 'LOCK', 'UNLOCK')),
    CONSTRAINT chk_user_admin_actions_reason CHECK (btrim(reason) <> '')
);
CREATE INDEX idx_user_admin_actions_target ON user_admin_actions (target_user_id, created_at DESC, id DESC);

-- Every time staff open someone's identity documents: who, whose, why, and until when the grant was valid.
CREATE TABLE kyc_access_log (
    id               UUID PRIMARY KEY,
    subject_user_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    actor_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason           VARCHAR(500) NOT NULL,
    grant_expires_at TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_kyc_access_log_reason CHECK (btrim(reason) <> '')
);
CREATE INDEX idx_kyc_access_log_actor ON kyc_access_log (actor_id, subject_user_id, grant_expires_at DESC);
CREATE INDEX idx_kyc_access_log_subject ON kyc_access_log (subject_user_id, created_at DESC, id DESC);

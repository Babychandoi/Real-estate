-- S6-ENGAGE: notification centre v2 (contract §11).
-- user_notifications gains a global delivery sequence (SSE frame id / Last-Event-ID replay cursor), a category for
-- preferences, an optional in-app link and a per-user dedupe key (one notification per business fact).
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE SEQUENCE IF NOT EXISTS user_notifications_seq;

ALTER TABLE user_notifications
    ADD COLUMN IF NOT EXISTS seq BIGINT,
    ADD COLUMN IF NOT EXISTS category VARCHAR(20),
    ADD COLUMN IF NOT EXISTS link VARCHAR(300),
    ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(160);

-- Existing rows keep their creation order.
WITH ordered AS (
    SELECT id, row_number() OVER (ORDER BY created_at, id) AS rn FROM user_notifications WHERE seq IS NULL
)
UPDATE user_notifications n SET seq = ordered.rn FROM ordered WHERE n.id = ordered.id;
SELECT setval('user_notifications_seq', GREATEST((SELECT coalesce(max(seq), 0) FROM user_notifications), 1),
              (SELECT count(*) > 0 FROM user_notifications));

UPDATE user_notifications SET category = CASE
        WHEN type LIKE 'SAVED\_SEARCH%' THEN 'ALERTS'
        WHEN type LIKE 'SAVED\_LISTING%' THEN 'SAVED_LISTINGS'
        WHEN type LIKE 'SHORTLIST%' THEN 'SHORTLIST'
        WHEN type LIKE 'LEAD%' OR type LIKE 'APPOINTMENT%' THEN 'LEADS'
        WHEN type LIKE 'LISTING%' OR type LIKE 'OWNERSHIP%' THEN 'LISTINGS'
        ELSE 'ACCOUNT' END
WHERE category IS NULL;

ALTER TABLE user_notifications
    ALTER COLUMN seq SET DEFAULT nextval('user_notifications_seq'),
    ALTER COLUMN seq SET NOT NULL,
    ALTER COLUMN category SET DEFAULT 'ACCOUNT',
    ALTER COLUMN category SET NOT NULL;
ALTER SEQUENCE user_notifications_seq OWNED BY user_notifications.seq;

ALTER TABLE user_notifications
    ADD CONSTRAINT chk_user_notifications_category
        CHECK (category IN ('ACCOUNT', 'LISTINGS', 'LEADS', 'ALERTS', 'SAVED_LISTINGS', 'SHORTLIST'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_notifications_seq ON user_notifications (seq);
CREATE INDEX IF NOT EXISTS idx_user_notifications_user_seq ON user_notifications (user_id, seq DESC);
CREATE INDEX IF NOT EXISTS idx_user_notifications_unread ON user_notifications (user_id, seq DESC) WHERE read_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_notifications_dedupe ON user_notifications (user_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

-- Per-category channel choices. A missing row means the category default (NotificationCategory in Java).
CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL
        CHECK (category IN ('ACCOUNT', 'LISTINGS', 'LEADS', 'ALERTS', 'SAVED_LISTINGS', 'SHORTLIST')),
    in_app BOOLEAN NOT NULL,
    email BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, category)
);

-- One-click unsubscribe links in alert e-mails (RFC 8058). Only the SHA-256 of the random token is stored.
CREATE TABLE IF NOT EXISTS notification_unsubscribe_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scope VARCHAR(20) NOT NULL CHECK (scope IN ('SAVED_SEARCH', 'CATEGORY')),
    saved_search_id UUID,
    category VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CHECK ((scope = 'SAVED_SEARCH' AND saved_search_id IS NOT NULL) OR (scope = 'CATEGORY' AND category IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_unsubscribe_tokens_expiry ON notification_unsubscribe_tokens (expires_at);

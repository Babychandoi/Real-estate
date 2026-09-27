-- Versioned product analytics events (contract §5), owned by stream S0-BE; consent/bot/retention/dashboards are S8.
-- Facts, not relations: no foreign keys, so deleting a listing or user never rewrites history (retention is S8's job).

-- Fail fast instead of waiting behind a concurrent lock; both settings end with this migration's transaction.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

CREATE TABLE analytics_events (
    event_id       UUID PRIMARY KEY,           -- client UUIDv4 for web events, UUIDv3("server:<name>:<key>") for server events
    name           VARCHAR(60)  NOT NULL,
    schema_version SMALLINT     NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    received_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    anonymous_id   VARCHAR(64),                -- NULL without analytics consent
    session_id     VARCHAR(64),                -- NULL without analytics consent
    user_id        UUID,                       -- from the bearer token only, never from the request body
    listing_id     UUID,
    is_internal    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_bot         BOOLEAN      NOT NULL DEFAULT FALSE,
    origin         VARCHAR(10)  NOT NULL,
    device         VARCHAR(20),
    area_code      VARCHAR(20),
    page_path      VARCHAR(200),               -- path only (query string and fragment are dropped at ingestion)
    properties     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    utm            JSONB,                      -- NULL without analytics consent
    CONSTRAINT chk_analytics_events_origin CHECK (origin IN ('web', 'server')),
    CONSTRAINT chk_analytics_events_version CHECK (schema_version > 0)
);

CREATE INDEX idx_analytics_events_name_time ON analytics_events (name, occurred_at);
CREATE INDEX idx_analytics_events_listing ON analytics_events (listing_id, name, occurred_at) WHERE listing_id IS NOT NULL;

-- S8-ANALYTICS: consent records (Decree 13/2023 proof of consent), bot/internal device flags, daily aggregates,
-- visitor days for cohorts, and the indexes the retention job and dashboard queries use.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- One row per consent decision. consent_id is a random id generated in the browser (not derived from anything personal);
-- user_id comes from the bearer token when the visitor is signed in. No IP address or user agent is kept.
CREATE TABLE analytics_consent_records (
    id             UUID PRIMARY KEY,
    consent_id     VARCHAR(64)  NOT NULL,
    purpose        VARCHAR(20)  NOT NULL,
    choice         VARCHAR(10)  NOT NULL,
    policy_version VARCHAR(20)  NOT NULL,
    source         VARCHAR(20)  NOT NULL,
    user_id        UUID,
    recorded_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_consent_purpose CHECK (purpose IN ('analytics')),
    CONSTRAINT chk_consent_choice CHECK (choice IN ('granted', 'withdrawn')),
    CONSTRAINT chk_consent_source CHECK (source IN ('banner', 'preferences'))
);
CREATE INDEX idx_consent_records_consent ON analytics_consent_records (consent_id, recorded_at DESC);
CREATE INDEX idx_consent_records_time ON analytics_consent_records (recorded_at);

-- Devices (analytics anonymous ids) classified as internal (a staff session was seen) or bot (behavioural rule).
CREATE TABLE analytics_client_flags (
    anonymous_id VARCHAR(64) NOT NULL,
    kind         VARCHAR(10) NOT NULL,
    reason       VARCHAR(40) NOT NULL,
    flagged_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (anonymous_id, kind),
    CONSTRAINT chk_client_flag_kind CHECK (kind IN ('INTERNAL', 'BOT'))
);
CREATE INDEX idx_client_flags_time ON analytics_client_flags (flagged_at);

-- Daily event counts (Vietnam calendar day), bots and internal traffic excluded. '' = dimension unknown.
CREATE TABLE analytics_daily_metrics (
    day         DATE         NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    device      VARCHAR(20)  NOT NULL,
    area_code   VARCHAR(20)  NOT NULL,
    source      VARCHAR(100) NOT NULL,
    events      BIGINT       NOT NULL,
    sessions    BIGINT       NOT NULL,
    computed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (day, name, device, area_code, source)
);

-- One row per device and active day (cohorts and return rate); pseudonymous, so it follows the identifier retention.
CREATE TABLE analytics_visitor_days (
    day          DATE         NOT NULL,
    anonymous_id VARCHAR(64)  NOT NULL,
    device       VARCHAR(20)  NOT NULL,
    source       VARCHAR(100) NOT NULL,
    PRIMARY KEY (day, anonymous_id)
);

-- Time-ordered appends: BRIN keeps range scans for retention/aggregation cheap at a fraction of a btree's size.
CREATE INDEX idx_analytics_events_occurred_brin ON analytics_events USING brin (occurred_at);
-- Re-marking a flagged device and the per-device rate rule.
CREATE INDEX idx_analytics_events_anonymous ON analytics_events (anonymous_id, occurred_at) WHERE anonymous_id IS NOT NULL;
-- Dashboard windows over leads and listings by creation time.
CREATE INDEX IF NOT EXISTS idx_leads_created ON leads (created_at);
CREATE INDEX IF NOT EXISTS idx_listings_created ON listings (created_at);

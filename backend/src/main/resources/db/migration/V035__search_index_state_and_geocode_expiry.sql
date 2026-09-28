-- Stream S2-SEARCH: Elasticsearch index generations (contract §9 rebuild) and geocode cache expiry (audit F10.3).

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- One row per concrete index behind an alias. The search-index job writes every ACTIVE, BUILDING and PREVIOUS index of
-- its alias (dual-write during a rebuild, and a lossless rollback target after the swap); RETIRED ones are not written.
CREATE TABLE IF NOT EXISTS search_index_state (
    index_name            VARCHAR(200) PRIMARY KEY,
    alias_name            VARCHAR(200) NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    mapping_version       INT          NOT NULL,
    backfill_cursor       UUID,
    backfilled_rows       BIGINT       NOT NULL DEFAULT 0,
    backfill_completed_at TIMESTAMPTZ,
    activated_at          TIMESTAMPTZ,
    retired_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_search_index_state_role CHECK (role IN ('ACTIVE', 'BUILDING', 'PREVIOUS', 'RETIRED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_search_index_state_active ON search_index_state (alias_name) WHERE role = 'ACTIVE';
CREATE UNIQUE INDEX IF NOT EXISTS uq_search_index_state_building ON search_index_state (alias_name) WHERE role = 'BUILDING';

-- Geocode cache: normalised key per provider/language, positive entries expire (14 days by default), empty answers
-- are cached briefly (negative cache, 1 hour). Existing rows keep working until 14 days after they were stored.
ALTER TABLE geocode_cache
    ADD COLUMN IF NOT EXISTS normalized_query VARCHAR(300),
    ADD COLUMN IF NOT EXISTS provider VARCHAR(100),
    ADD COLUMN IF NOT EXISTS negative BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

UPDATE geocode_cache SET expires_at = created_at + INTERVAL '14 days' WHERE expires_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_geocode_cache_expires ON geocode_cache (expires_at);

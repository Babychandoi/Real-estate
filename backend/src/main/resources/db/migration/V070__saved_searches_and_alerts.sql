-- S6-ENGAGE: saved searches and alerts (new listing, price drop, back on the market).
-- Filters are stored in the canonical form of contract §7 (SearchFilter.canonicalParams) plus the filterHash; a few
-- columns are denormalised so candidate searches for one changed listing are found with an index, then matched exactly
-- in Java with SearchFilter.matches (the same predicate the search API re-checks with).
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS saved_searches (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    params JSONB NOT NULL,
    filter_hash CHAR(32) NOT NULL,
    purpose VARCHAR(10) NOT NULL CHECK (purpose IN ('SALE', 'RENT')),
    types TEXT[] NOT NULL DEFAULT '{}',
    districts TEXT[] NOT NULL DEFAULT '{}',
    price_min BIGINT,
    price_max BIGINT,
    frequency VARCHAR(10) NOT NULL CHECK (frequency IN ('INSTANT', 'DAILY', 'WEEKLY', 'OFF')),
    alert_new BOOLEAN NOT NULL DEFAULT TRUE,
    alert_price_drop BOOLEAN NOT NULL DEFAULT TRUE,
    alert_back_on_market BOOLEAN NOT NULL DEFAULT TRUE,
    paused BOOLEAN NOT NULL DEFAULT FALSE,
    next_digest_at TIMESTAMPTZ,
    last_digest_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_saved_searches_user_hash UNIQUE (user_id, filter_hash)
);
CREATE INDEX IF NOT EXISTS idx_saved_searches_user ON saved_searches (user_id, created_at DESC);
-- Candidate lookup for a changed listing: active searches of the purpose, then array overlap / price range.
CREATE INDEX IF NOT EXISTS idx_saved_searches_active_purpose ON saved_searches (purpose, created_at)
    WHERE frequency <> 'OFF' AND NOT paused;
CREATE INDEX IF NOT EXISTS idx_saved_searches_districts ON saved_searches USING GIN (districts)
    WHERE frequency <> 'OFF' AND NOT paused;
CREATE INDEX IF NOT EXISTS idx_saved_searches_due ON saved_searches (next_digest_at)
    WHERE frequency <> 'OFF' AND NOT paused;

-- One row per (search, listing, kind, fact): the unique key is the alert dedupe. fact_key is the listing for NEW, the
-- new price for PRICE_DROP and the return timestamp for BACK_ON_MARKET, so a second drop to another price alerts again.
CREATE TABLE IF NOT EXISTS saved_search_matches (
    id BIGSERIAL PRIMARY KEY,
    saved_search_id UUID NOT NULL REFERENCES saved_searches(id) ON DELETE CASCADE,
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('NEW', 'PRICE_DROP', 'BACK_ON_MARKET')),
    fact_key VARCHAR(60) NOT NULL,
    price_vnd BIGINT,
    previous_price_vnd BIGINT,
    matched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at TIMESTAMPTZ,
    CONSTRAINT uq_saved_search_matches UNIQUE (saved_search_id, listing_id, kind, fact_key)
);
CREATE INDEX IF NOT EXISTS idx_saved_search_matches_pending ON saved_search_matches (saved_search_id, id)
    WHERE delivered_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_saved_search_matches_retention ON saved_search_matches (matched_at);

-- Last public state seen by the alert matcher, per listing. Compared with the read model on every change to classify it.
CREATE TABLE IF NOT EXISTS engage_listing_state (
    listing_id UUID PRIMARY KEY REFERENCES listings(id) ON DELETE CASCADE,
    visible BOOLEAN NOT NULL,
    purpose VARCHAR(10),
    price_vnd BIGINT,
    first_public_at TIMESTAMPTZ,
    hidden_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Everything public now is "already seen": deploying this release sends no alert for existing listings.
INSERT INTO engage_listing_state (listing_id, visible, purpose, price_vnd, first_public_at)
SELECT listing_id, TRUE, purpose, price_vnd, coalesce(published_at, now()) FROM listing_public_read
ON CONFLICT (listing_id) DO NOTHING;

-- A read-model row appearing, disappearing or changing price enqueues one coalesced job per listing. The job reads the
-- committed state itself, so the payload only names the listing.
CREATE OR REPLACE FUNCTION bds_engage_on_public_read()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    v_listing uuid;
BEGIN
    v_listing := CASE WHEN TG_OP = 'DELETE' THEN OLD.listing_id ELSE NEW.listing_id END;
    PERFORM bds_enqueue_job('engage-listing-change', v_listing::text, jsonb_build_object('listingId', v_listing));
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_engage_public_read_insert_delete ON listing_public_read;
CREATE TRIGGER trg_engage_public_read_insert_delete
    AFTER INSERT OR DELETE ON listing_public_read
    FOR EACH ROW EXECUTE FUNCTION bds_engage_on_public_read();

DROP TRIGGER IF EXISTS trg_engage_public_read_price ON listing_public_read;
CREATE TRIGGER trg_engage_public_read_price
    AFTER UPDATE OF price_vnd, purpose ON listing_public_read
    FOR EACH ROW
    WHEN (OLD.price_vnd IS DISTINCT FROM NEW.price_vnd OR OLD.purpose IS DISTINCT FROM NEW.purpose)
    EXECUTE FUNCTION bds_engage_on_public_read();

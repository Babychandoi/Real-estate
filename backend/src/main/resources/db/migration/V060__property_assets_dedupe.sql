-- S4-ADMIN (P-05, D-10): real property assets behind listings and duplicate detection.
-- Exact fingerprint → asset link; blocking keys (district + type + purpose + price bucket, area ±5%) → pg_trgm
-- similarity computed only over the rows of one block, never across all listings.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Lower case, accents removed, đ → d, punctuation to spaces, collapsed whitespace.
CREATE OR REPLACE FUNCTION bds_normalize_text(p_text text) RETURNS text
    LANGUAGE sql STABLE PARALLEL SAFE AS
$$
SELECT btrim(regexp_replace(regexp_replace(translate(lower(unaccent(COALESCE(p_text, ''))), 'đ', 'd'),
                                           '[^a-z0-9]+', ' ', 'g'), '\s+', ' ', 'g'))
$$;

CREATE TABLE property_assets (
    id                 UUID PRIMARY KEY,
    fingerprint        VARCHAR(64) NOT NULL,
    district_code      VARCHAR(50) NOT NULL,
    property_type      VARCHAR(30) NOT NULL,
    area_m2_rounded    INTEGER NOT NULL,
    normalized_address VARCHAR(400) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_property_assets_fingerprint UNIQUE (fingerprint)
);

ALTER TABLE listings
    ADD CONSTRAINT fk_listings_property_asset FOREIGN KEY (property_asset_id) REFERENCES property_assets (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_listings_property_asset ON listings (property_asset_id) WHERE property_asset_id IS NOT NULL;

-- The comparable attributes of a listing's current revision (submitted or public).
CREATE TABLE listing_fingerprints (
    listing_id    UUID PRIMARY KEY REFERENCES listings (id) ON DELETE CASCADE,
    revision_id   UUID NOT NULL REFERENCES listing_revisions (id) ON DELETE CASCADE,
    fingerprint   VARCHAR(64) NOT NULL,
    district_code VARCHAR(50) NOT NULL,
    property_type VARCHAR(30) NOT NULL,
    purpose       VARCHAR(20) NOT NULL,
    area_m2       NUMERIC(10, 2) NOT NULL,
    price_vnd     BIGINT NOT NULL,
    price_bucket  INTEGER NOT NULL,
    match_text    TEXT NOT NULL,
    computed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_listing_fingerprints_block
    ON listing_fingerprints (district_code, property_type, purpose, price_bucket, area_m2);
CREATE INDEX idx_listing_fingerprints_fingerprint ON listing_fingerprints (fingerprint);

CREATE TABLE listing_duplicate_candidates (
    id                   UUID PRIMARY KEY,
    listing_id           UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    candidate_listing_id UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    score                NUMERIC(4, 3) NOT NULL,
    reasons              JSONB NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    decided_by           UUID REFERENCES users (id) ON DELETE SET NULL,
    decided_at           TIMESTAMPTZ,
    note                 VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_listing_duplicate_candidates_status CHECK (status IN ('OPEN', 'DISMISSED', 'CONFIRMED')),
    CONSTRAINT chk_listing_duplicate_candidates_pair CHECK (listing_id <> candidate_listing_id),
    CONSTRAINT uq_listing_duplicate_candidates_pair UNIQUE (listing_id, candidate_listing_id)
);
CREATE INDEX idx_listing_duplicate_candidates_open ON listing_duplicate_candidates (listing_id) WHERE status = 'OPEN';
CREATE INDEX idx_listing_duplicate_candidates_candidate ON listing_duplicate_candidates (candidate_listing_id);

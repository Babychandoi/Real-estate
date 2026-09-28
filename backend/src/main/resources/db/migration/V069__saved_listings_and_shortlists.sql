-- S6-ENGAGE: account favourites ("tin đã lưu") and shared shortlists.
SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS saved_listings (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, listing_id)
);
-- Keyset paging of one user's list (newest first) and fan-out of listing changes to the users who saved it.
CREATE INDEX IF NOT EXISTS idx_saved_listings_user_created ON saved_listings (user_id, created_at DESC, listing_id DESC);
CREATE INDEX IF NOT EXISTS idx_saved_listings_listing ON saved_listings (listing_id);

CREATE TABLE IF NOT EXISTS shortlists (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    -- SHA-256 of the current share token; NULL = not shared. Rotating replaces it, revoking clears it.
    share_token_hash CHAR(64),
    share_role VARCHAR(10) CHECK (share_role IN ('VIEWER', 'EDITOR')),
    shared_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((share_token_hash IS NULL) = (share_role IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_shortlists_owner ON shortlists (owner_id, updated_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_shortlists_share_token ON shortlists (share_token_hash) WHERE share_token_hash IS NOT NULL;

CREATE TABLE IF NOT EXISTS shortlist_members (
    shortlist_id UUID NOT NULL REFERENCES shortlists(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL CHECK (role IN ('VIEWER', 'EDITOR')),
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (shortlist_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_shortlist_members_user ON shortlist_members (user_id);

CREATE TABLE IF NOT EXISTS shortlist_items (
    shortlist_id UUID NOT NULL REFERENCES shortlists(id) ON DELETE CASCADE,
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    added_by UUID REFERENCES users(id) ON DELETE SET NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (shortlist_id, listing_id)
);
CREATE INDEX IF NOT EXISTS idx_shortlist_items_order ON shortlist_items (shortlist_id, added_at DESC, listing_id DESC);

-- S3a-SUPPLY: listing freshness (P-14). Additive only.
-- Policy (docs/audit-2026-09-27/streams/s3a-supply.md): a published listing is valid 45 days after the owner last
-- confirmed it is still available; reminders 7 and 2 days before; an expired listing can be renewed within 30 days.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- A public "đã bán/không còn" report asks the owner to confirm within 48 hours; NULL = no open check.
ALTER TABLE listings
    ADD COLUMN IF NOT EXISTS sold_check_due_at TIMESTAMPTZ;

-- One row per reminder actually sent: (listing, expiry cycle, kind). The expiry cycle is the expires_at value the
-- reminder was computed for, so a re-confirmation starts a new cycle and old queued reminders become no-ops.
CREATE TABLE IF NOT EXISTS listing_expiry_reminders (
    listing_id UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    cycle_expires_at TIMESTAMPTZ NOT NULL,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('D7', 'D2')),
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (listing_id, cycle_expires_at, kind)
);

-- Expiry sweep and reminder scan: only ACTIVE listings with an expiry.
CREATE INDEX IF NOT EXISTS idx_listings_active_expires ON listings (expires_at) WHERE status = 'ACTIVE' AND expires_at IS NOT NULL;
-- Sold-check sweep.
CREATE INDEX IF NOT EXISTS idx_listings_sold_check ON listings (sold_check_due_at) WHERE sold_check_due_at IS NOT NULL;
-- My-listings without a status filter (S0-BE §2.7 follow-up): owner, newest first, stable.
CREATE INDEX IF NOT EXISTS idx_listings_owner_created ON listings (owner_id, created_at DESC, id DESC);

-- Existing ACTIVE listings get a first expiry 45 days after their last confirmation, never earlier than 14 days from
-- the deploy, so owners get both reminders before anything expires.
UPDATE listings
SET expires_at = GREATEST(availability_confirmed_at + INTERVAL '45 days', now() + INTERVAL '14 days')
WHERE status = 'ACTIVE' AND expires_at IS NULL AND availability_confirmed_at IS NOT NULL;

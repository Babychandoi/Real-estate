-- Supports owner-scoped lead inbox filtering and newest-first pagination.
CREATE INDEX IF NOT EXISTS idx_leads_listing_status_created
    ON leads(listing_id, status, created_at DESC);

-- Supports privileged inbox filtering and newest-first pagination.
CREATE INDEX IF NOT EXISTS idx_leads_status_created
    ON leads(status, created_at DESC);

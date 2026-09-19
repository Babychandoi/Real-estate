ALTER TABLE leads
    ADD COLUMN requester_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_leads_requester_created
    ON leads(requester_id, created_at DESC)
    WHERE requester_id IS NOT NULL;

-- S3b-LEADS: lead history, assignment, withdrawal, qualification note and scoped idempotency retention.
-- Additive only; the previous release keeps working against this schema.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS assignee_id UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS withdrawn_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS withdraw_reason VARCHAR(300),
    ADD COLUMN IF NOT EXISTS qualification_note VARCHAR(500);

-- Quota windows (per requester account and per phone blind index).
CREATE INDEX IF NOT EXISTS idx_leads_requester_window ON leads (requester_id, created_at) WHERE requester_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_leads_phone_window ON leads (phone_lookup_hash, created_at);
-- Leads handled by a team member.
CREATE INDEX IF NOT EXISTS idx_leads_assignee_created ON leads (assignee_id, created_at DESC, id DESC) WHERE assignee_id IS NOT NULL;
-- SLA: open NEW leads by age.
CREATE INDEX IF NOT EXISTS idx_leads_new_created ON leads (listing_id, created_at) WHERE status = 'NEW';

-- Append-only history of every lead change (who, when, what). Never updated.
CREATE TABLE lead_events (
    id          UUID PRIMARY KEY,
    lead_id     UUID NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
    type        VARCHAR(40) NOT NULL,
    actor_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    actor_side  VARCHAR(20) NOT NULL,
    from_status VARCHAR(30),
    to_status   VARCHAR(30),
    note        VARCHAR(500),
    data        JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- Internal events (qualification, assignment) are never shown to the requester.
    internal    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_lead_events_side CHECK (actor_side IN ('REQUESTER', 'OWNER_SIDE', 'STAFF', 'SYSTEM')),
    CONSTRAINT chk_lead_events_type CHECK (type IN ('CREATED', 'STATUS_CHANGED', 'ASSIGNED', 'QUALIFIED', 'WITHDRAWN',
        'APPOINTMENT_PROPOSED', 'APPOINTMENT_CONFIRMED', 'APPOINTMENT_RESCHEDULED', 'APPOINTMENT_CANCELLED',
        'APPOINTMENT_COMPLETED', 'APPOINTMENT_NO_SHOW'))
);
CREATE INDEX idx_lead_events_lead ON lead_events (lead_id, created_at DESC, id DESC);
CREATE INDEX idx_lead_events_created ON lead_events (created_at DESC, id DESC);

-- Legacy leads get their creation as the first history entry (the only fact known about them).
INSERT INTO lead_events (id, lead_id, type, actor_id, actor_side, to_status, created_at)
SELECT gen_random_uuid(), l.id, 'CREATED', l.requester_id, 'REQUESTER', 'NEW', l.created_at FROM leads l;

-- Idempotency keys are kept 24 h; NULL (legacy rows) means created_at + 24 h.
ALTER TABLE api_idempotency_keys ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

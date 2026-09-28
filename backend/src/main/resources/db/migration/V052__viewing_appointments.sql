-- S3b-LEADS: viewing appointments with proposed slots, two-party confirmation, outcome and reminder log.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TABLE viewing_appointments (
    id               UUID PRIMARY KEY,
    lead_id          UUID NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
    listing_id       UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    owner_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    requester_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    status           VARCHAR(20) NOT NULL,
    proposed_by_side VARCHAR(20) NOT NULL,
    proposed_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    starts_at        TIMESTAMPTZ,
    ends_at          TIMESTAMPTZ,
    confirmed_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    confirmed_at     TIMESTAMPTZ,
    cancelled_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    cancelled_at     TIMESTAMPTZ,
    cancel_reason    VARCHAR(300),
    outcome_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    outcome_at       TIMESTAMPTZ,
    no_show_party    VARCHAR(20),
    replaced_by      UUID REFERENCES viewing_appointments (id) ON DELETE SET NULL,
    note             VARCHAR(500),
    version          BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_appt_status CHECK (status IN ('PROPOSED', 'CONFIRMED', 'RESCHEDULED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    CONSTRAINT chk_appt_side CHECK (proposed_by_side IN ('REQUESTER', 'OWNER_SIDE')),
    CONSTRAINT chk_appt_no_show CHECK (no_show_party IS NULL OR no_show_party IN ('REQUESTER', 'OWNER_SIDE')),
    CONSTRAINT chk_appt_confirmed_time CHECK (status NOT IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW')
        OR (starts_at IS NOT NULL AND ends_at > starts_at))
);
-- At most one open appointment per lead.
CREATE UNIQUE INDEX uq_appt_open_per_lead ON viewing_appointments (lead_id) WHERE status IN ('PROPOSED', 'CONFIRMED');
CREATE INDEX idx_appt_lead ON viewing_appointments (lead_id, created_at DESC);
CREATE INDEX idx_appt_owner_start ON viewing_appointments (owner_id, starts_at) WHERE status = 'CONFIRMED';

CREATE TABLE appointment_slots (
    id             UUID PRIMARY KEY,
    appointment_id UUID NOT NULL REFERENCES viewing_appointments (id) ON DELETE CASCADE,
    starts_at      TIMESTAMPTZ NOT NULL,
    ends_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_slot_range CHECK (ends_at > starts_at)
);
CREATE INDEX idx_appt_slots ON appointment_slots (appointment_id, starts_at);

-- Exactly-once guard for reminders (the job queue delivers at least once).
CREATE TABLE appointment_reminders_sent (
    appointment_id UUID NOT NULL REFERENCES viewing_appointments (id) ON DELETE CASCADE,
    kind           VARCHAR(10) NOT NULL,
    sent_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (appointment_id, kind)
);

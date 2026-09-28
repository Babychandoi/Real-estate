-- S4-ADMIN: moderation v2 (claims, decisions with the real moderator, weekly random audit).
-- Additive only; the previous release keeps working against this schema.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- One active claim per listing. A claim expires after 30 minutes (enforced by the service through expires_at); an
-- expired row is simply taken over by the next claimant, so no sweeper is needed.
CREATE TABLE moderation_claims (
    listing_id   UUID PRIMARY KEY REFERENCES listings (id) ON DELETE CASCADE,
    revision_id  UUID NOT NULL REFERENCES listing_revisions (id) ON DELETE CASCADE,
    moderator_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    claimed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_moderation_claims_moderator ON moderation_claims (moderator_id, expires_at);

-- Every moderation decision with the moderator who took it (replaces the hard-coded placeholder id).
CREATE TABLE moderation_decisions (
    id            UUID PRIMARY KEY,
    listing_id    UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    revision_id   UUID NOT NULL REFERENCES listing_revisions (id) ON DELETE CASCADE,
    moderator_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    decision      VARCHAR(20) NOT NULL,
    reason_code   VARCHAR(40) NOT NULL,
    note          VARCHAR(1000),
    bulk_batch_id UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_moderation_decisions_decision
        CHECK (decision IN ('APPROVED', 'REJECTED', 'AUDIT_PASSED', 'AUDIT_FAILED'))
);
-- A revision is approved or rejected exactly once, even when two moderators race.
CREATE UNIQUE INDEX uq_moderation_decisions_revision ON moderation_decisions (revision_id)
    WHERE decision IN ('APPROVED', 'REJECTED');
CREATE INDEX idx_moderation_decisions_listing ON moderation_decisions (listing_id, created_at DESC, id DESC);
CREATE INDEX idx_moderation_decisions_moderator ON moderation_decisions (moderator_id, created_at DESC, id DESC);

-- Weekly random audit of already approved revisions (one sample set per ISO week, drawn once under a task lock).
CREATE TABLE moderation_audit_samples (
    id                   UUID PRIMARY KEY,
    week_start           DATE NOT NULL,
    listing_id           UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    revision_id          UUID NOT NULL REFERENCES listing_revisions (id) ON DELETE CASCADE,
    original_decision_id UUID REFERENCES moderation_decisions (id) ON DELETE SET NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reviewed_by          UUID REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at          TIMESTAMPTZ,
    note                 VARCHAR(1000),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_moderation_audit_samples_status CHECK (status IN ('OPEN', 'PASSED', 'FAILED')),
    CONSTRAINT uq_moderation_audit_samples_week_listing UNIQUE (week_start, listing_id)
);
CREATE INDEX idx_moderation_audit_samples_open ON moderation_audit_samples (week_start DESC, created_at, id) WHERE status = 'OPEN';

-- The pending queue reads submitted revisions oldest first; the partial index keeps that a range scan.
CREATE INDEX IF NOT EXISTS idx_listing_revisions_submitted_queue
    ON listing_revisions (submitted_at, listing_id) WHERE status = 'SUBMITTED';

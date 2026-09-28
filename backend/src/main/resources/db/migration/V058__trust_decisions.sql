-- S4-ADMIN: trust decisions (identity / ownership) with reason codes, deciders, validity and expiry notices.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

ALTER TABLE user_kyc_profiles
    ADD COLUMN IF NOT EXISTS decided_by           UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS revoked_at           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS decision_reason_code VARCHAR(40);

ALTER TABLE listing_verifications
    ADD COLUMN IF NOT EXISTS decision_reason_code VARCHAR(40);

CREATE TABLE trust_decisions (
    id           UUID PRIMARY KEY,
    subject_type VARCHAR(20) NOT NULL,
    subject_id   UUID NOT NULL,
    user_id      UUID REFERENCES users (id) ON DELETE CASCADE,
    listing_id   UUID REFERENCES listings (id) ON DELETE CASCADE,
    decision     VARCHAR(20) NOT NULL,
    reason_code  VARCHAR(40) NOT NULL,
    note         VARCHAR(1000),
    expires_at   TIMESTAMPTZ,
    actor_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_trust_decisions_subject CHECK (subject_type IN ('KYC', 'OWNERSHIP')),
    CONSTRAINT chk_trust_decisions_decision CHECK (decision IN ('APPROVED', 'REJECTED', 'REVOKED', 'EXPIRED'))
);
CREATE INDEX idx_trust_decisions_subject ON trust_decisions (subject_type, subject_id, created_at DESC, id DESC);
CREATE INDEX idx_trust_decisions_user ON trust_decisions (user_id, created_at DESC) WHERE user_id IS NOT NULL;

-- One reminder per subject and validity period (a renewed approval has a new expires_at, so it is reminded again).
CREATE TABLE trust_expiry_notices (
    subject_type VARCHAR(20) NOT NULL,
    subject_id   UUID NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    notified_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (subject_type, subject_id, expires_at)
);

CREATE INDEX IF NOT EXISTS idx_user_kyc_profiles_expiry ON user_kyc_profiles (expires_at) WHERE status = 'VERIFIED';
CREATE INDEX IF NOT EXISTS idx_listing_verifications_expiry ON listing_verifications (expires_at)
    WHERE status = 'VERIFIED_OWNER' AND revoked_at IS NULL;

-- Existing decisions become the first history entries (decider unknown for rows decided before this release).
INSERT INTO trust_decisions (id, subject_type, subject_id, user_id, decision, reason_code, note, expires_at, created_at)
-- user_kyc_profiles.user_id has no foreign key: a profile of a deleted account keeps its history without the user link.
SELECT uuid_generate_v4(), 'KYC', k.id, (SELECT u.id FROM users u WHERE u.id = k.user_id),
       CASE k.status WHEN 'VERIFIED' THEN 'APPROVED' ELSE 'REJECTED' END,
       'LEGACY', k.rejection_reason, k.expires_at, COALESCE(k.verified_at, k.created_at)
FROM user_kyc_profiles k
WHERE k.status IN ('VERIFIED', 'REJECTED');

INSERT INTO trust_decisions (id, subject_type, subject_id, listing_id, decision, reason_code, note, expires_at, actor_id, created_at)
SELECT uuid_generate_v4(), 'OWNERSHIP', v.id, v.listing_id,
       CASE v.status WHEN 'VERIFIED_OWNER' THEN 'APPROVED' WHEN 'REVOKED' THEN 'REVOKED' ELSE 'REJECTED' END,
       'LEGACY', v.verifier_note, v.expires_at, v.decided_by, COALESCE(v.verified_at, v.created_at)
FROM listing_verifications v
WHERE v.status IN ('VERIFIED_OWNER', 'REJECTED', 'REVOKED')
  AND EXISTS (SELECT 1 FROM listings l WHERE l.id = v.listing_id);

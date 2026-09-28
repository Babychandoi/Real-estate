-- Shared schema for the 2026-09-27 audit remediation (contract §2.1–§2.5), owned by stream S0-BE.
-- Additive only: existing write paths (JPA entities, UAT seeder) keep working without mapping the new columns.
-- Production impact: ADD COLUMN with constant or STABLE defaults is metadata-only; the STORED generated column rewrites
-- listing_revisions once; every backfill is one UPDATE guarded by "IS NULL" so a re-run would change nothing.

-- Fail fast instead of queueing behind a concurrent long lock (pg_dump, a slow query): the whole migration rolls back
-- and the deploy can be retried. Both settings end with this migration's transaction.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- §2.1 Money and attributes of a listing revision -------------------------------------------------------------------
ALTER TABLE listing_revisions
    ADD COLUMN IF NOT EXISTS price_period VARCHAR(10)
        GENERATED ALWAYS AS (CASE WHEN purpose = 'RENT' THEN 'MONTH' END) STORED,
    ADD COLUMN IF NOT EXISTS monthly_service_fee_vnd BIGINT,
    ADD COLUMN IF NOT EXISTS deposit_vnd BIGINT,
    ADD COLUMN IF NOT EXISTS furnishing VARCHAR(20),
    ADD COLUMN IF NOT EXISTS legal_status_code VARCHAR(30),
    ADD COLUMN IF NOT EXISTS project_id UUID;

ALTER TABLE listing_revisions
    ADD CONSTRAINT chk_listing_revisions_service_fee_non_negative
        CHECK (monthly_service_fee_vnd IS NULL OR monthly_service_fee_vnd >= 0),
    ADD CONSTRAINT chk_listing_revisions_deposit_non_negative
        CHECK (deposit_vnd IS NULL OR deposit_vnd >= 0),
    ADD CONSTRAINT chk_listing_revisions_furnishing
        CHECK (furnishing IS NULL OR furnishing IN ('NONE', 'BASIC', 'FULL')),
    ADD CONSTRAINT chk_listing_revisions_legal_status_code
        CHECK (legal_status_code IS NULL OR legal_status_code IN
               ('RED_BOOK', 'PINK_BOOK', 'SALE_CONTRACT', 'PENDING_CERTIFICATE', 'OTHER')),
    ADD CONSTRAINT fk_listing_revisions_project FOREIGN KEY (project_id) REFERENCES projects (id);

-- Supports the foreign key check on project delete and "listings of a project" lookups; most revisions have no project.
CREATE INDEX IF NOT EXISTS idx_listing_revisions_project ON listing_revisions (project_id) WHERE project_id IS NOT NULL;

-- Legal code from the free-text legal_status (kept as display detail). Accents and case are ignored; "chờ sổ" wins over
-- the certificate it waits for ("Đang chờ sổ hồng" is PENDING_CERTIFICATE), any other non-empty text is OTHER.
WITH normalized AS (
    SELECT id, lower(unaccent(btrim(legal_status))) AS legal_text
    FROM listing_revisions
    WHERE legal_status_code IS NULL AND legal_status IS NOT NULL AND btrim(legal_status) <> ''
)
UPDATE listing_revisions r
SET legal_status_code = CASE
        WHEN n.legal_text ~ '\mcho( ra| cap)? so\M' THEN 'PENDING_CERTIFICATE'
        WHEN n.legal_text ~ '\mso do\M' THEN 'RED_BOOK'
        WHEN n.legal_text ~ '\mso hong\M' THEN 'PINK_BOOK'
        WHEN n.legal_text ~ '\m(hop dong mua ban|hdmb)\M' THEN 'SALE_CONTRACT'
        ELSE 'OTHER'
    END
FROM normalized n
WHERE r.id = n.id;

-- §2.2 Listing lifecycle and data quality -----------------------------------------------------------------------------
ALTER TABLE listings
    ADD COLUMN IF NOT EXISTS availability_confirmed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'DIRECT',
    ADD COLUMN IF NOT EXISTS property_asset_id UUID; -- foreign key added by S4 together with property_assets

ALTER TABLE listings
    ADD CONSTRAINT chk_listings_source CHECK (source IN ('DIRECT', 'IMPORT', 'SEED'));

-- A published listing was last confirmed available when it was last changed; others have no confirmation yet.
UPDATE listings SET availability_confirmed_at = updated_at
WHERE status = 'ACTIVE' AND availability_confirmed_at IS NULL;

-- §2.7 Indexes, kept because EXPLAIN (ANALYZE, BUFFERS) on 212k synthetic listings showed them used
-- (docs/audit-2026-09-27/streams/s0-be.md): the ACTIVE feed page went from a parallel seq scan (41.8 ms, 3,034 buffers)
-- to an index-only scan (0.3 ms, 28 buffers); owner + status pages became an ordered index-only scan
-- (64 buffers instead of 924 for an owner with 12,000 listings). CREATE INDEX blocks writes to listings while it builds,
-- which takes milliseconds at production size.
CREATE INDEX IF NOT EXISTS idx_listings_owner_status_created ON listings (owner_id, status, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_listings_active_created ON listings (created_at DESC, id DESC) WHERE status = 'ACTIVE';

-- §2.3 Trust validity --------------------------------------------------------------------------------------------------
ALTER TABLE user_kyc_profiles ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

UPDATE user_kyc_profiles SET expires_at = verified_at + INTERVAL '24 months'
WHERE status = 'VERIFIED' AND verified_at IS NOT NULL AND expires_at IS NULL;

ALTER TABLE listing_verifications
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS decided_by UUID;

ALTER TABLE listing_verifications
    ADD CONSTRAINT fk_listing_verifications_decided_by FOREIGN KEY (decided_by) REFERENCES users (id);

UPDATE listing_verifications SET expires_at = verified_at + INTERVAL '180 days'
WHERE status = 'VERIFIED_OWNER' AND verified_at IS NOT NULL AND expires_at IS NULL;

-- §2.4 Leads -----------------------------------------------------------------------------------------------------------
ALTER TABLE leads DROP CONSTRAINT IF EXISTS chk_lead_status;
ALTER TABLE leads
    ADD CONSTRAINT chk_lead_status CHECK (status IN ('NEW', 'CONTACTED', 'APPOINTED', 'CLOSED', 'SPAM', 'WITHDRAWN'));

ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS first_response_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS qualification VARCHAR(20),
    ADD COLUMN IF NOT EXISTS qualified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS qualification_reason VARCHAR(200);

ALTER TABLE leads
    ADD CONSTRAINT chk_leads_qualification CHECK (qualification IS NULL OR qualification IN ('QUALIFIED', 'UNQUALIFIED'));

-- Existing leads have no recorded change history: their creation is the last known change. first_response_at stays
-- NULL because the moment of the first owner response was never stored (no invented timestamps).
UPDATE leads SET updated_at = created_at WHERE updated_at > created_at;

-- §2.5 Role values -----------------------------------------------------------------------------------------------------
-- NOT VALID first so the constraint never blocks the deploy; it is validated right away when existing rows comply.
ALTER TABLE user_roles
    ADD CONSTRAINT chk_user_roles_role CHECK (role IN ('USER', 'OWNER', 'BROKER', 'MODERATOR', 'ADMIN')) NOT VALID;

DO $$
BEGIN
    ALTER TABLE user_roles VALIDATE CONSTRAINT chk_user_roles_role;
EXCEPTION
    WHEN check_violation THEN
        RAISE WARNING 'user_roles contains role values outside USER/OWNER/BROKER/MODERATOR/ADMIN; chk_user_roles_role stays NOT VALID and only guards new rows';
END $$;

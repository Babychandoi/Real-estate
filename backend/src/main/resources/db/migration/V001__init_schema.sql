-- ==============================================================================
-- Flyway Migration V001: Khởi tạo Schema CSDL cốt lõi cho BDS WF 2026
-- Thiết kế theo tiêu chuẩn: PROJECT_CODE_RULES_BDS.md mục 10
-- ==============================================================================

-- 1. Tiện ích & Không gian địa lý PostGIS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- PostGIS được kích hoạt khi triển khai trên PostgreSQL có hỗ trợ không gian
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS postgis;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'PostGIS extension not available or skipped in current environment';
END $$;

-- 2. Bảng người dùng (Users) - Lưu trữ thông tin định danh và bảo vệ PII
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone_lookup_hash VARCHAR(64) NOT NULL,
    phone_encrypted TEXT NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_phone_hash UNIQUE (phone_lookup_hash)
);

CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- 3. Phân quyền người dùng (User Roles)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- 4. Bảng gốc Tin đăng BĐS (Listings aggregate root)
CREATE TABLE IF NOT EXISTS listings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    public_revision_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_listing_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'ACTIVE', 'PAUSED', 'EXPIRED', 'REJECTED', 'LOCKED'))
);

CREATE INDEX IF NOT EXISTS idx_listings_owner ON listings(owner_id);
CREATE INDEX IF NOT EXISTS idx_listings_status ON listings(status);

-- 5. Bảng Phiên bản tin đăng (Listing Revisions - Bất biến khi đã Submit/Duyệt)
CREATE TABLE IF NOT EXISTS listing_revisions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    revision_number INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    title VARCHAR(255) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    property_type VARCHAR(30) NOT NULL,
    price_vnd BIGINT NOT NULL,
    area_m2 NUMERIC(10, 2) NOT NULL,
    description TEXT,
    province_code VARCHAR(50),
    district_code VARCHAR(50),
    ward_code VARCHAR(50),
    address_summary VARCHAR(255),
    public_latitude DOUBLE PRECISION,
    public_longitude DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMPTZ,
    moderated_at TIMESTAMPTZ,
    moderation_note TEXT,
    CONSTRAINT uq_listing_revision UNIQUE (listing_id, revision_number),
    CONSTRAINT chk_revision_price CHECK (price_vnd >= 0),
    CONSTRAINT chk_revision_area CHECK (area_m2 > 0)
);

CREATE INDEX IF NOT EXISTS idx_revisions_listing_status ON listing_revisions(listing_id, status);

-- Cập nhật ràng buộc khóa ngoại cho public_revision_id của listings
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_listings_public_revision'
    ) THEN
        ALTER TABLE listings
            ADD CONSTRAINT fk_listings_public_revision
            FOREIGN KEY (public_revision_id) REFERENCES listing_revisions(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- 6. Hộp thư khách hàng tiềm năng (Leads)
CREATE TABLE IF NOT EXISTS leads (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE RESTRICT,
    full_name VARCHAR(150) NOT NULL,
    phone_encrypted TEXT NOT NULL,
    phone_lookup_hash VARCHAR(64) NOT NULL,
    note TEXT,
    consent_policy BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_lead_status CHECK (status IN ('NEW', 'CONTACTED', 'APPOINTED', 'CLOSED', 'SPAM'))
);

CREATE INDEX IF NOT EXISTS idx_leads_listing_created ON leads(listing_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_leads_phone_hash ON leads(phone_lookup_hash);

-- 7. Durable Transactional Outbox (Bền vững hóa thông điệp tích hợp & worker)
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed ON outbox_events(created_at) WHERE processed_at IS NULL;

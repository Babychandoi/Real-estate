CREATE TABLE service_plans (
    code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price_vnd BIGINT NOT NULL CHECK (price_vnd >= 0),
    listing_quota INT NOT NULL CHECK (listing_quota >= 0),
    duration_days INT NOT NULL CHECK (duration_days > 0),
    description VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0
);

INSERT INTO service_plans(code,name,price_vnd,listing_quota,duration_days,description,sort_order) VALUES
 ('FREE','Miễn phí',0,2,30,'Dành cho người đăng thử, 2 tin trong 30 ngày',0),
 ('STANDARD','Tiêu chuẩn',199000,10,30,'10 tin đăng, thống kê cơ bản và hỗ trợ ưu tiên',10),
 ('PRO','Chuyên nghiệp',499000,40,30,'40 tin đăng, workspace môi giới, analytics và SLA ưu tiên',20)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE users ADD COLUMN IF NOT EXISTS plan_code VARCHAR(30) NOT NULL DEFAULT 'FREE' REFERENCES service_plans(code);
ALTER TABLE users ADD COLUMN IF NOT EXISTS plan_expires_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS listing_quota_remaining INT NOT NULL DEFAULT 2 CHECK (listing_quota_remaining >= 0);

CREATE TABLE bank_settings (
    singleton_id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (singleton_id = 1),
    bank_bin VARCHAR(6) NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    account_number VARCHAR(19) NOT NULL,
    account_name VARCHAR(150) NOT NULL,
    admin_notification_email VARCHAR(150),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE package_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    plan_code VARCHAR(30) NOT NULL REFERENCES service_plans(code),
    amount_vnd BIGINT NOT NULL CHECK (amount_vnd > 0),
    transfer_reference VARCHAR(25) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('CREATED','TRANSFER_REPORTED','APPROVED','REJECTED','CANCELLED')),
    user_reported_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    review_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_package_orders_queue ON package_orders(status, created_at DESC);
CREATE INDEX idx_package_orders_user ON package_orders(user_id, created_at DESC);

CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    invoice_number VARCHAR(40) NOT NULL UNIQUE,
    order_id UUID NOT NULL UNIQUE REFERENCES package_orders(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    amount_vnd BIGINT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(600) NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_notifications ON user_notifications(user_id, created_at DESC);

CREATE TABLE broker_sla_settings (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    first_response_minutes INT NOT NULL DEFAULT 30 CHECK (first_response_minutes BETWEEN 5 AND 1440),
    reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    daily_digest_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE geocode_cache (
    query_hash CHAR(64) PRIMARY KEY,
    query_text VARCHAR(300) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE media_objects ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC','KYC_PRIVATE'));

-- ==============================================================================
-- Flyway Migration V005: Tạo bảng Giao dịch Đặt cọc & Ký quỹ Escrow Bảo đảm
-- Theo đặc tả FR28, FR30, UC05 và PROJECT_CODE_RULES_BDS.md
-- ==============================================================================

CREATE TABLE IF NOT EXISTS deposit_contracts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE RESTRICT,
    buyer_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    buyer_name VARCHAR(150) NOT NULL,
    buyer_phone VARCHAR(20) NOT NULL,
    buyer_id_masked VARCHAR(20) NOT NULL,
    seller_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    seller_name VARCHAR(150) NOT NULL,
    seller_phone VARCHAR(20) NOT NULL,
    deposit_amount NUMERIC(15, 2) NOT NULL,
    listing_price NUMERIC(15, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    terms_conditions TEXT NOT NULL,
    buyer_signed_at TIMESTAMPTZ,
    buyer_otp_verified BOOLEAN NOT NULL DEFAULT FALSE,
    seller_signed_at TIMESTAMPTZ,
    seller_otp_verified BOOLEAN NOT NULL DEFAULT FALSE,
    escrow_locked_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    dispute_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_deposit_status CHECK (status IN ('DRAFT', 'AWAITING_SELLER_SIGN', 'ESCROW_LOCKED', 'COMPLETED', 'REFUNDED', 'DISPUTED'))
);

CREATE INDEX IF NOT EXISTS idx_deposit_contracts_listing ON deposit_contracts(listing_id);
CREATE INDEX IF NOT EXISTS idx_deposit_contracts_buyer ON deposit_contracts(buyer_id);
CREATE INDEX IF NOT EXISTS idx_deposit_contracts_seller ON deposit_contracts(seller_id);
CREATE INDEX IF NOT EXISTS idx_deposit_contracts_status ON deposit_contracts(status);

CREATE TABLE IF NOT EXISTS escrow_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_id UUID NOT NULL REFERENCES deposit_contracts(id) ON DELETE CASCADE,
    action VARCHAR(30) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    performed_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_escrow_action CHECK (action IN ('DEPOSIT', 'LOCK', 'RELEASE', 'REFUND', 'DISPUTE'))
);

CREATE INDEX IF NOT EXISTS idx_escrow_transactions_contract ON escrow_transactions(contract_id);

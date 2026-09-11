-- ==============================================================================
-- Flyway Migration V004: Xác thực Định danh eKYC & Gắn Nhãn Tin Chính Chủ (FR01, FR03, NFR12)
-- Thiết kế theo tiêu chuẩn bảo mật dữ liệu PII và kiến trúc BDS WF 2026
-- ==============================================================================

-- 1. Bảng Hồ sơ định danh eKYC người dùng / chủ tài khoản (User KYC Profiles)
CREATE TABLE IF NOT EXISTS user_kyc_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    id_number_encrypted TEXT NOT NULL,
    id_number_lookup_hash VARCHAR(64) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    dob VARCHAR(20),
    address VARCHAR(255),
    id_card_front_url TEXT,
    id_card_back_url TEXT,
    selfie_url TEXT,
    face_match_score DOUBLE PRECISION,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_kyc_status ON user_kyc_profiles(status);
CREATE INDEX IF NOT EXISTS idx_kyc_user ON user_kyc_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_kyc_id_hash ON user_kyc_profiles(id_number_lookup_hash);

-- 2. Bảng Hồ sơ Thẩm định Pháp lý & Quyền Sở hữu Tin đăng (Listing Verifications)
CREATE TABLE IF NOT EXISTS listing_verifications (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL,
    user_kyc_id UUID,
    verification_type VARCHAR(50) NOT NULL,
    certificate_number VARCHAR(100),
    document_urls TEXT,
    owner_name_on_doc VARCHAR(150) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    verifier_note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_verifications_status ON listing_verifications(status);
CREATE INDEX IF NOT EXISTS idx_verifications_listing ON listing_verifications(listing_id);

-- 3. Bổ sung cờ nhãn chính chủ cho bảng listings
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'listings' AND column_name = 'is_verified_owner'
    ) THEN
        ALTER TABLE listings ADD COLUMN is_verified_owner BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

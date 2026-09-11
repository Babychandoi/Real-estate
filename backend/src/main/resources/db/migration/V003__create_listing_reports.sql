-- ============================================================================
-- V003: Quản lý Vụ việc Báo xấu & Khiếu nại Tin đăng (Violation Reports Desk)
-- Tuân thủ: FR21, FR27, FR31, UC04
-- ============================================================================

CREATE TABLE IF NOT EXISTS listing_reports (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL,
    case_number VARCHAR(30) NOT NULL UNIQUE,
    reporter_type VARCHAR(30) NOT NULL DEFAULT 'ANONYMOUS',
    reporter_phone VARCHAR(50),
    category VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    description TEXT NOT NULL,
    evidence_urls TEXT,
    resolution_note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_reports_status ON listing_reports(status, severity);
CREATE INDEX IF NOT EXISTS idx_reports_listing ON listing_reports(listing_id);

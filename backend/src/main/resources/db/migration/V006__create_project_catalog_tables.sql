-- ==============================================================================
-- Flyway Migration V006: Tạo bảng Danh mục Dự án BĐS (FR25, UC08)
-- Theo đặc tả SRS 0.9.1 và PROJECT_CODE_RULES_BDS.md
-- ==============================================================================

CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    developer_name VARCHAR(150) NOT NULL,
    province_code VARCHAR(50) NOT NULL,
    district_code VARCHAR(50) NOT NULL,
    address VARCHAR(255) NOT NULL,
    total_area_m2 NUMERIC(12, 2),
    total_blocks INT NOT NULL DEFAULT 1,
    total_units INT NOT NULL DEFAULT 0,
    handover_year INT,
    legal_license_number VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_projects_slug UNIQUE (slug),
    CONSTRAINT chk_project_status CHECK (status IN ('ACTIVE', 'PLANNING', 'UNDER_CONSTRUCTION', 'COMPLETED', 'LOCKED'))
);

CREATE INDEX IF NOT EXISTS idx_projects_slug ON projects(slug);
CREATE INDEX IF NOT EXISTS idx_projects_district ON projects(district_code);
CREATE INDEX IF NOT EXISTS idx_projects_status ON projects(status);

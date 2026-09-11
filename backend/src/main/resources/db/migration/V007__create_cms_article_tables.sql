-- ====================================================================
-- Migration: V007__create_cms_article_tables.sql
-- Mục đích: Bảng bài viết CMS, chính sách pháp lý & cẩm nang thị trường
-- Tuân thủ: ERD04/ED04, FR24, FR26, FR32, UC07
-- ====================================================================

CREATE TABLE IF NOT EXISTS cms_articles (
    id UUID PRIMARY KEY,
    slug VARCHAR(255) NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    published_revision_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cms_articles_category ON cms_articles(category);
CREATE INDEX IF NOT EXISTS idx_cms_articles_status ON cms_articles(status);
CREATE INDEX IF NOT EXISTS idx_cms_articles_slug ON cms_articles(slug);

CREATE TABLE IF NOT EXISTS cms_article_revisions (
    id UUID PRIMARY KEY,
    article_id UUID NOT NULL REFERENCES cms_articles(id) ON DELETE CASCADE,
    revision_number INT NOT NULL,
    title VARCHAR(500) NOT NULL,
    summary TEXT,
    content_html TEXT NOT NULL,
    cover_image_url VARCHAR(1000),
    author_name VARCHAR(255) NOT NULL,
    legal_reference VARCHAR(500),
    meta_description TEXT,
    canonical_url VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    rejection_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_cms_revisions_article_id ON cms_article_revisions(article_id);
CREATE INDEX IF NOT EXISTS idx_cms_revisions_status ON cms_article_revisions(status);

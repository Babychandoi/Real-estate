-- S7-SEO (audit P-07, F16): CMS publishing workflow — immutable revisions, scheduled publishing, unpublish (410),
-- sources, preview tokens — plus the content generation used by the SEO/sitemap caches.

-- Article lifecycle. published_at = when the current public revision went live; first_published_at never changes
-- once set (an unpublished article answers 410, a never-published one 404).
ALTER TABLE cms_articles
    ADD COLUMN IF NOT EXISTS scheduled_revision_id UUID REFERENCES cms_article_revisions (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS scheduled_publish_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS published_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS first_published_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS unpublished_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version               BIGINT NOT NULL DEFAULT 0;

UPDATE cms_articles a
SET published_at       = COALESCE(r.reviewed_at, a.updated_at),
    first_published_at = COALESCE(r.reviewed_at, a.updated_at)
FROM cms_article_revisions r
WHERE r.id = a.published_revision_id AND a.published_at IS NULL;

ALTER TABLE cms_articles
    ADD CONSTRAINT chk_cms_articles_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'PUBLISHED', 'ARCHIVED', 'REJECTED')),
    ADD CONSTRAINT chk_cms_articles_category CHECK (category IN ('LEGAL_POLICY', 'KNOWLEDGE', 'MARKET_INSIGHTS')),
    ADD CONSTRAINT chk_cms_articles_schedule CHECK ((scheduled_revision_id IS NULL) = (scheduled_publish_at IS NULL));

-- Public list: newest published first, keyset friendly; the scheduler finds due articles by index.
CREATE INDEX IF NOT EXISTS idx_cms_articles_public ON cms_articles (published_at DESC, id DESC) WHERE status = 'PUBLISHED';
CREATE INDEX IF NOT EXISTS idx_cms_articles_due ON cms_articles (scheduled_publish_at) WHERE scheduled_revision_id IS NOT NULL;

-- Revisions: sources and who wrote/submitted them. SCHEDULED = approved, waiting for its publish time; SUPERSEDED = was
-- public, replaced by a newer revision (kept, never edited: ERD04/ED04).
ALTER TABLE cms_article_revisions
    ADD COLUMN IF NOT EXISTS source_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS source_url   VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS created_by   UUID,
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ;

ALTER TABLE cms_article_revisions
    ADD CONSTRAINT chk_cms_revisions_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'SCHEDULED', 'PUBLISHED', 'SUPERSEDED', 'REJECTED', 'ARCHIVED'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_cms_revisions_number ON cms_article_revisions (article_id, revision_number);

-- Content of a revision is frozen once it left DRAFT: only its workflow columns may change.
CREATE OR REPLACE FUNCTION bds_cms_revision_immutable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.status <> 'DRAFT' AND (
        NEW.article_id, NEW.revision_number, NEW.title, NEW.summary, NEW.content_html, NEW.cover_image_url,
        NEW.author_name, NEW.legal_reference, NEW.meta_description, NEW.canonical_url, NEW.source_name, NEW.source_url,
        NEW.created_at, NEW.created_by
    ) IS DISTINCT FROM (
        OLD.article_id, OLD.revision_number, OLD.title, OLD.summary, OLD.content_html, OLD.cover_image_url,
        OLD.author_name, OLD.legal_reference, OLD.meta_description, OLD.canonical_url, OLD.source_name, OLD.source_url,
        OLD.created_at, OLD.created_by
    ) THEN
        RAISE EXCEPTION 'cms revision % is immutable once submitted (status %)', OLD.id, OLD.status
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS trg_cms_revision_immutable ON cms_article_revisions;
CREATE TRIGGER trg_cms_revision_immutable BEFORE UPDATE ON cms_article_revisions
    FOR EACH ROW EXECUTE FUNCTION bds_cms_revision_immutable();

-- Preview links for unpublished revisions: only the SHA-256 of the token is stored; short-lived.
CREATE TABLE IF NOT EXISTS cms_preview_tokens (
    id          UUID PRIMARY KEY,
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    revision_id UUID        NOT NULL REFERENCES cms_article_revisions (id) ON DELETE CASCADE,
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_cms_preview_tokens_expiry ON cms_preview_tokens (expires_at);

-- One counter for public content (articles, projects, project amenities): part of the SEO cache keys, bumped in the
-- same transaction as a publish/unpublish/project change, so a cached page or sitemap never outlives the change.
CREATE TABLE IF NOT EXISTS seo_content_state (
    singleton_id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (singleton_id = 1),
    generation   BIGINT      NOT NULL DEFAULT 1,
    changed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO seo_content_state (singleton_id) VALUES (1) ON CONFLICT DO NOTHING;

CREATE OR REPLACE FUNCTION bds_bump_seo_content_generation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    UPDATE seo_content_state SET generation = generation + 1, changed_at = now() WHERE singleton_id = 1;
    RETURN NULL;
END
$$;

DROP TRIGGER IF EXISTS trg_cms_articles_seo_generation ON cms_articles;
CREATE TRIGGER trg_cms_articles_seo_generation AFTER INSERT OR UPDATE OR DELETE ON cms_articles
    FOR EACH STATEMENT EXECUTE FUNCTION bds_bump_seo_content_generation();

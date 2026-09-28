-- Stream S2-SEARCH (contract §9): keep listing_public_read and the search index in step with the write model.
--
-- * listings: a DEFERRED constraint trigger refreshes the read-model row at COMMIT of the writing transaction (it sees
--   the final state of every table touched in that transaction), so hiding/locking/approving is visible to the database
--   search path immediately, and enqueues a 'search-index' job for Elasticsearch.
-- * every other source table (revisions, media, users, roles, KYC, ownership checks, projects) only enqueues jobs; the
--   job handler refreshes the rows and writes Elasticsearch. Owner/project changes enqueue one fan-out job instead of
--   one job per listing inside the user's transaction.
-- Jobs coalesce on their dedupe key (listing id / owner:<id> / project:<id>) through bds_enqueue_job (V028).

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

CREATE OR REPLACE FUNCTION bds_enqueue_listing_index(p_listing uuid)
    RETURNS void
    LANGUAGE plpgsql
AS $$
BEGIN
    IF p_listing IS NOT NULL THEN
        PERFORM bds_enqueue_job('search-index', p_listing::text, jsonb_build_object('listingId', p_listing));
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION bds_enqueue_owner_index(p_owner uuid)
    RETURNS void
    LANGUAGE plpgsql
AS $$
BEGIN
    IF p_owner IS NOT NULL THEN
        PERFORM bds_enqueue_job('search-index', 'owner:' || p_owner::text, jsonb_build_object('ownerId', p_owner));
    END IF;
END;
$$;

-- listings ------------------------------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION bds_lpr_on_listing()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM bds_enqueue_listing_index(OLD.id);
        RETURN OLD;
    END IF;
    PERFORM bds_refresh_listing_public_read(NEW.id);
    PERFORM bds_enqueue_listing_index(NEW.id);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_lpr_listing_insert ON listings;
CREATE CONSTRAINT TRIGGER trg_lpr_listing_insert
    AFTER INSERT ON listings
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION bds_lpr_on_listing();

DROP TRIGGER IF EXISTS trg_lpr_listing_update ON listings;
CREATE CONSTRAINT TRIGGER trg_lpr_listing_update
    AFTER UPDATE ON listings
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status
          OR OLD.public_revision_id IS DISTINCT FROM NEW.public_revision_id
          OR OLD.slug IS DISTINCT FROM NEW.slug
          OR OLD.owner_id IS DISTINCT FROM NEW.owner_id
          OR OLD.availability_confirmed_at IS DISTINCT FROM NEW.availability_confirmed_at
          OR OLD.updated_at IS DISTINCT FROM NEW.updated_at)
    EXECUTE FUNCTION bds_lpr_on_listing();

DROP TRIGGER IF EXISTS trg_lpr_listing_delete ON listings;
CREATE TRIGGER trg_lpr_listing_delete
    AFTER DELETE ON listings
    FOR EACH ROW EXECUTE FUNCTION bds_lpr_on_listing();

-- listing_revisions: only approved (public or previously public) revisions matter to the read model ------------------
CREATE OR REPLACE FUNCTION bds_lpr_on_revision()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') AND OLD.status = 'APPROVED' THEN
        PERFORM bds_enqueue_listing_index(OLD.listing_id);
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') AND NEW.status = 'APPROVED' THEN
        PERFORM bds_enqueue_listing_index(NEW.listing_id);
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_lpr_revision ON listing_revisions;
CREATE TRIGGER trg_lpr_revision
    AFTER INSERT OR UPDATE OR DELETE ON listing_revisions
    FOR EACH ROW EXECUTE FUNCTION bds_lpr_on_revision();

-- listing_media of the public revision ---------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION bds_lpr_on_media()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    v_listing uuid;
BEGIN
    FOR v_listing IN
        SELECT l.id FROM listings l
        WHERE l.public_revision_id IN (CASE WHEN TG_OP <> 'INSERT' THEN OLD.revision_id END,
                                       CASE WHEN TG_OP <> 'DELETE' THEN NEW.revision_id END)
    LOOP
        PERFORM bds_enqueue_listing_index(v_listing);
    END LOOP;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_lpr_media ON listing_media;
CREATE TRIGGER trg_lpr_media
    AFTER INSERT OR UPDATE OR DELETE ON listing_media
    FOR EACH ROW EXECUTE FUNCTION bds_lpr_on_media();

-- users (name, avatar, account status) ---------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION bds_lpr_on_user()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM bds_enqueue_owner_index(NEW.id);
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_lpr_user ON users;
CREATE TRIGGER trg_lpr_user
    AFTER UPDATE OF full_name, avatar_media_url, status ON users
    FOR EACH ROW
    WHEN (OLD.full_name IS DISTINCT FROM NEW.full_name
          OR OLD.avatar_media_url IS DISTINCT FROM NEW.avatar_media_url
          OR OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION bds_lpr_on_user();

-- user_roles and user_kyc_profiles (seller role and identity trust) ------------------------------------------------------
CREATE OR REPLACE FUNCTION bds_lpr_on_user_fact()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        PERFORM bds_enqueue_owner_index(OLD.user_id);
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') AND (TG_OP = 'INSERT' OR NEW.user_id IS DISTINCT FROM OLD.user_id) THEN
        PERFORM bds_enqueue_owner_index(NEW.user_id);
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_lpr_user_role ON user_roles;
CREATE TRIGGER trg_lpr_user_role
    AFTER INSERT OR UPDATE OR DELETE ON user_roles
    FOR EACH ROW EXECUTE FUNCTION bds_lpr_on_user_fact();

DROP TRIGGER IF EXISTS trg_lpr_kyc ON user_kyc_profiles;
CREATE TRIGGER trg_lpr_kyc
    AFTER INSERT OR UPDATE OR DELETE ON user_kyc_profiles
    FOR EACH ROW EXECUTE FUNCTION bds_lpr_on_user_fact();

-- listing_verifications (ownership trust) ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION bds_lpr_on_verification()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        PERFORM bds_enqueue_listing_index(OLD.listing_id);
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') AND (TG_OP = 'INSERT' OR NEW.listing_id IS DISTINCT FROM OLD.listing_id) THEN
        PERFORM bds_enqueue_listing_index(NEW.listing_id);
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_lpr_verification ON listing_verifications;
CREATE TRIGGER trg_lpr_verification
    AFTER INSERT OR UPDATE OR DELETE ON listing_verifications
    FOR EACH ROW EXECUTE FUNCTION bds_lpr_on_verification();

-- projects (name, slug, status shown on cards) -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION bds_lpr_on_project()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM bds_enqueue_job('search-index', 'project:' || NEW.id::text, jsonb_build_object('projectId', NEW.id));
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_lpr_project ON projects;
CREATE TRIGGER trg_lpr_project
    AFTER UPDATE OF name, slug, status ON projects
    FOR EACH ROW
    WHEN (OLD.name IS DISTINCT FROM NEW.name OR OLD.slug IS DISTINCT FROM NEW.slug OR OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION bds_lpr_on_project();

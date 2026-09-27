-- Durable job queue and scheduled-task locks (contract §3), owned by stream S0-BE.
-- New tables only; nothing existing is locked or rewritten.

-- A job is pending while completed_at and dead_lettered_at are both NULL. While pending, (queue, dedupe_key) is unique:
-- enqueueing the same key again coalesces into the pending row (payload replaced, run_at = earliest, enqueue_seq + 1).
CREATE TABLE background_jobs (
    id               UUID PRIMARY KEY,
    queue            VARCHAR(60)  NOT NULL,
    dedupe_key       VARCHAR(200),
    payload          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    run_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    attempts         INT          NOT NULL DEFAULT 0,
    max_attempts     INT          NOT NULL DEFAULT 10,
    enqueue_seq      BIGINT       NOT NULL DEFAULT 1,
    locked_by        VARCHAR(120),
    locked_until     TIMESTAMPTZ,
    lease_token      UUID,
    last_error       VARCHAR(2000),
    completed_at     TIMESTAMPTZ,
    dead_lettered_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_background_jobs_queue CHECK (queue ~ '^[a-z0-9][a-z0-9._-]{0,59}$'),
    CONSTRAINT chk_background_jobs_attempts CHECK (attempts >= 0 AND max_attempts > 0),
    CONSTRAINT chk_background_jobs_single_outcome CHECK (completed_at IS NULL OR dead_lettered_at IS NULL)
);

-- Coalescing target (ON CONFLICT infers this partial index).
CREATE UNIQUE INDEX uq_background_jobs_pending_dedupe ON background_jobs (queue, dedupe_key)
    WHERE completed_at IS NULL AND dead_lettered_at IS NULL AND dedupe_key IS NOT NULL;
-- Claim scan: due pending jobs of one queue in run_at order.
CREATE INDEX idx_background_jobs_due ON background_jobs (queue, run_at)
    WHERE completed_at IS NULL AND dead_lettered_at IS NULL;
-- "Already sent" lookups for once-only queues (e-mail) across pending and retained completed rows.
CREATE INDEX idx_background_jobs_dedupe ON background_jobs (queue, dedupe_key) WHERE dedupe_key IS NOT NULL;
-- Retention purge of completed rows.
CREATE INDEX idx_background_jobs_completed ON background_jobs (completed_at) WHERE completed_at IS NOT NULL;
-- Dead-letter inspection and the bds.jobs.dead gauge.
CREATE INDEX idx_background_jobs_dead ON background_jobs (queue, dead_lettered_at) WHERE dead_lettered_at IS NOT NULL;

-- Enqueue helper for triggers and SQL callers; the Java JobQueue uses the same function, so coalescing has one
-- definition. p_run_at NULL means now; p_max_attempts only applies when a new row is created.
CREATE OR REPLACE FUNCTION bds_enqueue_job(p_queue text, p_dedupe text, p_payload jsonb,
                                           p_run_at timestamptz DEFAULT now(), p_max_attempts integer DEFAULT 10)
    RETURNS uuid
    LANGUAGE plpgsql
AS $$
DECLARE
    v_id uuid;
BEGIN
    INSERT INTO background_jobs (id, queue, dedupe_key, payload, run_at, max_attempts)
    VALUES (gen_random_uuid(), p_queue, p_dedupe, COALESCE(p_payload, '{}'::jsonb), COALESCE(p_run_at, now()),
            COALESCE(p_max_attempts, 10))
    ON CONFLICT (queue, dedupe_key) WHERE completed_at IS NULL AND dead_lettered_at IS NULL AND dedupe_key IS NOT NULL
    DO UPDATE SET payload     = EXCLUDED.payload,
                  run_at      = LEAST(background_jobs.run_at, EXCLUDED.run_at),
                  enqueue_seq = background_jobs.enqueue_seq + 1,
                  updated_at  = now()
    RETURNING id INTO v_id;
    RETURN v_id;
END;
$$;

-- One row per scheduled task; a task runs only on the instance that holds an unexpired lock.
CREATE TABLE scheduled_task_locks (
    name         VARCHAR(100) PRIMARY KEY,
    locked_until TIMESTAMPTZ  NOT NULL,
    locked_by    VARCHAR(120) NOT NULL
);

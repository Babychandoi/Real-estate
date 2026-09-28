-- S3b-LEADS: a broker's team; leads of the owner's listings can be assigned to active members.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TABLE broker_team_members (
    owner_id   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    member_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_at TIMESTAMPTZ,
    PRIMARY KEY (owner_id, member_id),
    CONSTRAINT chk_team_not_self CHECK (owner_id <> member_id)
);
CREATE INDEX idx_broker_team_member ON broker_team_members (member_id) WHERE removed_at IS NULL;

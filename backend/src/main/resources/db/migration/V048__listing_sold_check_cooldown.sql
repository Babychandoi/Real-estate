-- S3a-SUPPLY Review 2: when the owner answered the last sold check (cooldown before a new one).
ALTER TABLE listings ADD COLUMN IF NOT EXISTS sold_check_cleared_at TIMESTAMPTZ;

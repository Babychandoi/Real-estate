-- S5-SEC phase B (UI-14, DS-11): the token pages tell "already used" apart from "replaced by a newer link", and the
-- verification link brings the person back to the page where they registered (a validated relative path).
ALTER TABLE email_verification_tokens
    ADD COLUMN return_path VARCHAR(512),
    ADD COLUMN superseded_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_email_verification_return_path
        CHECK (return_path IS NULL OR (left(return_path, 1) = '/' AND left(return_path, 2) <> '//'));

ALTER TABLE password_reset_tokens ADD COLUMN superseded_at TIMESTAMPTZ;

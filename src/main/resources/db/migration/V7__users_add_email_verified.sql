-- V7: track email verification status on users (Slice 8).
-- Existing rows are grandfathered as TRUE - they registered before verification
-- was a requirement and we don't want to lock them out.

ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users SET email_verified = TRUE;

-- New users will get FALSE by default (column default kept after backfill).

-- Slice 12: refresh-token revocation via a per-user epoch.
-- Bumping token_version invalidates every previously-issued JWT for that user
-- (access, refresh, password_reset, verify_email, change_email, account_restore).
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

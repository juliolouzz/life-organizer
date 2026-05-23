-- Slice 9: account deletion with grace period.
-- NULL = active account. Future timestamp = pending hard delete on/after that date.
ALTER TABLE users ADD COLUMN deletion_scheduled_at TIMESTAMPTZ NULL;

-- Partial index: only rows pending deletion are in the index, which is rare.
-- Used by the scheduled hard-delete job's lookup.
CREATE INDEX idx_users_deletion_scheduled
    ON users(deletion_scheduled_at)
    WHERE deletion_scheduled_at IS NOT NULL;

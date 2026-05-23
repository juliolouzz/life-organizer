-- V4: categories table (Slice 6).
-- Categories belong to a user. They are referenced by id from budgets and
-- recurring_transactions. Transactions themselves still carry a free-text
-- "category" column for backwards compatibility - it's matched case-insensitively
-- to categories.name when computing budget-vs-actual.

CREATE TABLE categories (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    name        VARCHAR(50)  NOT NULL,
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('INCOME', 'EXPENSE', 'SAVINGS', 'BOTH')),
    archived    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One category name per user (case-insensitive). LOWER() index implements that.
CREATE UNIQUE INDEX idx_categories_user_name_unique
    ON categories (user_id, LOWER(name));

CREATE INDEX idx_categories_user_archived ON categories (user_id) WHERE archived = FALSE;

-- Backfill: for each distinct (user_id, lower(category), type) in transactions,
-- insert a category row using the type as kind. If the same category name was
-- used across multiple types, the first one wins (the unique index catches dupes).
INSERT INTO categories (user_id, name, kind)
SELECT t.user_id, MIN(t.category), t.type
FROM transactions t
WHERE t.deleted_at IS NULL
GROUP BY t.user_id, LOWER(t.category), t.type
ON CONFLICT (user_id, LOWER(name)) DO NOTHING;

-- V5: monthly budgets per category (Slice 6).

CREATE TABLE budgets (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT         NOT NULL REFERENCES users(id),
    category_id  BIGINT         NOT NULL REFERENCES categories(id),
    amount       NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    month        INTEGER        NOT NULL CHECK (month BETWEEN 1 AND 12),
    year         INTEGER        NOT NULL CHECK (year BETWEEN 2000 AND 9999),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- One budget per (user, category, year, month).
CREATE UNIQUE INDEX idx_budgets_unique
    ON budgets (user_id, category_id, year, month);

-- Lookup pattern: "all budgets for user X in year/month Y/M"
CREATE INDEX idx_budgets_user_period
    ON budgets (user_id, year, month);

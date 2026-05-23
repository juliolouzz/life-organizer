-- V6: recurring transaction templates (Slice 6).
--
-- A recurring template produces real rows in `transactions` on a schedule.
-- The materialiser job (called on each /transactions list call) picks up
-- any rows where next_due_date <= today, not paused, not past end_date,
-- creates a transaction with the template values, and advances next_due_date.

CREATE TABLE recurring_transactions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT         NOT NULL REFERENCES users(id),
    category_id     BIGINT         NOT NULL REFERENCES categories(id),
    amount          NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    type            VARCHAR(10)    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE', 'SAVINGS')),
    description     VARCHAR(255)   NOT NULL DEFAULT '',
    frequency       VARCHAR(10)    NOT NULL CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY')),
    start_date      DATE           NOT NULL,
    end_date        DATE           NULL,
    next_due_date   DATE           NOT NULL,
    paused          BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_recurring_user_active
    ON recurring_transactions (user_id, next_due_date)
    WHERE paused = FALSE;

-- V2: transactions table per spec section 4.2
CREATE TABLE transactions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT        NOT NULL REFERENCES users(id),
    amount           NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    type             VARCHAR(10)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    category         VARCHAR(50)   NOT NULL,
    description      VARCHAR(255)  NOT NULL,
    transaction_date DATE          NOT NULL,
    deleted_at       TIMESTAMPTZ   NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Generic FK index for joins (e.g. /me-style aggregations later).
CREATE INDEX idx_transactions_user_id ON transactions (user_id);

-- Partial index backing the canonical list query - matches the JPQL keyset predicate exactly
-- so the planner can use this index instead of a seq scan + sort.
CREATE INDEX idx_transactions_user_active
    ON transactions (user_id, transaction_date DESC, id DESC)
    WHERE deleted_at IS NULL;

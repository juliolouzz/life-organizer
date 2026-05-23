-- Slice 13: per-user currency selection (display-only, no FX).
-- Default to BRL so existing rows + every "no preference" path keeps working.
ALTER TABLE users ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'BRL';
ALTER TABLE users ADD CONSTRAINT users_currency_check
    CHECK (currency IN ('BRL', 'USD', 'EUR'));

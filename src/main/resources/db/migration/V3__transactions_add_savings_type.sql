-- V3: extend transactions.type CHECK constraint to allow SAVINGS as a third type
-- (Slice 5). No data migration needed - no SAVINGS rows can have existed yet
-- because the old CHECK constraint forbade them.

ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;

ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
    CHECK (type IN ('INCOME', 'EXPENSE', 'SAVINGS'));

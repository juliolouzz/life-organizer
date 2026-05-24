-- Slice 14: per-user "month boundary" day.
-- The user defines their accounting month as starting on this day of the
-- calendar month (1-31). When the user picks "This month" on the dashboard
-- or a budget, the range is built around the most recent past anchor.
-- Default 1 -> behaves as a regular calendar month for every existing user.
ALTER TABLE users ADD COLUMN month_boundary_day INTEGER NOT NULL DEFAULT 1;
ALTER TABLE users ADD CONSTRAINT users_month_boundary_day_check
    CHECK (month_boundary_day BETWEEN 1 AND 31);

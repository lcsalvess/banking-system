
-- Add an explicit reference date for daily yield transactions.
ALTER TABLE transactions
    ADD COLUMN yield_date DATE;

-- Preserve the reference date of existing yield transactions.
UPDATE transactions
SET yield_date = created_at::date
WHERE transaction_type = 'YIELD';

-- Replace the previous daily-yield constraint with one based on the
-- explicit reference date.
DROP INDEX uk_transactions_daily_yield;

CREATE UNIQUE INDEX uk_transactions_daily_yield
    ON transactions (account_id, yield_date)
    WHERE transaction_type = 'YIELD'
        AND yield_date IS NOT NULL;

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_yield_date
        CHECK (
            (transaction_type = 'YIELD' AND yield_date IS NOT NULL)
                OR
            (transaction_type <> 'YIELD' AND yield_date IS NULL)
            );
-- Snapshot of the account balance immediately after each transaction.
--
-- Written in the same database transaction as the balance change, while the
-- account row is locked, so for a given account the sequence of rows ordered
-- by id is the exact history of its balance. It allows:
--   * reconciling accounts.balance against the ledger cheaply;
--   * reading a historical balance without summing the whole history;
--   * showing a running balance in statements.

-- Added as nullable so existing rows can be backfilled.
ALTER TABLE transactions
    ADD COLUMN balance_after NUMERIC(19, 2);

-- Backfill: running balance per account in insertion (id) order.
-- Rows are inserted while the account row is locked, so id order is also the
-- order in which the balance changed.
UPDATE transactions t
SET balance_after = r.running_balance
FROM (SELECT id,
             SUM(
             CASE
                 WHEN transaction_type IN ('DEPOSIT', 'TRANSFER_RECEIVED', 'YIELD')
                     THEN amount
                 WHEN transaction_type IN ('WITHDRAWAL', 'TRANSFER_SENT')
                     THEN -amount
                 ELSE 0
                 END
                ) OVER (PARTITION BY account_id ORDER BY id) AS running_balance
      FROM transactions) r
WHERE t.id = r.id;

ALTER TABLE transactions
    ALTER COLUMN balance_after SET NOT NULL;

-- Same bounds as accounts (ck_accounts_balance_non_negative and
-- ck_accounts_balance_max).
ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_balance_after_range
        CHECK (balance_after >= 0 AND balance_after <= 9999999999999.99);

-- Serves "latest transaction of an account" lookups (ORDER BY id DESC LIMIT 1)
-- used by the historical balance and by the reconciliation.
CREATE INDEX idx_transactions_account_id_desc
    ON transactions (account_id, id DESC);
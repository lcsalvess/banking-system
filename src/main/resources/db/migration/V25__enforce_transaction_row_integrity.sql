-- ============================================================
-- Transaction amount
-- ============================================================

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_amount_max
        CHECK (amount <= 9999999999999.99) NOT VALID;


-- ============================================================
-- Transfer code
-- ============================================================

-- Older transfers may not have a transfer code.
-- Keep existing rows while enforcing the rule for new records.

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_transfer_code
        CHECK (
            (
                transaction_type IN ('TRANSFER_SENT', 'TRANSFER_RECEIVED')
                    AND transfer_code IS NOT NULL
                )
                OR
            (
                transaction_type NOT IN ('TRANSFER_SENT', 'TRANSFER_RECEIVED')
                    AND transfer_code IS NULL
                )
            ) NOT VALID;

-- Prevent duplicate transfer directions for the same code.

CREATE UNIQUE INDEX uk_transactions_transfer_code_type
    ON transactions (transfer_code, transaction_type)
    WHERE transfer_code IS NOT NULL
        AND transaction_type IN ('TRANSFER_SENT', 'TRANSFER_RECEIVED');


-- ============================================================
-- System-generated yield attribution
-- ============================================================

-- Legacy yields attributed to system_migration are now
-- explicitly identified as system-generated transactions.

UPDATE transactions
SET performed_by_system  = TRUE,
    performed_by_user_id = NULL
WHERE transaction_type = 'YIELD'
  AND performed_by_system = FALSE
  AND performed_by_user_id = (SELECT id
                              FROM users
                              WHERE username = 'system_migration');


-- ============================================================
-- Transaction actor integrity
-- ============================================================

ALTER TABLE transactions
    DROP CONSTRAINT ck_transactions_actor;

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_actor
        CHECK (
            (
                transaction_type = 'YIELD'
                    AND performed_by_system = TRUE
                    AND performed_by_user_id IS NULL
                )
                OR
            (
                transaction_type <> 'YIELD'
                    AND performed_by_system = FALSE
                    AND performed_by_user_id IS NOT NULL
                )
            );

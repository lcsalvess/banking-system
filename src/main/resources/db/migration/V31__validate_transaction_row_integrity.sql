-- ============================================================
-- Validate transaction row integrity constraints
-- ============================================================

-- V25 introduced these CHECK constraints as NOT VALID to avoid
-- scanning historical transaction records during deployment.
--
-- Existing records have now been checked and no violations were
-- found. Validate both constraints against the complete table.

ALTER TABLE transactions
    VALIDATE CONSTRAINT ck_transactions_amount_max,
    VALIDATE CONSTRAINT ck_transactions_transfer_code;

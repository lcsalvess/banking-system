-- Upper bound for account balances (keeps values inside NUMERIC(19,2) and
-- exactly representable by JSON clients). Mirrors Account.MAX_BALANCE.
ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_balance_max
        CHECK (balance <= 9999999999999.99);
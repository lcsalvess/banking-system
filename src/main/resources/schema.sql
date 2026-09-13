CREATE SEQUENCE IF NOT EXISTS account_number_seq START WITH 1 INCREMENT BY 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_transaction_daily_yield
    ON transaction (account_id, (CAST(created_at AS date)))
    WHERE transaction_type = 'YIELD';
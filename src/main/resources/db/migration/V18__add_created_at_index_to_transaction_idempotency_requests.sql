CREATE INDEX idx_transaction_idempotency_created_at
    ON transaction_idempotency_requests (created_at);

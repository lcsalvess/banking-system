package com.lcsalvess.bankingsystem.repository.idempotency;

import java.util.UUID;

public record TransactionIdempotencyRecord(
        UUID idempotencyKey,
        String operationType,
        String requestHash,
        UUID transactionCode
) {
}
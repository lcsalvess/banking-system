package com.lcsalvess.bankingsystem.repository.idempotency;

import java.util.UUID;

/**
 * Represents a persisted transaction idempotency record.
 *
 * @param idempotencyKey the unique key identifying the original request
 * @param operationType the type of financial operation
 * @param requestHash the fingerprint of the original request payload
 * @param transactionCode the associated transaction identifier, or {@code null}
 *     if the operation has not been completed
 */
public record TransactionIdempotencyRecord(
    UUID idempotencyKey,
    String operationType,
    String requestHash,
    UUID transactionCode
) {
}

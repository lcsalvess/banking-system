package com.lcsalvess.bankingsystem.service.transaction.idempotency;

public enum TransactionIdempotencyOperation {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER
}
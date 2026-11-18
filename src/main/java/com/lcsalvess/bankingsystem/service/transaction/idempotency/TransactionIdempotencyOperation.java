package com.lcsalvess.bankingsystem.service.transaction.idempotency;

/**
 * Defines the financial operation types supported by transaction idempotency.
 *
 * <p>Distinguishes deposits, withdrawals, and transfers when identifying
 * and validating repeated transaction requests.
 */
public enum TransactionIdempotencyOperation {
  DEPOSIT,
  WITHDRAWAL,
  TRANSFER
}

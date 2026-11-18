package com.lcsalvess.bankingsystem.entity.transaction;

/**
 * Defines the types of financial transactions supported by the banking system.
 *
 * <p>Includes deposits, withdrawals, outgoing and incoming transfers,
 * and system-generated yield transactions.
 */
public enum TransactionType {
  DEPOSIT,
  WITHDRAWAL,
  TRANSFER_SENT,
  TRANSFER_RECEIVED,
  YIELD
}

package com.lcsalvess.bankingsystem.repository.projection;

import java.math.BigDecimal;

/**
 * Represents an account whose balance does not match the {@code balance_after}
 * value of its most recent transaction.
 */
public interface BalanceMismatch {

  /**
   * Returns the identifier of the account with the balance mismatch.
   *
   * @return the account identifier
   */
  Long getAccountId();

  /**
   * Returns the account number.
   *
   * @return the account number
   */
  String getAccountNumber();

  /**
   * Returns the current balance stored for the account.
   *
   * @return the account balance
   */
  BigDecimal getAccountBalance();

  /**
   * Returns the balance recorded by the most recent transaction.
   *
   * @return the ledger balance
   */
  BigDecimal getLedgerBalance();
}

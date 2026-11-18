package com.lcsalvess.bankingsystem.repository.projection;

/**
 * Projection containing the account and transaction identifiers
 * associated with a balance chain inconsistency.
 */
public interface BalanceChainBreak {

  /**
   * Returns the identifier of the account with the balance chain inconsistency.
   *
   * @return the account identifier
   */
  Long getAccountId();

  /**
   * Returns the identifier of the transaction associated with the inconsistency.
   *
   * @return the transaction identifier
   */
  Long getTransactionId();
}

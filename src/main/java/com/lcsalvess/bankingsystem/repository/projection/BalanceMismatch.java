package com.lcsalvess.bankingsystem.repository.projection;

import java.math.BigDecimal;

/**
 * Represents an account whose balance does not match the {@code balance_after}
 * value of its most recent transaction.
 */
public interface BalanceMismatch {

    Long getAccountId();

    String getAccountNumber();

    BigDecimal getAccountBalance();

    BigDecimal getLedgerBalance();
}

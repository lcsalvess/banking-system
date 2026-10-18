package com.lcsalvess.bankingsystem.repository.projection;

import java.math.BigDecimal;

/**
 * Conta cujo saldo não bate com o balance_after da sua última transação.
 */
public interface BalanceMismatch {

    Long getAccountId();

    String getAccountNumber();

    BigDecimal getAccountBalance();

    BigDecimal getLedgerBalance();
}
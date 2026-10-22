package com.lcsalvess.bankingsystem.event.transaction;

import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionOperationEvent(
        TransactionType type,
        String accountNumber,
        BigDecimal amount,
        String username,
        UUID transactionCode
) {

    /**
     * Para eventos sem transação auditável, como o rendimento do sistema, que
     * não carregam código.
     */
    public TransactionOperationEvent(
            TransactionType type,
            String accountNumber,
            BigDecimal amount,
            String username
    ) {
        this(type, accountNumber, amount, username, null);
    }
}

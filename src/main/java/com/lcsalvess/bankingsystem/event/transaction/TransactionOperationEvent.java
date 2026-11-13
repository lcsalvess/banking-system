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
}

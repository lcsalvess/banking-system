package com.lucas.bankingsystem.event.transaction;

import com.lucas.bankingsystem.entity.enums.TransactionType;

import java.math.BigDecimal;

public record TransactionOperationEvent(
        TransactionType type,
        String accountNumber,
        BigDecimal amount,
        String username
) {
}

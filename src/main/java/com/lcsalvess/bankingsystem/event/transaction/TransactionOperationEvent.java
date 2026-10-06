package com.lcsalvess.bankingsystem.event.transaction;

import com.lcsalvess.bankingsystem.entity.enums.TransactionType;

import java.math.BigDecimal;

public record TransactionOperationEvent(
        TransactionType type,
        String accountNumber,
        BigDecimal amount,
        String username
) {
}

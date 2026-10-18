package com.lcsalvess.bankingsystem.event.transaction;

import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;

import java.math.BigDecimal;

public record TransactionTransferEvent(
        TransactionType type,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal amount,
        String username
) {
}

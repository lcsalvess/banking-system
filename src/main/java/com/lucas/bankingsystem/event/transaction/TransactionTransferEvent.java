package com.lucas.bankingsystem.event.transaction;

import com.lucas.bankingsystem.entity.enums.TransactionType;

import java.math.BigDecimal;

public record TransactionTransferEvent(
        TransactionType type,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal amount,
        String username
) {
}

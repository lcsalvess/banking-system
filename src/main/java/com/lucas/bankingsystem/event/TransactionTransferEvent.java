package com.lucas.bankingsystem.event;

import com.lucas.bankingsystem.entity.enums.TransactionType;

import java.math.BigDecimal;

public record TransactionTransferEvent(
        TransactionType type,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal amount
) {
}

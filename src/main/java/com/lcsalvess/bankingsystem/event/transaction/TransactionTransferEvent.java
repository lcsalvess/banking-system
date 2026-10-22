package com.lcsalvess.bankingsystem.event.transaction;

import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionTransferEvent(
        TransactionType type,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal amount,
        String username,
        UUID transferCode
) {
}

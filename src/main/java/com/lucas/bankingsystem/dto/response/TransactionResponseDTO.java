package com.lucas.bankingsystem.dto.response;

import com.lucas.bankingsystem.config.MoneySerializer;
import com.lucas.bankingsystem.entity.Transaction;
import com.lucas.bankingsystem.entity.enums.TransactionType;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponseDTO(
        UUID transactionCode,
        TransactionType type,
        @JsonSerialize(using = MoneySerializer.class)
        BigDecimal amount,
        LocalDateTime createdAt
) {
    public static TransactionResponseDTO fromEntity(Transaction transaction) {
        return new TransactionResponseDTO(
                transaction.getTransactionCode(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCreatedAt()
        );
    }
}

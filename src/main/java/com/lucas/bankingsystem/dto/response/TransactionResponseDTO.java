package com.lucas.bankingsystem.dto.response;

import com.lucas.bankingsystem.entity.Transaction;
import com.lucas.bankingsystem.entity.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponseDTO(
        Long id,
        TransactionType transactionType,
        BigDecimal amount,
        LocalDateTime createdAt
) {
    public static TransactionResponseDTO fromEntity(Transaction transaction) {
        return new TransactionResponseDTO(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCreatedAt()
        );
    }
}

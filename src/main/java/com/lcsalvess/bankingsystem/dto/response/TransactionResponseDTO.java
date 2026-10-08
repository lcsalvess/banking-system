package com.lcsalvess.bankingsystem.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lcsalvess.bankingsystem.serialization.MoneySerializer;
import com.lcsalvess.bankingsystem.entity.Transaction;
import com.lcsalvess.bankingsystem.entity.enums.TransactionType;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponseDTO(
        UUID transactionCode,
        UUID transferCode,
        TransactionType type,
        @JsonSerialize(using = MoneySerializer.class)
        BigDecimal amount,
        LocalDateTime createdAt
) {
    public static TransactionResponseDTO fromEntity(Transaction transaction) {
        return new TransactionResponseDTO(
                transaction.getTransactionCode(),
                transaction.getTransferCode(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCreatedAt()
        );
    }
}
